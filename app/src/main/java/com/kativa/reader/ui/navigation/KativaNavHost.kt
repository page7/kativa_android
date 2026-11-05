package com.kativa.reader.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.kativa.reader.core.designsystem.ThemeMode
import com.kativa.reader.ui.auth.LoginScreen
import com.kativa.reader.ui.home.HomeScreen
import com.kativa.reader.ui.library.LibraryGridScreen
import com.kativa.reader.ui.library.LibraryScreen
import com.kativa.reader.ui.me.MeScreen
import com.kativa.reader.ui.onboarding.WelcomeScreen
import com.kativa.reader.ui.reader.MangaReaderScreen
import com.kativa.reader.ui.series.SeriesDetailScreen
import com.kativa.reader.ui.servers.ServerPickerScreen
import com.kativa.reader.ui.servers.ServerSetupScreen
import com.kativa.reader.ui.settings.SettingsScreen
import com.kativa.reader.ui.splash.SplashScreen

/**
 * Single-activity navigation graph.
 *
 * Splash → welcome / server picker / home, then the four-tab shell. Login, server setup,
 * library grid, series detail and the reader are full-screen destinations outside the tabs
 * (docs/PLAN.md §2).
 */
@Composable
fun KativaNavHost(
    themeMode: ThemeMode,
    privacyMode: Boolean,
    navController: NavHostController = rememberNavController(),
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
                    navController.navigate(Routes.SERVER_SETUP) {
                        popUpTo(Routes.WELCOME) { inclusive = true }
                    }
                },
            )
        }

        composable(Routes.SERVER_PICKER) {
            ServerPickerScreen(
                onAddServer = { navController.navigate(Routes.SERVER_SETUP) },
                onEditServer = { id -> navController.navigate(Routes.serverSetup(id)) },
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
            route = "${Routes.SERVER_SETUP}?${Routes.ARG_SERVER_ID}={${Routes.ARG_SERVER_ID}}",
            arguments = listOf(
                navArgument(Routes.ARG_SERVER_ID) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        ) {
            ServerSetupScreen(
                onBack = { navController.popBackStack() },
                onSaved = { serverId, _, _ ->
                    // Always continue to sign-in: the credentials step is what actually authorises
                    // the app, and it is reachable even when the health probe was skipped.
                    navController.navigate(Routes.login(serverId)) {
                        popUpTo(Routes.SERVER_PICKER)
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
                onOpenSearch = { /* search lands in M2 */ },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
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
            route = "${Routes.READER}/{chapterId}",
            arguments = listOf(navArgument("chapterId") { type = NavType.StringType }),
        ) {
            MangaReaderScreen(
                onBack = { navController.popBackStack() },
                onOpenChapter = { chapterId ->
                    // Replace the reader entry: reading chapter after chapter must not grow the stack.
                    navController.navigate(Routes.reader(chapterId)) {
                        popUpTo("${Routes.READER}/{chapterId}") { inclusive = true }
                    }
                },
            )
        }

        composable(TopDestination.Discover.route) {
            com.kativa.reader.ui.discover.DiscoverPlaceholder()
        }

        composable(TopDestination.Me.route) {
            MeScreen(
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                onManageServers = { navController.navigate(Routes.SERVER_PICKER) },
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onManageServers = { navController.navigate(Routes.SERVER_PICKER) },
                onSignedOut = {
                    navController.navigate(Routes.SERVER_PICKER) {
                        popUpTo(navController.graph.findStartDestination().id) { inclusive = true }
                    }
                },
            )
        }
    }
}
