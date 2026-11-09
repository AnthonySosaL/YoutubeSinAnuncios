package com.vancedclean.app.navigation

import android.content.Intent
import android.net.Uri
import androidx.navigation.NavController
import com.vancedclean.app.player.PlayerSheet

/** Abre en la app los enlaces de YouTube que llegan desde otras apps (ver o compartir). */
object DeepLinks {

    private val URL_REGEX = Regex("""https?://\S+""")

    fun handle(intent: Intent, navController: NavController) {
        val uri = when (intent.action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)
                ?.let { URL_REGEX.find(it)?.value }
                ?.let(Uri::parse)
            else -> null
        } ?: return
        open(uri, navController)
    }

    private fun open(uri: Uri, navController: NavController) {
        val host = uri.host?.lowercase() ?: return
        if (!host.endsWith("youtube.com") && host != "youtu.be") return
        val segments = uri.pathSegments

        val videoId = when {
            host == "youtu.be" -> segments.firstOrNull()
            segments.firstOrNull() in setOf("shorts", "live", "embed", "v") -> segments.getOrNull(1)
            else -> uri.getQueryParameter("v")
        }
        if (!videoId.isNullOrBlank()) {
            PlayerSheet.open("https://www.youtube.com/watch?v=$videoId")
            return
        }

        uri.getQueryParameter("list")?.takeIf { it.isNotBlank() }?.let {
            navController.navigate(Screen.Playlist.createRoute(it))
            return
        }

        val first = segments.firstOrNull() ?: return
        if (first.startsWith("@") || first in setOf("channel", "c", "user")) {
            navController.navigate(Screen.Channel.createRoute(uri.toString()))
        }
    }
}
