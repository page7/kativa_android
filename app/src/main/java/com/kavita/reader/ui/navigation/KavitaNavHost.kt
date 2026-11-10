package com.kavita.reader.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.kavita.reader.core.designsystem.ThemeMode
import com.kavita.reader.ui.auth.LoginScreen
import com.kavita.reader.ui.home.HomeScreen
import com.kavita.reader.ui.library.LibraryGridScreen
import com.kavita.reader.ui.library.LibraryScreen
import com.kavita.reader.ui.me.MeScreen
import com.kavita.reader.ui.onboarding.WelcomeScreen
import com.kavita.reader.ui.reader.MangaReaderScreen
import com.kavita.reader.ui.series.SeriesDetailScreen
import com.kavita.reader.ui.servers.ServerPickerScreen
import com.kavita.reader.ui.servers.ServerSetupScreen
import com.kavita.reader.ui.settings.SettingsScreen
import com.kavita.reader.ui.splash.SplashScreen

/**
 * Single-activity navigation graph.
 *
 * Splash → welcome / server picker / home, then the four-tab shell. Login, server setup,
 * library grid, series detail and the reader are full-screen destinations outside the tabs
 * (docs/PLAN.md §2).
 */
@Composable
fun KavitaNavHost(
    themeMode: ThemeMode,
    privacyMode: Boolean,
    navController: NavHostController = rememberNavController(),
) {
    val sessionExpiry: com.kavita.reader.ui.navigation.SessionExpiryHandler =
        hiltViewModel()
    val currentNav = rememberUpdatedState(navController)

    // A dead token on any screen must lead somewhere the user can actually fix it: the server
    // picker (marked expired) and then the login form. Without this, 401s were shown as error
    // screens whose "retry" could only fail again.
    LaunchedEffect(sessionExpiry) {
        sessionExpiry.expired.collect { serverId ->
            val nav = currentNav.value
            if (nav.currentDestination?.route?.startsWith(Routes.SERVER_PICKER) == true) return@collect
            nav.navigate(Routes.serverPicker(showBack = false)) {
                // Clear the stack: the screen the user was on is unusable until they sign in, and
                // back from the picker must not return to it.
                popUpTo(nav.graph.findStartDestination().id) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    // Lets any screen's error state offer "sign in again" without hard-coding navigation.
    androidx.compose.runtime.CompositionLocalProvider(
        com.kavita.reader.ui.common.LocalSignInAgain provides {
            val nav = currentNav.value
            val serverId = sessionExpiry.serverToSignIn()
            if (serverId != null) {
                nav.navigate(Routes.login(serverId)) { launchSingleTop = true }
            } else {
                nav.navigate(Routes.serverPicker(showBack = true)) { launchSingleTop = true }
            }
        },
    ) {
        NavHost(
            navController = navController,
            startDestination = Routes.SPLASH,
        ) {
        composable(Routes.SPLASH) {
            SplashScreen(
                onWelcome = {
                    navController.navigate(Routes.WELCOME) {
                        popUpTo(Routes.SPLASH) { inclusive = true }
                    }
                },
                onServerPicker = {
                    navController.navigate(Routes.SERVER_PICKER) {
                        popUpTo(Routes.SPLASH) { inclusive = true }
                    }
                },
                onHome = {
                    navController.navigate(TopDestination.Home.route) {
                        popUpTo(Routes.SPLASH) { inclusive = true }
                    }
                },
            )
        }

        composable(Routes.WELCOME) {
            WelcomeScreen(
                onContinue = {
                    navController.navigate(Routes.serverSetup(showBack = false)) {
                        popUpTo(Routes.WELCOME) { inclusive = true }
                    }
                },
            )
        }

        composable(
            route = "${Routes.SERVER_PICKER}?${Routes.ARG_SHOW_BACK}={${Routes.ARG_SHOW_BACK}}",
            arguments = listOf(
                navArgument(Routes.ARG_SHOW_BACK) {
                    type = NavType.BoolType
                    defaultValue = false
                },
            ),
        ) { entry ->
            val showBack = entry.arguments?.getBoolean(Routes.ARG_SHOW_BACK) == true
            ServerPickerScreen(
                onAddServer = { navController.navigate(Routes.serverSetup(showBack = true)) },
                onEditServer = { id -> navController.navigate(Routes.serverSetup(id)) },
                // Only offer "back" when this screen was explicitly pushed on top of another.
                // As the app's startup destination there is no previous entry, and a back button
                // (or a pop) would just exit the app.
                onBack = if (!showBack) {
                    null
                } else {
                    {
                        if (!navController.popBackStack()) {
                            navController.navigate(TopDestination.Home.route) { launchSingleTop = true }
                        }
                    }
                },
                onOpenServer = { serverId, signedIn ->
                    if (signedIn) {
                        navController.navigate(TopDestination.Home.route) {
                            popUpTo(Routes.SERVER_PICKER) { inclusive = true }
                        }
                    } else {
                        navController.navigate(Routes.login(serverId))
                    }
                },
            )
        }

        composable(
            route = "${Routes.SERVER_SETUP}?" +
                "${Routes.ARG_SERVER_ID}={${Routes.ARG_SERVER_ID}}&" +
                "${Routes.ARG_SHOW_BACK}={${Routes.ARG_SHOW_BACK}}",
            arguments = listOf(
                navArgument(Routes.ARG_SERVER_ID) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument(Routes.ARG_SHOW_BACK) {
                    type = NavType.BoolType
                    defaultValue = false
                },
            ),
        ) { entry ->
            val showBack = entry.arguments?.getBoolean(Routes.ARG_SHOW_BACK) == true
            ServerSetupScreen(
                // First run arrives here straight from Welcome: there is no previous entry, so a
                // back arrow would only exit the app. Show it when setup was pushed on top of the
                // picker (add another server) or when editing an existing one.
                onBack = if (showBack) ({ navController.popBackStack() }) else null,
                onSaved = { serverId, _, _ ->
                    // Always continue to sign-in: the credentials step is what actually authorises
                    // the app, and it is reachable even when the health probe was skipped.
                    if (!showBack) {
                        // First run: setup is the root, so replace it with the picker before
                        // pushing sign-in. Backing out of sign-in then lands on the server list
                        // (where the saved entry can be edited) instead of a stale "add server"
                        // form or a bare exit.
                        navController.navigate(Routes.SERVER_PICKER) {
                            popUpTo(entry.destination.id) { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                    navController.navigate(Routes.login(serverId)) {
                        if (showBack) {
                            // Pushed from the picker: keep it underneath so back from sign-in
                            // returns there instead of to a form that was just saved.
                            popUpTo(Routes.SERVER_PICKER)
                        }
                    }
                },
            )
        }

        composable(
            route = "${Routes.LOGIN}?${Routes.ARG_SERVER_ID}={${Routes.ARG_SERVER_ID}}",
            arguments = listOf(
                navArgument(Routes.ARG_SERVER_ID) { type = NavType.StringType },
            ),
        ) {
            LoginScreen(
                onBack = { navController.popBackStack() },
                onSignedIn = {
                    navController.navigate(TopDestination.Home.route) {
                        popUpTo(Routes.SERVER_PICKER) { inclusive = true }
                    }
                },
            )
        }

        composable(TopDestination.Home.route) {
            HomeScreen(
                onOpenSeries = { id -> navController.navigate(Routes.series(id)) },
                onOpenSearch = { navController.navigate(Routes.SEARCH) },
                // The user-centre icon pushes Me on top of Home, so both the toolbar back button
                // and the system back gesture return here (docs/PAGES.md P03/P09).
                onOpenMe = {
                    navController.navigate(TopDestination.Me.route) {
                        launchSingleTop = true
                    }
                },
                onOpenLibrary = { id -> navController.navigate(Routes.library(id)) },
                onOpenStream = { key -> navController.navigate(Routes.stream(key)) },
            )
        }

        composable(TopDestination.Library.route) {
            LibraryScreen(
                onOpenLibrary = { id -> navController.navigate(Routes.library(id)) },
            )
        }

        composable(
            route = "${Routes.LIBRARY}/{libraryId}",
            arguments = listOf(navArgument("libraryId") { type = NavType.StringType }),
        ) {
            LibraryGridScreen(
                onBack = { navController.popBackStack() },
                onOpenSeries = { id -> navController.navigate(Routes.series(id)) },
            )
        }

        composable(
            route = "${Routes.SERIES}/{seriesId}",
            arguments = listOf(navArgument("seriesId") { type = NavType.StringType }),
        ) {
            SeriesDetailScreen(
                onBack = { navController.popBackStack() },
                onOpenChapter = { chapterId -> navController.navigate(Routes.reader(chapterId)) },
            )
        }

        composable(
            route = "${Routes.READER}/{chapterId}?${Routes.ARG_PAGE}={${Routes.ARG_PAGE}}",
            arguments = listOf(
                navArgument("chapterId") { type = NavType.StringType },
                // 0-based page index; -1 = no explicit target, resume from server progress.
                navArgument(Routes.ARG_PAGE) {
                    type = NavType.IntType
                    defaultValue = -1
                },
            ),
        ) { entry ->
            MangaReaderScreen(
                onBack = { navController.popBackStack() },
                onOpenChapter = { chapterId ->
                    // Replace the reader entry: reading chapter after chapter must not grow the stack.
                    navController.navigate(Routes.reader(chapterId)) {
                        popUpTo(entry.destination.id) { inclusive = true }
                    }
                },
            )
        }

        composable(TopDestination.Discover.route) {
            com.kavita.reader.ui.discover.DiscoverPlaceholder(
                onOpenSearch = { navController.navigate(Routes.SEARCH) },
                onOpenCollections = { navController.navigate(Routes.shelf("collections")) },
                onOpenReadingLists = { navController.navigate(Routes.shelf("lists")) },
                onOpenWantToRead = { navController.navigate(Routes.shelf("want-to-read")) },
            )
        }

        composable(TopDestination.Me.route) {
            MeScreen(
                // Return to Home rather than popBackStack(): Me is also a top-level tab route, so
                // it may be the graph's start destination on some paths and popping would exit
                // the app. Navigating Home is deterministic.
                onBack = {
                    // Prefer the real back stack: this preserves whatever the user came from.
                    // Fall back to Home only when Me is the first entry (deep link / process
                    // restore), where popping would exit the app instead of returning anywhere.
                    if (!navController.popBackStack()) {
                        navController.navigate(TopDestination.Home.route) {
                            popUpTo(TopDestination.Me.route) { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                onManageServers = { navController.navigate(Routes.serverPicker(showBack = true)) },
                onOpenBookmarks = { navController.navigate(Routes.BOOKMARKS) },
                onOpenWantToRead = { navController.navigate(Routes.shelf("want-to-read")) },
            )
        }

        composable(Routes.BOOKMARKS) {
            com.kavita.reader.ui.bookmarks.BookmarksScreen(
                onBack = { navController.popBackStack() },
                onOpenBookmark = { chapterId, pageIndex ->
                    // Carry the 0-based bookmark page index: resuming at saved progress would open
                    // a different page than the one the user tapped (docs/FLOWS.md F11).
                    navController.navigate(Routes.reader(chapterId, pageIndex))
                },
            )
        }

        composable(Routes.SEARCH) {
            com.kavita.reader.ui.search.SearchScreen(
                onBack = { navController.popBackStack() },
                onOpenSeries = { id -> navController.navigate(Routes.series(id)) },
            )
        }

        composable(
            route = "${Routes.SHELF}/{kind}",
            arguments = listOf(navArgument("kind") { type = NavType.StringType }),
        ) { entry ->
            val kind = entry.arguments?.getString("kind").orEmpty()
            com.kavita.reader.ui.shelf.ShelfScreen(
                onBack = { navController.popBackStack() },
                onOpenSeries = { id -> navController.navigate(Routes.series(id)) },
                onOpenShelf = { shelfId -> navController.navigate(Routes.shelfDetail(kind, shelfId)) },
            )
        }

        composable(
            route = "${Routes.SHELF}/{kind}/{shelfId}",
            arguments = listOf(
                navArgument("kind") { type = NavType.StringType },
                navArgument("shelfId") { type = NavType.StringType },
            ),
        ) {
            com.kavita.reader.ui.shelf.ShelfScreen(
                onBack = { navController.popBackStack() },
                onOpenSeries = { id -> navController.navigate(Routes.series(id)) },
                onOpenShelf = { /* already inside a shelf */ },
            )
        }

        composable(
            route = "${Routes.STREAM}/{streamKey}",
            arguments = listOf(navArgument("streamKey") { type = NavType.StringType }),
        ) {
            com.kavita.reader.ui.stream.StreamScreen(
                onBack = { navController.popBackStack() },
                onOpenSeries = { id -> navController.navigate(Routes.series(id)) },
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onSignedOut = {
                    navController.navigate(Routes.SERVER_PICKER) {
                        popUpTo(navController.graph.findStartDestination().id) { inclusive = true }
                    }
                },
            )
        }
        }
    }
}
