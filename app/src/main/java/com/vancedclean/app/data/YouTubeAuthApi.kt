package com.vancedclean.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object YouTubeAuthApi {

    data class VideoResult(
        val url: String,
        val title: String,
        val thumbnailUrl: String,
        val channelName: String,
        val channelAvatarUrl: String?,
        val viewCountText: String?,
        val durationText: String?,
    )

    fun fetchSubscriptions(context: Context): List<VideoResult> {
        val cookies = CookieStorage.getCookies(context) ?: return emptyList()
        return fetchBrowse("FEsubscriptions", cookies)
    }

    fun fetchHistory(context: Context): List<VideoResult> {
        val cookies = CookieStorage.getCookies(context) ?: return emptyList()
        return fetchBrowse("FEhistory", cookies)
    }

    /** Inicio personalizado con las recomendaciones de la cuenta. */
    fun fetchHome(context: Context): List<VideoResult> {
        val cookies = CookieStorage.getCookies(context) ?: return emptyList()
        return fetchBrowse("FEwhat_to_watch", cookies)
    }

    /** Videos de una playlist; las públicas funcionan también sin sesión. */
    fun fetchPlaylist(context: Context, listId: String): List<VideoResult> =
        fetchBrowse("VL$listId", CookieStorage.getCookies(context))

    private fun fetchBrowse(browseId: String, cookies: String?): List<VideoResult> {
        val json = InnerTube.post("browse", JSONObject().put("browseId", browseId), cookies)
        return try {
            findAllVideos(json).distinctBy { it.url }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun findAllVideos(json: JSONObject): List<VideoResult> {
        val results = mutableListOf<VideoResult>()
        findVideoRenderers(json, results, 0)
        return results
    }

    private fun findVideoRenderers(obj: Any, results: MutableList<VideoResult>, depth: Int) {
        if (depth > 30) return
        when (obj) {
            is JSONObject -> {
                val lockup = obj.optJSONObject("lockupViewModel")
                if (lockup != null && lockup.has("contentId")) {
                    val type = lockup.optString("contentType")
                    if (type.isNotBlank() && type != "LOCKUP_CONTENT_TYPE_VIDEO") return
                    parseLockupViewModel(lockup)?.let { results.add(it) }
                    return
                }
                val renderer = obj.optJSONObject("videoRenderer")
                    ?: obj.optJSONObject("gridVideoRenderer")
                    ?: obj.optJSONObject("compactVideoRenderer")
                    ?: obj.optJSONObject("playlistVideoRenderer")
                if (renderer != null) {
                    parseVideoRenderer(renderer)?.let { results.add(it) }
                } else {
                    for (key in obj.keys()) {
                        findVideoRenderers(obj.get(key), results, depth + 1)
                    }
                }
            }
            is JSONArray -> {
                for (i in 0 until obj.length()) {
                    findVideoRenderers(obj.get(i), results, depth + 1)
                }
            }
        }
    }

    private fun parseVideoRenderer(renderer: JSONObject): VideoResult? {
        val videoId = renderer.optString("videoId", "")
        if (videoId.isBlank()) return null

        val title = renderer.optJSONObject("title")?.let { titleObj ->
            titleObj.optJSONArray("runs")?.let { runs ->
                buildString {
                    for (i in 0 until runs.length()) {
                        append(runs.getJSONObject(i).optString("text", ""))
                    }
                }
            } ?: titleObj.optString("simpleText", "")
        } ?: ""

        if (title.isBlank()) return null

        val thumbnailUrl = renderer.optJSONObject("thumbnail")
            ?.optJSONArray("thumbnails")
            ?.let { thumbs ->
                if (thumbs.length() > 0)
                    thumbs.getJSONObject(thumbs.length() - 1).optString("url", "")
                else ""
            } ?: ""

        val channelName = (renderer.optJSONObject("longBylineText")
            ?: renderer.optJSONObject("shortBylineText"))
            ?.optJSONArray("runs")
            ?.let { runs ->
                if (runs.length() > 0) runs.getJSONObject(0).optString("text", "")
                else ""
            } ?: ""

        val channelAvatarUrl = renderer.optJSONObject("channelThumbnailSupportedRenderers")
            ?.optJSONObject("channelThumbnailWithLinkRenderer")
            ?.optJSONObject("thumbnail")
            ?.optJSONArray("thumbnails")
            ?.let { thumbs ->
                if (thumbs.length() > 0) thumbs.getJSONObject(0).optString("url", null)
                else null
            }

        val viewCountText = renderer.optJSONObject("viewCountText")?.let { vct ->
            vct.optString("simpleText", null)
                ?: vct.optJSONArray("runs")?.let { runs ->
                    buildString {
                        for (i in 0 until runs.length()) {
                            append(runs.getJSONObject(i).optString("text", ""))
                        }
                    }
                }
        }

        val durationText = renderer.optJSONObject("lengthText")
            ?.optString("simpleText", null)
            ?: renderer.optJSONObject("thumbnailOverlays")
                ?.let { findDurationFromOverlays(it) }

        return VideoResult(
            url = "https://www.youtube.com/watch?v=$videoId",
            title = title,
            thumbnailUrl = thumbnailUrl,
            channelName = channelName,
            channelAvatarUrl = channelAvatarUrl,
            viewCountText = viewCountText,
            durationText = durationText,
        )
    }

    private fun parseLockupViewModel(lockup: JSONObject): VideoResult? {
        val videoId = lockup.optString("contentId", "")
        if (videoId.isBlank()) return null

        val lmvm = lockup.optJSONObject("metadata")
            ?.optJSONObject("lockupMetadataViewModel") ?: return null

        val title = lmvm.optJSONObject("title")
            ?.optString("content", "") ?: ""
        if (title.isBlank()) return null

        val metadataRows = lmvm.optJSONObject("metadata")
            ?.optJSONObject("contentMetadataViewModel")
            ?.optJSONArray("metadataRows")

        var channelName = ""
        var viewCountText: String? = null
        if (metadataRows != null && metadataRows.length() > 0) {
            channelName = metadataRows.getJSONObject(0)
                .optJSONArray("metadataParts")
                ?.optJSONObject(0)
                ?.optJSONObject("text")
                ?.optString("content", "") ?: ""

            if (metadataRows.length() > 1) {
                viewCountText = metadataRows.getJSONObject(1)
                    .optJSONArray("metadataParts")
                    ?.optJSONObject(0)
                    ?.optJSONObject("text")
                    ?.optString("content", null)
            }
        }

        var thumbnailUrl = ""
        val contentImage = lockup.optJSONObject("contentImage")
        if (contentImage != null) {
            thumbnailUrl = findThumbnailUrl(contentImage) ?: ""
        }

        val channelAvatarUrl = lmvm.optJSONObject("image")
            ?.let { findThumbnailUrl(it) }

        var durationText: String? = null
        if (contentImage != null) {
            durationText = findDurationInOverlays(contentImage)
        }

        return VideoResult(
            url = "https://www.youtube.com/watch?v=$videoId",
            title = title,
            thumbnailUrl = thumbnailUrl,
            channelName = channelName,
            channelAvatarUrl = channelAvatarUrl,
            viewCountText = viewCountText,
            durationText = durationText,
        )
    }

    private fun findThumbnailUrl(obj: Any): String? {
        when (obj) {
            is JSONObject -> {
                val sources = obj.optJSONArray("sources")
                if (sources != null && sources.length() > 0) {
                    return sources.getJSONObject(sources.length() - 1).optString("url", null)
                }
                for (key in obj.keys()) {
                    val found = findThumbnailUrl(obj.get(key))
                    if (found != null) return found
                }
            }
            is JSONArray -> {
                for (i in 0 until obj.length()) {
                    val found = findThumbnailUrl(obj.get(i))
                    if (found != null) return found
                }
            }
        }
        return null
    }

    private fun findDurationInOverlays(obj: Any): String? {
        when (obj) {
            is JSONObject -> {
                val badge = obj.optJSONObject("thumbnailBadgeViewModel")
                if (badge != null) {
                    val text = badge.optString("text", null)
                    if (text != null && text.contains(":")) return text
                }
                for (key in obj.keys()) {
                    val found = findDurationInOverlays(obj.get(key))
                    if (found != null) return found
                }
            }
            is JSONArray -> {
                for (i in 0 until obj.length()) {
                    val found = findDurationInOverlays(obj.get(i))
                    if (found != null) return found
                }
            }
        }
        return null
    }

    private fun findDurationFromOverlays(overlays: Any): String? {
        when (overlays) {
            is JSONArray -> {
                for (i in 0 until overlays.length()) {
                    val overlay = overlays.getJSONObject(i)
                    val timeStatus = overlay.optJSONObject("thumbnailOverlayTimeStatusRenderer")
                    if (timeStatus != null) {
                        return timeStatus.optJSONObject("text")
                            ?.optString("simpleText", null)
                    }
                }
            }
            is JSONObject -> {
                val timeStatus = overlays.optJSONObject("thumbnailOverlayTimeStatusRenderer")
                if (timeStatus != null) {
                    return timeStatus.optJSONObject("text")
                        ?.optString("simpleText", null)
                }
            }
        }
        return null
    }
}
