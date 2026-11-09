package com.vancedclean.app.navigation

import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.compose.runtime.Composable
import com.vancedclean.app.ui.ChannelScreen
import com.vancedclean.app.ui.DownloadScreen
import com.vancedclean.app.ui.InstagramLoginScreen
import com.vancedclean.app.ui.LocalVideoScreen
import com.vancedclean.app.ui.MusicScreen
import com.vancedclean.app.ui.NowPlayingScreen
import com.vancedclean.app.ui.MyDownloadsScreen
import com.vancedclean.app.ui.LibraryScreen
import com.vancedclean.app.ui.LoginScreen
import com.vancedclean.app.ui.MainScreen
import com.vancedclean.app.ui.PlaylistScreen
import com.vancedclean.app.ui.SearchScreen
import com.vancedclean.app.ui.SettingsScreen
import com.vancedclean.app.ui.SubscriptionsScreen

@Composable
fun NavGraph(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Home.route,
        modifier = modifier
    ) {
        composable(Screen.Home.route) {
            MainScreen(navController = navController)
        }

        composable(Screen.Search.route) {
            SearchScreen(navController = navController)
        }

        composable(Screen.Login.route) {
            LoginScreen(navController = navController)
        }

        composable(
            route = Screen.Channel.route,
            arguments = listOf(
                navArgument(Screen.Channel.ARG_CHANNEL_URL) { type = NavType.StringType }
            )
        ) {
            ChannelScreen(navController = navController)
        }

        composable(
            route = Screen.Playlist.route,
            arguments = listOf(
                navArgument(Screen.Playlist.ARG_ID) { type = NavType.StringType },
                navArgument(Screen.Playlist.ARG_TITLE) { type = NavType.StringType; defaultValue = "" }
            )
        ) {
            PlaylistScreen(navController = navController)
        }

        composable(
            route = Screen.Download.route,
            arguments = listOf(navArgument(Screen.Download.ARG_URL) { type = NavType.StringType; defaultValue = "" })
        ) { entry ->
            DownloadScreen(navController, entry.arguments?.getString(Screen.Download.ARG_URL).orEmpty())
        }

        composable(Screen.MyDownloads.route) { MyDownloadsScreen(navController) }

        composable(Screen.Music.route) { MusicScreen(navController) }

        composable(Screen.NowPlaying.route) { NowPlayingScreen(navController) }

        composable(Screen.InstagramLogin.route) { InstagramLoginScreen(navController) }

        composable(
            route = Screen.LocalVideo.route,
            arguments = listOf(navArgument(Screen.LocalVideo.ARG_ID) { type = NavType.StringType })
        ) { entry ->
            LocalVideoScreen(navController, entry.arguments?.getString(Screen.LocalVideo.ARG_ID).orEmpty())
        }

        composable(Screen.Subscriptions.route) {
            SubscriptionsScreen(navController = navController)
        }

        composable(Screen.Library.route) {
            LibraryScreen(navController = navController)
        }

        composable(Screen.Settings.route) {
            SettingsScreen(navController = navController)
        }
    }
}
