package com.vancedclean.app.download

import android.content.Context
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.math.BigInteger
import java.util.concurrent.TimeUnit

/** Sesión de Instagram (cookies del inicio de sesión en la app). */
object InstagramSession {
    private const val PREFS = "instagram_session"
    private const val KEY = "cookies"

    fun save(context: Context, cookies: String) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, cookies).apply()

    fun cookies(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)?.takeIf { "sessionid=" in it }

    fun clear(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY).apply()

    fun isLoggedIn(context: Context) = cookies(context) != null
}

/**
 * Obtiene los videos de un reel/post de Instagram (mismos endpoints que usa SnapTube):
 * 1) API de la app con la sesión iniciada en Zaza (/api/v1/media/{id}/info/), lo más fiable;
 * 2) sin sesión: GraphQL público y la página /embed (Instagram los limita a menudo).
 */
object InstagramResolver {

    private const val APP_ID = "936619743392459"
    private const val UA = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/138.0.0.0 Safari/537.36"
    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"

    /** Contexto de la app para leer la sesión (se fija al abrir la app). */
    @Volatile
    var appContext: Context? = null

    /** Cabeceras para bajar los archivos de los servidores de Instagram. */
    val mediaHeaders: Map<String, String> = mapOf("Referer" to "https://www.instagram.com/", "User-Agent" to UA)

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    fun shortcode(url: String): String? =
        Regex("""instagram\.com/(?:[\w.]+/)?(?:p|reels?|tv)/([\w-]+)""").find(url)?.groupValues?.get(1)

    fun resolve(url: String): DownloadOptions {
        val code = shortcode(url) ?: error("No reconozco ese enlace de Instagram (debe ser un post o un reel)")
        val cookies = appContext?.let { InstagramSession.cookies(it) }
        val errors = mutableListOf<String>()

        if (cookies != null) {
            runCatching { return fromMediaApi(code, cookies, url) }.onFailure { errors += "api: ${it.message}" }
        }
        runCatching { return fromGraphql(code, url) }.onFailure { errors += "graphql: ${it.message}" }
        runCatching { return fromEmbed(code, url) }.onFailure { errors += "embed: ${it.message}" }

        android.util.Log.w("ZazaInstagram", errors.joinToString(" | "))
        error(
            if (cookies == null) "Instagram pide iniciar sesión para descargar este contenido. " +
                "Ve a Ajustes → Descargar → Iniciar sesión en Instagram."
            else "Instagram no dejó descargar este contenido (puede ser privado o haber cambiado)."
        )
    }

    // ---------- 1) API con sesión ----------

    private fun fromMediaApi(code: String, cookies: String, url: String): DownloadOptions {
        val id = mediaId(code)
        val csrf = Regex("""csrftoken=([^;]+)""").find(cookies)?.groupValues?.get(1).orEmpty()
        val json = getJson(
            "https://www.instagram.com/api/v1/media/$id/info/",
            mapOf("Cookie" to cookies, "X-IG-App-ID" to APP_ID, "X-CSRFToken" to csrf, "X-Requested-With" to "XMLHttpRequest")
        )
        val item = json.optJSONArray("items")?.optJSONObject(0) ?: throw IOException("sin datos")
        val medias = item.optJSONArray("carousel_media")?.let { arr -> (0 until arr.length()).map { arr.getJSONObject(it) } }
            ?: listOf(item)
        val videos = mutableListOf<VideoChoice>()
        val audios = mutableListOf<AudioChoice>()
        medias.forEachIndexed { index, m ->
            val prefix = if (medias.size > 1) "Video ${index + 1} · " else ""
            collectApiMedia(m, prefix, videos, audios)
        }
        if (videos.isEmpty()) throw IOException("La publicación no tiene videos")
        val caption = item.optJSONObject("caption")?.optString("text").orEmpty()
        return options(
            url, caption, item.optJSONObject("user")?.optString("username").orEmpty(),
            item.optJSONObject("image_versions2")?.optJSONArray("candidates")?.optJSONObject(0)?.optString("url"),
            item.optDouble("video_duration", 0.0).toLong(), videos, audios
        )
    }

