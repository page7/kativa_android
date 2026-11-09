package com.kativa.reader.core.designsystem

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Single-line text field that draws exactly one horizontal rule underneath the text.
 *
 * Material's `OutlinedTextField` puts a rounded box around the text, which read as a heavy
 * "form control" in the search top bar. This component keeps only the baseline rule: 1dp while
 * idle, 2dp in the brand colour while focused.
 *
 * The height is **content-independent**. The previous version sized itself from its contents,
 * and the two states differed: an empty query has no trailing clear button, so the row collapsed
 * to one text line, while a typed query pulled the row up to the button's 48dp touch target.
 * The whole field therefore grew by ~6dp and the rule visibly jumped as soon as the first
 * character was typed. Everything below is measured from [textStyle] instead:
 *
 *  - the field is [minFieldHeight] tall at least, growing only with the system font scale;
 *  - the rule occupies its own fixed 2dp slot, so the 1dp↔2dp focus animation cannot resize it
 *    either (a height animation on the rule was enough to shift the layout by one pixel).
 */
@Composable
fun KativaUnderlineField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val onBrand = LocalOnBrandSurface.current
    val lineThickness by animateDpAsState(
        targetValue = if (focused) RuleFocusedThickness else RuleIdleThickness,
        label = "underlineThickness",
    )
    // On the brand-coloured top bar the theme's `outline` is a near-white grey that vanishes
    // against mint, and `primary` is mint-adjacent. Both states fall back to the same ink the bar
    // draws its content with, dimmed while idle.
    val lineColor = when {
        onBrand -> KativaColors.OnBrand.copy(alpha = if (focused) 1f else 0.45f)
        focused -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.outline
    }
    val textStyle = MaterialTheme.typography.bodyLarge.copy(
        color = if (onBrand) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurface
        },
    )

    val density = LocalDensity.current
    // Measured from the typography rather than hard-coded, so large system font sizes are not
    // clipped. `fontScale` is applied explicitly: the composed height must depend on the text
    // metrics only, never on which children happen to be present.
    val lineHeightSp = if (textStyle.lineHeight.isSp) textStyle.lineHeight.value else 24f
    val textLineHeight = (lineHeightSp * density.fontScale).dp
    val fieldHeight = maxOf(
        MinFieldHeight,
        textLineHeight + RuleGap + RuleSlotThickness,
    )

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = textStyle,
        cursorBrush = SolidColor(
            if (onBrand) KativaColors.OnBrand else MaterialTheme.colorScheme.primary,
        ),
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        interactionSource = interactionSource,
        modifier = modifier,
        decorationBox = { innerTextField ->
            Box(Modifier.fillMaxWidth().height(fieldHeight)) {
                Row(
                    // Reserve the rule's strip so the text is never drawn over it, then centre
                    // whatever is left: placeholder, typed text and the clear button all share
                    // this one fixed box, which is what keeps the two states pixel-identical.
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = RuleGap + RuleSlotThickness),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.weight(1f)) {
                        if (value.isEmpty() && placeholder != null) {
                            Text(
                                text = placeholder,
                                style = textStyle,
                                color = if (onBrand) {
                                    // Dimmed ink, not `onSurfaceVariant`: the grey placeholder tone
                                    // only reaches ~3.2:1 on the mint band.
                                    KativaColors.OnBrand.copy(alpha = 0.7f)
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        innerTextField()
                    }
                    trailingIcon?.invoke()
                }

                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(RuleSlotThickness)
                        .align(Alignment.BottomStart),
                    contentAlignment = Alignment.BottomStart,
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(lineThickness)
                            .background(lineColor),
                    )
                }
            }
        },
    )
}

/** Minimum tap target (AGENTS.md §6). */
private val MinFieldHeight = 48.dp

/** Breathing room between the last text line and the rule. */
private val RuleGap = 4.dp

/** Space always reserved for the rule, whatever its current thickness. */
private val RuleSlotThickness = 2.dp

private val RuleIdleThickness = 1.dp
private val RuleFocusedThickness = 2.dp
