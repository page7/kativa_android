package com.kavita.reader.ui.navigation

import com.kavita.reader.R
import com.kavita.reader.core.designsystem.KavitaIcons

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
    Home("home", R.string.nav_home, KavitaIcons.Home),
    Library("library", R.string.nav_library, KavitaIcons.Library),
    Discover("discover", R.string.nav_discover, KavitaIcons.Discover),
    Me("me", R.string.nav_me, KavitaIcons.Me),
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
    const val BOOKMARKS = "bookmarks"

    const val ARG_SERVER_ID = "serverId"
    const val ARG_SHOW_BACK = "showBack"
    const val ARG_PAGE = "page"

    /**
     * @param showBack true when setup was pushed on top of another screen (server picker → add,
     *   or edit an existing server). First-run setup (welcome → setup) is a graph root with
     *   nothing to return to, so it must NOT offer a back affordance.
     */
    fun serverSetup(serverId: String? = null, showBack: Boolean = serverId != null): String {
        val query = buildList {
            serverId?.let { add("$ARG_SERVER_ID=$it") }
            if (showBack) add("$ARG_SHOW_BACK=true")
        }
        return if (query.isEmpty()) SERVER_SETUP else "$SERVER_SETUP?${query.joinToString("&")}"
    }

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

    /**
     * Opens a chapter, optionally at an explicit page.
     *
     * `pageIndex` is the **0-based** page index, the same space as `Reader/image?page=` and
     * `BookmarkDto.page`. It is used when the user taps a bookmark: resuming at saved progress
     * would open a different page than the one they chose. Omitted means "resume".
     */
    fun reader(chapterId: Int, pageIndex: Int? = null): String =
        if (pageIndex == null || pageIndex < 0) {
            reader(chapterId)
        } else {
            "$READER/$chapterId?$ARG_PAGE=$pageIndex"
        }

    fun shelf(kind: String): String = "$SHELF/$kind"

    fun shelfDetail(kind: String, shelfId: Int): String = "$SHELF/$kind/$shelfId"

    fun stream(streamKey: String): String = "$STREAM/$streamKey"
}
