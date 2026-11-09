package com.vancedclean.app.download

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Descarga por trozos con la cabecera Range (como hace el reproductor): googlevideo corta o
 * frena las descargas de un solo golpe, y así además se puede reintentar solo el trozo que falló.
 */
object HttpDownloader {

    private const val CHUNK = 4L * 1024 * 1024

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    // Mismo User-Agent que el reproductor (el de Android): con el de un navegador YouTube respondía 403.
    private val userAgent: String = System.getProperty("http.agent")?.takeIf { it.isNotBlank() } ?: "Dalvik/2.1.0 (Linux; U; Android 14)"

    suspend fun download(
        url: String,
        target: File,
        headers: Map<String, String> = emptyMap(),
        onProgress: (downloaded: Long, total: Long) -> Unit,
    ) {
        target.parentFile?.mkdirs()
        var total = -1L
        var pos = 0L
        FileOutputStream(target).use { out ->
            while (total < 0 || pos < total) {
                currentCoroutineContext().ensureActive()
                val end = pos + CHUNK - 1
                var attempt = 0
                while (true) {
                    try {
                        val req = Request.Builder().url(url)
                            .header("User-Agent", userAgent)
                            .header("Range", "bytes=$pos-$end")
                            .apply { headers.forEach { (k, v) -> header(k, v) } }
                            .build()
                        client.newCall(req).execute().use { resp ->
                            if (resp.code == 416 && total > 0) return
                            if (!resp.isSuccessful) throw IOException("El servidor respondió ${resp.code}")
                            val body = resp.body ?: throw IOException("Respuesta vacía")
                            if (resp.code == 200) {
                                // El servidor ignoró el Range: viene el archivo entero de una vez.
                                total = body.contentLength()
                                pos = 0
                                out.channel.truncate(0)
                            } else if (total < 0) {
                                total = resp.header("Content-Range")?.substringAfterLast('/')?.toLongOrNull() ?: -1L
                            }
                            val input = body.byteStream()
                            val buf = ByteArray(64 * 1024)
                            var lastReport = 0L
                            while (true) {
                                val n = input.read(buf)
                                if (n < 0) break
                                out.write(buf, 0, n)
                                pos += n
                                if (pos - lastReport > 256 * 1024) {
                                    lastReport = pos
                                    onProgress(pos, total)
                                }
                            }
                            if (resp.code == 200 || total < 0) total = pos
                        }
                        break
                    } catch (e: IOException) {
                        if (++attempt >= 3) throw e
                        currentCoroutineContext().ensureActive()
                    }
                }
                onProgress(pos, total)
            }
        }
    }
}
