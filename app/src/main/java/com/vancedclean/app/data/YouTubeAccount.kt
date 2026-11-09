package com.vancedclean.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

enum class LikeStatus { LIKE, DISLIKE, NONE }

/** Estado del video para la cuenta: like y suscripción al canal. */
data class VideoAccountState(
    val likeStatus: LikeStatus = LikeStatus.NONE,
    val subscribed: Boolean = false,
    val channelId: String? = null,
)

data class PlaylistOption(val id: String, val title: String, val containsVideo: Boolean)

data class PlaylistSummary(val id: String, val title: String, val thumbnailUrl: String?, val subtitle: String)

data class ChannelSummary(val id: String, val name: String, val avatarUrl: String?) {
    val url get() = "https://www.youtube.com/channel/$id"
}

/**
 * Acciones con la cuenta de Google (como hacía la app vieja con su VideoYtbDataService,
 * SubscriptionYtbDataService y PlaylistYtbDataService): todo se guarda en YouTube,
 * así que se ve también en youtube.com.
 */
object YouTubeAccount {

    private fun cookies(context: Context): String =
        CookieStorage.getCookies(context) ?: throw IOException("Inicia sesión para usar esta función")

    fun isLoggedIn(context: Context) = CookieStorage.isLoggedIn(context)

    // ---------- Like / suscripción ----------

    fun videoState(context: Context, videoId: String): VideoAccountState {
        val json = InnerTube.post("next", JSONObject().put("videoId", videoId), cookies(context))
        val like = findAll(json, "likeStatus").filterIsInstance<String>()
            .firstOrNull { it == "LIKE" || it == "DISLIKE" || it == "INDIFFERENT" }
        val button = findAll(json, "subscribeButtonRenderer").filterIsInstance<JSONObject>().firstOrNull()
        val subscribed = button?.optBoolean("subscribed")
            ?: findAll(json, "subscriptionStateEntity").filterIsInstance<JSONObject>()
                .firstOrNull()?.optBoolean("subscribed")
            ?: false
        return VideoAccountState(
            likeStatus = when (like) {
                "LIKE" -> LikeStatus.LIKE
                "DISLIKE" -> LikeStatus.DISLIKE
                else -> LikeStatus.NONE
            },
            subscribed = subscribed,
            channelId = button?.optString("channelId")?.takeIf { it.isNotBlank() },
        )
    }

    fun setLike(context: Context, videoId: String, status: LikeStatus) {
        val endpoint = when (status) {
            LikeStatus.LIKE -> "like/like"
            LikeStatus.DISLIKE -> "like/dislike"
            LikeStatus.NONE -> "like/removelike"
        }
        InnerTube.post(endpoint, JSONObject().put("target", JSONObject().put("videoId", videoId)), cookies(context))
    }

    fun setSubscribed(context: Context, channelId: String, subscribe: Boolean) {
        val endpoint = if (subscribe) "subscription/subscribe" else "subscription/unsubscribe"
        InnerTube.post(endpoint, JSONObject().put("channelIds", JSONArray().put(channelId)), cookies(context))
    }

    fun isSubscribed(context: Context, channelId: String): Boolean {
        val json = InnerTube.post("browse", JSONObject().put("browseId", channelId), cookies(context))
        return findAll(json, "subscribeButtonRenderer").filterIsInstance<JSONObject>()
            .firstOrNull()?.optBoolean("subscribed")
            ?: findAll(json, "subscriptionStateEntity").filterIsInstance<JSONObject>()
                .firstOrNull()?.optBoolean("subscribed")
            ?: false
    }

    // ---------- Playlists y Ver más tarde ----------

    /** Playlists del usuario indicando si ya contienen el video (incluye "Ver más tarde"). */
    fun playlistsForVideo(context: Context, videoId: String): List<PlaylistOption> {
        val json = InnerTube.post(
            "playlist/get_add_to_playlist",
            JSONObject().put("videoIds", JSONArray().put(videoId)),
            cookies(context)
        )
        return findAll(json, "playlistAddToOptionRenderer").filterIsInstance<JSONObject>().mapNotNull {
            val id = it.optString("playlistId").takeIf { s -> s.isNotBlank() } ?: return@mapNotNull null
            PlaylistOption(id, it.optJSONObject("title").ytText(), it.optString("containsSelectedVideos") == "ALL")
        }
    }

