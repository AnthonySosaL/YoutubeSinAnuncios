package com.vancedclean.app.viewmodel

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import java.util.concurrent.TimeUnit

class DownloaderImpl private constructor() : Downloader() {

    /** Cookies de sesión ("SID=xxx; HSID=yyy; ..."). Se actualiza con [setCookies]. */
    @Volatile
    private var cookieString: String? = null

    fun setCookies(cookies: String?) {
        cookieString = cookies?.trim()?.takeIf { it.isNotEmpty() }
    }

    /**
     * Interceptor de red (se ejecuta en cada salto, incluidas las redirecciones).
     * Solo añade la cabecera Cookie si el host es youtube.com, googleapis.com o
     * google.com (o un subdominio exacto de ellos), de modo que las cookies nunca
     * se envíen a otros dominios, ni siquiera tras una redirección.
     */
    private val cookieInterceptor = Interceptor { chain ->
        val request = chain.request()
        val cookies = cookieString

        if (cookies.isNullOrBlank() || !isCookieHost(request.url.host)) {
            chain.proceed(request)
        } else {
            val existing = request.header("Cookie")
            val merged = if (existing.isNullOrBlank()) cookies else "$existing; $cookies"
            chain.proceed(request.newBuilder().header("Cookie", merged).build())
        }
    }

    // Verificación SSL estándar del sistema: sin TrustAllCerts ni HostnameVerifier personalizado.
    private val client = OkHttpClient.Builder()
        .readTimeout(30, TimeUnit.SECONDS)
        .connectTimeout(20, TimeUnit.SECONDS)
        .addNetworkInterceptor(cookieInterceptor)
        .build()

    override fun execute(request: Request): Response {
        val httpMethod = request.httpMethod()
        val url = request.url()
        val headers = request.headers()
        val dataToSend = request.dataToSend()

        val body = when {
            dataToSend != null -> dataToSend.toRequestBody()
            httpMethod.equals("POST", ignoreCase = true) ||
                httpMethod.equals("PUT", ignoreCase = true) ||
                httpMethod.equals("PATCH", ignoreCase = true) -> ByteArray(0).toRequestBody()
            else -> null
        }

        val requestBuilder = okhttp3.Request.Builder()
            .url(url)
            .method(httpMethod, body)

        for ((headerName, headerValueList) in headers) {
            if (headerValueList.size > 1) {
                requestBuilder.removeHeader(headerName)
                for (headerValue in headerValueList) {
                    requestBuilder.addHeader(headerName, headerValue)
                }
            } else if (headerValueList.size == 1) {
                requestBuilder.header(headerName, headerValueList[0])
            }
        }

        client.newCall(requestBuilder.build()).execute().use { response ->
            val responseBodyString = response.body?.string() ?: ""

            val responseHeaders: MutableMap<String, List<String>> = mutableMapOf()
            for (name in response.headers.names()) {
                responseHeaders[name] = response.headers.values(name)
            }

            return Response(
                response.code,
                response.message,
                responseHeaders,
                responseBodyString,
                response.request.url.toString()
            )
        }
    }

    companion object {
        private val COOKIE_DOMAINS = listOf("youtube.com", "googleapis.com", "google.com")

        @Volatile
        private var instance: DownloaderImpl? = null

        fun getInstance(): DownloaderImpl =
            instance ?: synchronized(this) {
                instance ?: DownloaderImpl().also { instance = it }
            }

        private fun isCookieHost(host: String): Boolean {
            val normalized = host.lowercase()
            return COOKIE_DOMAINS.any { domain ->
                normalized == domain || normalized.endsWith(".$domain")
            }
        }
    }
}
