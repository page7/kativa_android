package com.kativa.reader.ui.navigation

import com.kativa.reader.R
import com.kativa.reader.core.designsystem.KativaIcons

/**
 * Top-level destinations. Four tabs only — the download tab was removed by product decision
 * (AGENTS.md §1/§10), so "Me" carries server switching and settings.
 *
 * Icons come from the generated asset table, never `Icons.*` (AGENTS.md §6).
 */
enum class TopDestination(
    val route: String,
    val labelRes: Int,
    val iconRes: Int,
) {
    Home("home", R.string.nav_home, KativaIcons.Home),
    Library("library", R.string.nav_library, KativaIcons.Library),
    Discover("discover", R.string.nav_discover, KativaIcons.Discover),
    Me("me", R.string.nav_me, KativaIcons.Me),
    ;

    companion object {
        val routes = entries.map { it.route }.toSet()
    }
}

/** Non-tab routes. */
object Routes {
    const val SPLASH = "splash"
    const val WELCOME = "welcome"
    const val SERVER_PICKER = "servers"
    const val SERVER_SETUP = "servers/setup"
    const val LOGIN = "login"
    const val SETTINGS = "settings"
    const val SERVER_MANAGE = "settings/servers"
    const val LIBRARY = "library"
    const val SERIES = "series"
    const val READER = "reader"
    const val SEARCH = "search"
    const val SHELF = "shelf"
    const val STREAM = "stream"

    const val ARG_SERVER_ID = "serverId"
    const val ARG_SHOW_BACK = "showBack"

    fun serverSetup(serverId: String? = null): String =
        if (serverId == null) SERVER_SETUP else "$SERVER_SETUP?$ARG_SERVER_ID=$serverId"

    /**
     * @param showBack true when the picker is pushed on top of another screen (Me → switch
     *   server). The startup destination must NOT show a back affordance.
     */
    fun serverPicker(showBack: Boolean): String =
        if (showBack) "$SERVER_PICKER?$ARG_SHOW_BACK=true" else SERVER_PICKER

    fun login(serverId: String): String = "$LOGIN?$ARG_SERVER_ID=$serverId"

    fun library(libraryId: Int): String = "$LIBRARY/$libraryId"

    fun series(seriesId: Int): String = "$SERIES/$seriesId"

    fun reader(chapterId: Int): String = "$READER/$chapterId"

    fun shelf(kind: String): String = "$SHELF/$kind"

    fun shelfDetail(kind: String, shelfId: Int): String = "$SHELF/$kind/$shelfId"

    fun stream(streamKey: String): String = "$STREAM/$streamKey"
}
