package com.vancedclean.app.data

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * Cliente mínimo de la API interna de YouTube (InnerTube) con la sesión del usuario.
 * Las cookies vienen del login con WebView; la cabecera SAPISIDHASH es la misma
 * que usa youtube.com para autenticar las peticiones.
 */
object InnerTube {

    const val CLIENT_VERSION = "2.20241105.01.00"
    private const val ORIGIN = "https://www.youtube.com"
    private const val USER_AGENT = "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

    /** País para el contenido (Ajustes → País / región). */
    @Volatile
    var country: String = "US"

    val client: OkHttpClient = OkHttpClient.Builder()
        .readTimeout(30, TimeUnit.SECONDS)
        .connectTimeout(20, TimeUnit.SECONDS)
        .build()

    /** POST a /youtubei/v1/[endpoint]. Lanza IOException si YouTube responde con error. */
    fun post(endpoint: String, body: JSONObject, cookies: String?): JSONObject {
        body.put("context", JSONObject().put("client", JSONObject().apply {
            put("clientName", "WEB")
            put("clientVersion", CLIENT_VERSION)
            put("hl", "es")
            put("gl", country)
        }))

        val request = Request.Builder()
            .url("$ORIGIN/youtubei/v1/$endpoint?prettyPrint=false")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .applyAuth(cookies)
            .build()

        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw IOException("YouTube respondió ${response.code}")
            return JSONObject(text)
        }
    }

    /** GET autenticado (para los pings de historial). */
    fun get(url: String, cookies: String?) {
        val request = Request.Builder().url(url).get().applyAuth(cookies).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("YouTube respondió ${response.code}")
        }
    }

    private fun Request.Builder.applyAuth(cookies: String?): Request.Builder {
        addHeader("Origin", ORIGIN)
        addHeader("X-Origin", ORIGIN)
        addHeader("Referer", "$ORIGIN/")
        addHeader("User-Agent", USER_AGENT)
        if (cookies.isNullOrBlank()) return this
        addHeader("Cookie", cookies)
        addHeader("X-Goog-AuthUser", "0")
        val sapisid = cookieValue(cookies, "SAPISID") ?: cookieValue(cookies, "__Secure-3PAPISID")
        if (sapisid != null) {
            val ts = System.currentTimeMillis() / 1000
            addHeader("Authorization", "SAPISIDHASH ${ts}_${sha1("$ts $sapisid $ORIGIN")}")
        }
        return this
    }

    private fun cookieValue(cookies: String, name: String): String? =
        cookies.split(";").map { it.trim() }
            .find { it.startsWith("$name=") }
            ?.substringAfter("=")
            ?.takeIf { it.isNotBlank() }

    private fun sha1(input: String): String =
        MessageDigest.getInstance("SHA-1").digest(input.toByteArray())
            .joinToString("") { "%02x".format(it) }
}

/** Busca recursivamente todos los valores de [key] dentro de un JSON de YouTube. */
fun findAll(obj: Any?, key: String, out: MutableList<Any> = mutableListOf(), depth: Int = 0): List<Any> {
    if (depth > 60 || obj == null) return out
    when (obj) {
        is JSONObject -> for (k in obj.keys()) {
            val v = obj.get(k)
            if (k == key) out.add(v)
            findAll(v, key, out, depth + 1)
        }
        is JSONArray -> for (i in 0 until obj.length()) findAll(obj.get(i), key, out, depth + 1)
    }
    return out
}

/** Texto de un objeto de texto de YouTube (simpleText, runs o content). */
fun JSONObject?.ytText(): String {
    if (this == null) return ""
    optString("simpleText").takeIf { it.isNotBlank() }?.let { return it }
    optString("content").takeIf { it.isNotBlank() }?.let { return it }
    val runs = optJSONArray("runs") ?: return ""
    return buildString { for (i in 0 until runs.length()) append(runs.getJSONObject(i).optString("text")) }
}

/** URL de la miniatura más grande dentro de un objeto con "thumbnails" o "sources". */
fun bestThumbnail(obj: Any?): String? {
    val lists = findAll(obj, "thumbnails") + findAll(obj, "sources")
    val arr = lists.firstOrNull { it is JSONArray && it.length() > 0 } as? JSONArray ?: return null
    val url = arr.getJSONObject(arr.length() - 1).optString("url")
    return when {
        url.isBlank() -> null
        url.startsWith("//") -> "https:$url"
        else -> url
    }
}
