package com.vancedclean.app.download

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

enum class DlFormat(val ext: String, val label: String) { MP4("mp4", "Video MP4"), MP3("mp3", "Audio MP3") }

enum class DlSource(val label: String) { YOUTUBE("YouTube"), INSTAGRAM("Instagram") }

/** Un archivo descargado por la app. */
data class DownloadEntry(
    val id: String,
    val title: String,
    val author: String,
    val thumbnailUrl: String?,
    val source: DlSource,
    val format: DlFormat,
    val quality: String,
    /** Ruta de las descargas antiguas en la carpeta privada (vacía si ya está en la pública). */
    val path: String,
    val sizeBytes: Long,
    val createdAt: Long,
    /** content:// en Music/Zaza o Movies/Zaza (sobrevive a desinstalar la app). */
    val uri: String = "",
) {
    val playUri: Uri get() = if (uri.isNotBlank()) Uri.parse(uri) else Uri.fromFile(File(path))

    /** Solo los MP3 de YouTube van al reproductor de música. */
    val isMusic get() = format == DlFormat.MP3 && source == DlSource.YOUTUBE
}

/**
 * Índice de descargas. Los archivos viven en Music/Zaza y Movies/Zaza (públicas, no se borran al
 * desinstalar) y este índice guarda lo que el archivo no tiene (miniatura, calidad, origen).
 * Al abrir la app se completa con lo que haya en esas carpetas, así tras reinstalar vuelve todo.
 */
object DownloadStore {

    private const val INDEX = "descargas.json"
    private val _entries = MutableStateFlow<List<DownloadEntry>>(emptyList())
    val entries: StateFlow<List<DownloadEntry>> = _entries.asStateFlow()
    private var loaded = false

    /** Carpeta temporal privada para descargar y convertir antes de publicar. */
    fun workDir(context: Context): File = File(context.cacheDir, "descargas").apply { mkdirs() }

    @Synchronized
    fun load(context: Context) {
        if (loaded) return
        loaded = true
        val f = File(context.filesDir, INDEX)
        val saved = if (!f.exists()) emptyList() else runCatching {
            val arr = JSONArray(f.readText())
            (0 until arr.length()).map { fromJson(arr.getJSONObject(it)) }
        }.getOrDefault(emptyList())
        _entries.value = saved
        migrateAndRescan(context)
    }

    /** Vuelve a leer las carpetas públicas (por ejemplo tras dar permiso de lectura). */
    @Synchronized
    fun refresh(context: Context) {
        load(context)
        migrateAndRescan(context)
    }

    private fun migrateAndRescan(context: Context) {
        // 1) Descargas viejas en la carpeta privada → carpeta pública.
        val migrated = _entries.value.map { e ->
            if (e.uri.isBlank() && e.path.isNotBlank() && File(e.path).exists()) {
                runCatching {
                    val album = if (e.source == DlSource.INSTAGRAM) MediaLibrary.ALBUM_INSTAGRAM else MediaLibrary.ALBUM_YOUTUBE
                    val uri = MediaLibrary.publish(context, File(e.path), e.format, e.title, e.author, album)
                    File(e.path).delete()
                    e.copy(uri = uri, path = "")
                }.getOrDefault(e)
            } else e
        }
        // 2) Quitar lo que ya no existe.
        val alive = migrated.filter { e ->
            if (e.uri.isNotBlank()) MediaLibrary.exists(context, e.uri) else File(e.path).exists()
        }
        // 3) Añadir lo que esté en Music/Zaza y Movies/Zaza pero no en el índice (reinstalación).
        val known = alive.map { it.uri }.toSet()
        val found = MediaLibrary.rescan(context).filter { it.uri !in known }
        _entries.value = (alive + found).sortedByDescending { it.createdAt }
        save(context)
    }

    @Synchronized
    fun add(context: Context, entry: DownloadEntry) {
        load(context)
        _entries.value = listOf(entry) + _entries.value.filterNot { it.id == entry.id }
        save(context)
    }

    /** Quita del índice (el archivo ya se borró o el usuario lo confirmó). */
    @Synchronized
    fun forget(context: Context, id: String) {
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
        put("uri", e.uri)
    }

    private fun fromJson(o: JSONObject) = DownloadEntry(
        id = o.getString("id"),
        title = o.optString("title"),
        author = o.optString("author"),
        thumbnailUrl = o.optString("thumb").ifBlank { null },
        source = runCatching { DlSource.valueOf(o.getString("source")) }.getOrDefault(DlSource.YOUTUBE),
        format = runCatching { DlFormat.valueOf(o.getString("format")) }.getOrDefault(DlFormat.MP4),
        quality = o.optString("quality"),
        path = o.optString("path"),
        sizeBytes = o.optLong("size"),
        createdAt = o.optLong("created"),
        uri = o.optString("uri"),
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