    fun addToPlaylist(context: Context, playlistId: String, videoId: String) =
        editPlaylist(context, playlistId, JSONObject().put("action", "ACTION_ADD_VIDEO").put("addedVideoId", videoId))

    fun removeFromPlaylist(context: Context, playlistId: String, videoId: String) =
        editPlaylist(
            context, playlistId,
            JSONObject().put("action", "ACTION_REMOVE_VIDEO_BY_VIDEO_ID").put("removedVideoId", videoId)
        )

    private fun editPlaylist(context: Context, playlistId: String, action: JSONObject) {
        val body = JSONObject().put("playlistId", playlistId).put("actions", JSONArray().put(action))
        val json = InnerTube.post("browse/edit_playlist", body, cookies(context))
        if (json.optString("status") == "STATUS_FAILED") throw IOException("YouTube no aceptó el cambio")
    }

    fun createPlaylist(context: Context, title: String, videoId: String?): String {
        val body = JSONObject().put("title", title).put("privacyStatus", "PRIVATE")
        if (videoId != null) body.put("videoIds", JSONArray().put(videoId))
        val json = InnerTube.post("playlist/create", body, cookies(context))
        return json.optString("playlistId")
    }

    fun deletePlaylist(context: Context, playlistId: String) {
        InnerTube.post("playlist/delete", JSONObject().put("playlistId", playlistId), cookies(context))
    }

    /** Playlists guardadas y creadas por el usuario (Biblioteca). */
    fun libraryPlaylists(context: Context): List<PlaylistSummary> {
        val json = InnerTube.post("browse", JSONObject().put("browseId", "FEplaylist_aggregation"), cookies(context))
        val lockups = findAll(json, "lockupViewModel").filterIsInstance<JSONObject>()
            .filter { it.optString("contentType") == "LOCKUP_CONTENT_TYPE_PLAYLIST" }
            .mapNotNull { lockup ->
                val id = lockup.optString("contentId").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val meta = lockup.optJSONObject("metadata")?.optJSONObject("lockupMetadataViewModel")
                val subtitle = findAll(meta, "metadataParts").filterIsInstance<JSONArray>()
                    .flatMap { parts -> (0 until parts.length()).map { parts.getJSONObject(it).optJSONObject("text").ytText() } }
                    .filter { it.isNotBlank() }
                    .joinToString(" • ")
                PlaylistSummary(id, meta?.optJSONObject("title").ytText().orEmpty(),
                    bestThumbnail(lockup.optJSONObject("contentImage")), subtitle)
            }
        val grids = findAll(json, "gridPlaylistRenderer").filterIsInstance<JSONObject>().mapNotNull {
            val id = it.optString("playlistId").takeIf { s -> s.isNotBlank() } ?: return@mapNotNull null
            PlaylistSummary(id, it.optJSONObject("title").ytText(), bestThumbnail(it.opt("thumbnail")),
                it.optJSONObject("videoCountShortText").ytText())
        }
        return (lockups + grids).distinctBy { it.id }
    }

    // ---------- Canales e inicio ----------

    fun subscribedChannels(context: Context): List<ChannelSummary> {
        val json = InnerTube.post("browse", JSONObject().put("browseId", "FEchannels"), cookies(context))
        return findAll(json, "channelRenderer").filterIsInstance<JSONObject>().mapNotNull {
            val id = it.optString("channelId").takeIf { s -> s.isNotBlank() } ?: return@mapNotNull null
            ChannelSummary(id, it.optJSONObject("title").ytText(), bestThumbnail(it.opt("thumbnail")))
        }.distinctBy { it.id }
    }

    // ---------- Historial ----------

    /** Registra el video en el historial de YouTube (lo que hacía addToHistory en la app vieja). */
    fun addToHistory(context: Context, videoId: String) {
        val cookies = cookies(context)
        val player = InnerTube.post("player", JSONObject().put("videoId", videoId), cookies)
        val base = player.optJSONObject("playbackTracking")
            ?.optJSONObject("videostatsPlaybackUrl")
            ?.optString("baseUrl")
            ?.takeIf { it.isNotBlank() } ?: return
        val cpnChars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"
        val cpn = (1..16).map { cpnChars.random() }.joinToString("")
        InnerTube.get("$base&ver=2&cpn=$cpn&c=WEB&cver=${InnerTube.CLIENT_VERSION}", cookies)
    }
}
