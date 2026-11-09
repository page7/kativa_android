package com.kativa.reader.core.designsystem

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kativa.reader.R

/**
 * Every icon in the app comes from the generated asset pipeline (`docs/ASSETS.md`), never from
 * `material-icons-extended`. This wrapper exists so screens never touch `Icons.*` directly
 * (AGENTS.md §6 / §10).
 */
@Composable
fun KativaIcon(
    @DrawableRes icon: Int,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
    size: Dp = KativaDimens.IconSize.dp,
) {
    Icon(
        painter = painterResource(icon),
        contentDescription = contentDescription,
        tint = tint,
        modifier = modifier.size(size),
    )
}

/** Named icon constants — a single place to audit asset usage. */
object KativaIcons {
    val Back = R.drawable.ic_back
    val Close = R.drawable.ic_close
    val More = R.drawable.ic_more
    val Search = R.drawable.ic_search
    val Settings = R.drawable.ic_settings
    val SettingsReader = R.drawable.ic_settings_reader
    val Edit = R.drawable.ic_edit
    val Delete = R.drawable.ic_delete
    val Sort = R.drawable.ic_sort
    val PrivacyOff = R.drawable.ic_privacy_off
    val PrivacyOn = R.drawable.ic_privacy_on
    val Visibility = R.drawable.ic_visibility
    val VisibilityOff = R.drawable.ic_visibility_off
    val ExpandLess = R.drawable.ic_expand_less
    val ExpandMore = R.drawable.ic_expand_more
    val Bookmark = R.drawable.ic_bookmark
    val BookmarkFilled = R.drawable.ic_bookmark_filled
    val Read = R.drawable.ic_read
    val Unread = R.drawable.ic_unread
    val Favorite = R.drawable.ic_favorite
    val FavoriteFilled = R.drawable.ic_favorite_filled
    val CoverFallback = R.drawable.ic_cover_fallback_mark

    val PrevChapter = R.drawable.ic_prev_chapter
    val NextChapter = R.drawable.ic_next_chapter
    val Toc = R.drawable.ic_toc
    val DoublePage = R.drawable.ic_double_page
    val Webtoon = R.drawable.ic_webtoon
    val Brightness = R.drawable.ic_brightness
    val Volume = R.drawable.ic_volume

    val Home = R.drawable.ic_home
    val Library = R.drawable.ic_library
    val Discover = R.drawable.ic_compass
    val Me = R.drawable.ic_person
}
