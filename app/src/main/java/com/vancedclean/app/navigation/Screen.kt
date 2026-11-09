package com.vancedclean.app.navigation

sealed class Screen(val route: String) {

    data object Home : Screen("home")

    data object Search : Screen("search")

    data object Login : Screen("login")

    data object Channel : Screen("channel/{channelUrl}") {
        const val ARG_CHANNEL_URL = "channelUrl"

        fun createRoute(channelUrl: String): String =
            "channel/" + android.net.Uri.encode(channelUrl)
    }

    data object Playlist : Screen("playlist/{playlistId}?title={title}") {
        const val ARG_ID = "playlistId"
        const val ARG_TITLE = "title"

        fun createRoute(playlistId: String, title: String = ""): String =
            "playlist/" + android.net.Uri.encode(playlistId) + "?title=" + android.net.Uri.encode(title)
    }

    data object Subscriptions : Screen("subscriptions")
    data object Library : Screen("library")
    data object Settings : Screen("settings")
}
