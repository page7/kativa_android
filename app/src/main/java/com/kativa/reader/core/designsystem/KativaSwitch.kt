package com.kativa.reader.core.designsystem

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Brand switch (AGENTS.md §6: screens must not style Material components themselves).
 *
 * The default checked track is `colorScheme.primary`, which the light theme maps to the *deep*
 * brand green so that text on white stays readable. A switch track is a large filled area, though,
 * and the deep tone there reads as "darkened green" next to the mint top bar and mint buttons. The
 * track therefore uses the brand mint with the dark `OnBrand` ink as the thumb, exactly like
 * [KativaPrimaryButton].
 *
 * Small indicators (radio button dot, checkbox tick) deliberately keep `primary`: a mint ring on
 * white is only ~2.2:1 and would be hard to see.
 */
@Composable
fun KativaSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        enabled = enabled,
        colors = SwitchDefaults.colors(
            checkedThumbColor = KativaColors.OnBrand,
            checkedTrackColor = KativaColors.BrandPrimary,
            checkedBorderColor = KativaColors.BrandPrimary,
            checkedIconColor = KativaColors.BrandPrimary,
            uncheckedThumbColor = MaterialTheme.colorScheme.outline,
            uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
            uncheckedBorderColor = MaterialTheme.colorScheme.outline,
        ),
    )
}
