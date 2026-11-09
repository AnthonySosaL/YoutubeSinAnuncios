package com.vancedclean.app.download

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

enum class DlFormat(val ext: String, val label: String) { MP4("mp4", "Video MP4"), MP3("mp3", "Audio MP3") }

enum class DlSource(val label: String) { YOUTUBE("YouTube"), INSTAGRAM("Instagram") }

/** Un archivo ya descargado por la app. */
data class DownloadEntry(
    val id: String,
    val title: String,
    val author: String,
    val thumbnailUrl: String?,
    val source: DlSource,
    val format: DlFormat,
    val quality: String,
    val path: String,
    val sizeBytes: Long,
    val createdAt: Long,
) {
    val file get() = File(path)

    /** Solo los MP3 de YouTube van al reproductor de música (pedido de Anthony). */
    val isMusic get() = format == DlFormat.MP3 && source == DlSource.YOUTUBE
}

/**
 * Índice propio de descargas. Los archivos viven en la carpeta privada de la app
 * (Android/data/.../files/descargas) con un ".nomedia", así que la galería y otras apps
 * no los mezclan, y el reproductor de música solo lista lo que está en este índice:
 * nunca los MP3 que ya hubiera en el teléfono.
 */
object DownloadStore {

    private const val INDEX = "descargas.json"
    private val _entries = MutableStateFlow<List<DownloadEntry>>(emptyList())
    val entries: StateFlow<List<DownloadEntry>> = _entries.asStateFlow()
    private var loaded = false

    fun folder(context: Context, format: DlFormat): File {
        val base = context.getExternalFilesDir(null) ?: context.filesDir
        val root = File(base, "descargas").apply { mkdirs() }
        File(root, ".nomedia").let { if (!it.exists()) it.createNewFile() }
        return File(root, if (format == DlFormat.MP3) "musica" else "videos").apply { mkdirs() }
    }

    @Synchronized
    fun load(context: Context) {
        if (loaded) return
        loaded = true
        val f = File(context.filesDir, INDEX)
        if (!f.exists()) return
        _entries.value = runCatching {
            val arr = JSONArray(f.readText())
            (0 until arr.length()).map { fromJson(arr.getJSONObject(it)) }.filter { it.file.exists() }
        }.getOrDefault(emptyList())
    }

    @Synchronized
    fun add(context: Context, entry: DownloadEntry) {
        load(context)
        _entries.value = listOf(entry) + _entries.value.filterNot { it.id == entry.id }
        save(context)
    }

    @Synchronized
    fun delete(context: Context, id: String) {
        load(context)
        _entries.value.firstOrNull { it.id == id }?.file?.delete()
        _entries.value = _entries.value.filterNot { it.id == id }
        save(context)
    }

    private fun save(context: Context) {
        val arr = JSONArray()
        _entries.value.forEach { arr.put(toJson(it)) }
        File(context.filesDir, INDEX).writeText(arr.toString())
    }

    private fun toJson(e: DownloadEntry) = JSONObject().apply {
        put("id", e.id); put("title", e.title); put("author", e.author)
        put("thumb", e.thumbnailUrl ?: ""); put("source", e.source.name); put("format", e.format.name)
        put("quality", e.quality); put("path", e.path); put("size", e.sizeBytes); put("created", e.createdAt)
    }

    private fun fromJson(o: JSONObject) = DownloadEntry(
        id = o.getString("id"),
        title = o.optString("title"),
        author = o.optString("author"),
        thumbnailUrl = o.optString("thumb").ifBlank { null },
        source = runCatching { DlSource.valueOf(o.getString("source")) }.getOrDefault(DlSource.YOUTUBE),
        format = runCatching { DlFormat.valueOf(o.getString("format")) }.getOrDefault(DlFormat.MP4),
        quality = o.optString("quality"),
        path = o.getString("path"),
        sizeBytes = o.optLong("size"),
        createdAt = o.optLong("created"),
    )

    /** Nombre de archivo seguro y único dentro de [dir]. */
    fun uniqueFile(dir: File, title: String, ext: String): File {
        val base = title.replace(Regex("""[\\/:*?"<>|\n\r\t]"""), " ").trim().take(80).ifBlank { "descarga" }
        var f = File(dir, "$base.$ext")
        var i = 2
        while (f.exists()) f = File(dir, "$base ($i).$ext").also { i++ }
        return f
    }
}