    private fun collectApiMedia(m: JSONObject, prefix: String, videos: MutableList<VideoChoice>, audios: MutableList<AudioChoice>) {
        m.optString("video_dash_manifest").takeIf { it.isNotBlank() }?.let { addDash(it, prefix, videos, audios) }
        val versions = m.optJSONArray("video_versions") ?: return
        val best = (0 until versions.length()).map { versions.getJSONObject(it) }
            .distinctBy { it.optInt("height") }
            .sortedByDescending { it.optInt("height") }
        best.forEach { v ->
            videos += VideoChoice("${prefix}${v.optInt("height")}p", v.optInt("height"), v.getString("url"), null, 0)
        }
        best.firstOrNull()?.let {
            audios += AudioChoice("${prefix}Audio del video → MP3 128 kbps", it.getString("url"), 128, 128, 0)
        }
    }

    // ---------- 2) GraphQL público ----------

    private fun fromGraphql(code: String, url: String): DownloadOptions {
        val page = request(Request.Builder().url("https://www.instagram.com/p/$code/").header("User-Agent", UA).build())
        val lsd = Regex(""""LSD",\[],\{"token":"([^"]+)"""").find(page.body)?.groupValues?.get(1)
            ?: Regex(""""lsd":"([^"]+)"""").find(page.body)?.groupValues?.get(1).orEmpty()
        val csrf = page.cookies["csrftoken"].orEmpty()
        val variables = JSONObject().put("shortcode", code)
            .put("fetch_tagged_user_count", JSONObject.NULL)
            .put("hoisted_comment_id", JSONObject.NULL)
            .put("hoisted_reply_id", JSONObject.NULL)
        val body = FormBody.Builder()
            .add("doc_id", "8845758582119845")
            .add("variables", variables.toString())
            .add("lsd", lsd)
            .build()
        val res = request(
            Request.Builder().url("https://www.instagram.com/graphql/query").post(body)
                .header("User-Agent", UA)
                .header("X-IG-App-ID", APP_ID)
                .header("X-FB-LSD", lsd)
                .header("X-CSRFToken", csrf)
                .header("X-ASBD-ID", "129477")
                .header("X-FB-Friendly-Name", "PolarisPostActionLoadPostQueryQuery")
                .header("Cookie", page.cookies.entries.joinToString("; ") { "${it.key}=${it.value}" })
                .header("Referer", "https://www.instagram.com/p/$code/")
                .build()
        )
        val media = JSONObject(res.body).optJSONObject("data")?.optJSONObject("xdt_shortcode_media")
            ?: throw IOException(JSONObject(res.body).optString("message", "sin datos"))
        val nodes = media.optJSONObject("edge_sidecar_to_children")?.optJSONArray("edges")
            ?.let { e -> (0 until e.length()).mapNotNull { e.getJSONObject(it).optJSONObject("node") } }
            ?: listOf(media)
        val videos = mutableListOf<VideoChoice>()
        val audios = mutableListOf<AudioChoice>()
        nodes.filter { it.optBoolean("is_video") }.forEachIndexed { i, n ->
            val prefix = if (nodes.size > 1) "Video ${i + 1} · " else ""
            n.optJSONObject("dash_info")?.optString("video_dash_manifest")?.takeIf { it.isNotBlank() }
                ?.let { addDash(it, prefix, videos, audios) }
            val vu = n.optString("video_url").takeIf { it.isNotBlank() } ?: return@forEachIndexed
            val h = n.optJSONObject("dimensions")?.optInt("height") ?: 0
            videos += VideoChoice("${prefix}Original ${if (h > 0) "${h}p" else ""}".trim(), h, vu, null, 0)
            audios += AudioChoice("${prefix}Audio del video → MP3 128 kbps", vu, 128, 128, 0)
        }
        if (videos.isEmpty()) throw IOException("La publicación no tiene videos")
        val caption = media.optJSONObject("edge_media_to_caption")?.optJSONArray("edges")
            ?.optJSONObject(0)?.optJSONObject("node")?.optString("text").orEmpty()
        return options(url, caption, media.optJSONObject("owner")?.optString("username").orEmpty(),
            media.optString("display_url"), media.optDouble("video_duration", 0.0).toLong(), videos, audios)
    }

    // ---------- 3) Página /embed ----------

    private fun fromEmbed(code: String, url: String): DownloadOptions {
        val html = request(Request.Builder().url("https://www.instagram.com/p/$code/embed/captioned/")
            .header("User-Agent", UA).build()).body
        val raw = Regex("""video_url\\?":\\?"(.*?)\\?"""").find(html)?.groupValues?.get(1)
            ?: Regex("""<video[^>]+src="([^"]+)"""").find(html)?.groupValues?.get(1)
            ?: throw IOException("sin video en embed")
        val vu = raw.replace("\\\\/", "/").replace("\\/", "/").replace("\\u0026", "&").replace("&amp;", "&")
        val user = Regex("""class="UsernameText"[^>]*>([^<]+)<""").find(html)?.groupValues?.get(1).orEmpty()
        return options(url, "", user, null, 0,
            listOf(VideoChoice("Original", 0, vu, null, 0)),
            listOf(AudioChoice("Audio del video → MP3 128 kbps", vu, 128, 128, 0)))
    }

    // ---------- Utilidades ----------

    /** DASH de Instagram: video H.264 sin audio + audio AAC aparte (como YouTube). */
    private fun addDash(manifest: String, prefix: String, videos: MutableList<VideoChoice>, audios: MutableList<AudioChoice>) {
        val reps = Regex("""<Representation\b([^>]*)>(.*?)</Representation>""", RegexOption.DOT_MATCHES_ALL).findAll(manifest)
        val list = reps.mapNotNull { r ->
            val attrs = r.groupValues[1]
            val base = Regex("""<BaseURL>(.*?)</BaseURL>""").find(r.groupValues[2])?.groupValues?.get(1) ?: return@mapNotNull null
            Triple(attrs, base.replace("&amp;", "&"), attrs.contains("audio"))
        }.toList()
        val audio = list.filter { it.third || Regex("""mimeType="audio""").containsMatchIn(it.first) }
            .maxByOrNull { attr(it.first, "bandwidth")?.toIntOrNull() ?: 0 }
        list.filter { Regex("""mimeType="video""").containsMatchIn(it.first) && attr(it.first, "codecs")?.startsWith("avc") != false }
            .sortedByDescending { attr(it.first, "height")?.toIntOrNull() ?: 0 }
            .distinctBy { attr(it.first, "height") }
            .forEach { v ->
                val h = attr(v.first, "height")?.toIntOrNull() ?: 0
                if (audio != null) videos += VideoChoice("${prefix}${h}p", h, v.second, audio.second, 0)
            }
        audio?.let {
            val kbps = (attr(it.first, "bandwidth")?.toIntOrNull() ?: 128_000) / 1000
            audios += AudioChoice("${prefix}AAC $kbps kbps → MP3 ${if (kbps >= 100) 192 else 128} kbps", it.second, kbps,
                if (kbps >= 100) 192 else 128, 0)
        }
    }

    private fun attr(attrs: String, name: String) = Regex("""\b$name="([^"]*)"""").find(attrs)?.groupValues?.get(1)

    private fun options(url: String, caption: String, user: String, thumb: String?, duration: Long,
                        videos: List<VideoChoice>, audios: List<AudioChoice>) = DownloadOptions(
        source = DlSource.INSTAGRAM,
        pageUrl = url,
        title = caption.lineSequence().firstOrNull()?.take(70)?.ifBlank { null } ?: "Instagram de @$user",
        author = if (user.isNotBlank()) "@$user" else "Instagram",
        thumbnailUrl = thumb,
        durationSec = duration,
        videos = videos.distinctBy { it.videoUrl }.sortedByDescending { it.height },
        audios = audios.distinctBy { it.url },
    )

    /** id numérico del post a partir del código del enlace. */
    private fun mediaId(code: String): String {
        var n = BigInteger.ZERO
        for (c in code.take(11)) {
            val i = ALPHABET.indexOf(c)
            if (i < 0) break
            n = n.shiftLeft(6).add(BigInteger.valueOf(i.toLong()))
        }
        return n.toString()
    }

    private class Page(val body: String, val cookies: Map<String, String>)

    private fun request(req: Request): Page = client.newCall(req).execute().use { resp ->
        val body = resp.body?.string().orEmpty()
        if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
        val cookies = resp.headers("Set-Cookie").mapNotNull {
            it.substringBefore(';').split('=', limit = 2).takeIf { p -> p.size == 2 }?.let { p -> p[0] to p[1] }
        }.toMap()
        Page(body, cookies)
    }

    private fun getJson(url: String, headers: Map<String, String>): JSONObject {
        val b = Request.Builder().url(url).header("User-Agent", UA)
        headers.forEach { (k, v) -> b.header(k, v) }
        return JSONObject(request(b.build()).body)
    }

    @Suppress("unused")
    private fun JSONArray.objects() = (0 until length()).map { getJSONObject(it) }
}
