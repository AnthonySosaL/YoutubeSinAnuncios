package com.vancedclean.app.download

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** Una lista de reproducción creada por el usuario (canciones por su content://). */
data class MusicList(val id: String, val name: String, val songs: List<String>)

/**
 * Favoritas y listas propias. Se guardan en la app (rápido) y además como archivos .m3u en
 * Música/Zaza/Listas, que Android no borra al desinstalar: tras reinstalar se importan solas.
 */
object MusicLists {

    private const val FILE = "listas_musica.json"
    const val FAVORITES_NAME = "Favoritas"
    private const val LISTS_DIR = "Zaza/Listas/"
    private val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _favorites = MutableStateFlow<Set<String>>(emptySet())
    val favorites: StateFlow<Set<String>> = _favorites.asStateFlow()

    private val _lists = MutableStateFlow<List<MusicList>>(emptyList())
    val lists: StateFlow<List<MusicList>> = _lists.asStateFlow()

    private var loaded = false

    @Synchronized
    fun load(context: Context) {
        if (loaded) return
        loaded = true
        val f = File(context.filesDir, FILE)
        if (f.exists()) {
            runCatching {
                val o = JSONObject(f.readText())
                _favorites.value = o.optJSONArray("fav")?.strings()?.toSet().orEmpty()
                _lists.value = o.optJSONArray("lists")?.let { arr ->
                    (0 until arr.length()).map { i ->
                        val l = arr.getJSONObject(i)
                        MusicList(l.getString("id"), l.getString("name"), l.optJSONArray("songs")?.strings().orEmpty())
                    }
                }.orEmpty()
            }
        } else {
            importFromM3u(context)
        }
    }

    /** Vuelve a intentar importar las listas (tras dar permiso de lectura después de reinstalar). */
    @Synchronized
    fun reimportIfEmpty(context: Context) {
        load(context)
        if (_favorites.value.isEmpty() && _lists.value.isEmpty()) importFromM3u(context)
    }

    // ---------- Favoritas ----------

    fun isFavorite(uri: String) = uri in _favorites.value

    fun toggleFavorite(context: Context, uri: String) {
        if (uri.isBlank()) return
        _favorites.value = if (uri in _favorites.value) _favorites.value - uri else _favorites.value + uri
        persist(context, FAVORITES_NAME, _favorites.value.toList())
    }

    // ---------- Listas ----------

    fun create(context: Context, name: String, firstSong: String? = null): MusicList {
        val list = MusicList(UUID.randomUUID().toString(), uniqueName(name.trim().ifBlank { "Mi lista" }), listOfNotNull(firstSong))
        _lists.value = _lists.value + list
        persist(context, list.name, list.songs)
        return list
    }

    fun rename(context: Context, id: String, newName: String) {
        val old = _lists.value.firstOrNull { it.id == id } ?: return
        val name = uniqueName(newName.trim().ifBlank { old.name }, exceptId = id)
        _lists.value = _lists.value.map { if (it.id == id) it.copy(name = name) else it }
        save(context)
        io.launch {
            deleteM3u(context, old.name)
            writeM3u(context, name, old.songs)
        }
    }

    fun delete(context: Context, id: String) {
        val old = _lists.value.firstOrNull { it.id == id } ?: return
        _lists.value = _lists.value.filterNot { it.id == id }
        save(context)
        io.launch { deleteM3u(context, old.name) }
    }

    fun add(context: Context, id: String, uri: String) = change(context, id) { if (uri in it) it else it + uri }

    fun remove(context: Context, id: String, uri: String) = change(context, id) { it - uri }

    private fun change(context: Context, id: String, block: (List<String>) -> List<String>) {
        var changed: MusicList? = null
        _lists.value = _lists.value.map { if (it.id == id) it.copy(songs = block(it.songs)).also { n -> changed = n } else it }
        changed?.let { persist(context, it.name, it.songs) }
    }

    /** Quita una canción borrada de favoritas y de todas las listas. */
    fun forgetSong(context: Context, uri: String) {
        if (uri in _favorites.value) toggleFavorite(context, uri)
        _lists.value.filter { uri in it.songs }.forEach { remove(context, it.id, uri) }
    }

    private fun uniqueName(base: String, exceptId: String? = null): String {
        val taken = _lists.value.filter { it.id != exceptId }.map { it.name.lowercase() }.toSet() + FAVORITES_NAME.lowercase()
        if (base.lowercase() !in taken) return base
        var i = 2
        while ("$base $i".lowercase() in taken) i++
        return "$base $i"
    }

    // ---------- Guardado ----------

    private fun persist(context: Context, listName: String, songs: List<String>) {
        save(context)
        io.launch { writeM3u(context, listName, songs) }
    }

    @Synchronized
    private fun save(context: Context) {
        val o = JSONObject()
        o.put("fav", JSONArray(_favorites.value.toList()))
        o.put("lists", JSONArray().apply {
            _lists.value.forEach { l -> put(JSONObject().put("id", l.id).put("name", l.name).put("songs", JSONArray(l.songs))) }
        })
        File(context.applicationContext.filesDir, FILE).writeText(o.toString())
    }

    // ---------- Respaldo .m3u en Música/Zaza/Listas ----------

    private fun filesUri(): Uri =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        else MediaStore.Files.getContentUri("external")

    private fun m3uDir() = Environment.DIRECTORY_MUSIC + "/" + LISTS_DIR

    /** Nombre de archivo de cada canción (lo que se escribe en el .m3u). */
    private fun displayName(context: Context, uri: String): String? = runCatching {
        context.contentResolver.query(Uri.parse(uri), arrayOf(MediaStore.MediaColumns.DISPLAY_NAME), null, null, null)
            ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
    }.getOrNull()

    @Synchronized
    private fun writeM3u(context: Context, listName: String, songs: List<String>) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        runCatching {
            val body = buildString {
                append("#EXTM3U\n")
                songs.forEach { u -> displayName(context, u)?.let { append("../").append(it).append('\n') } }
            }
            val resolver = context.contentResolver
            val existing = findM3u(context, listName)
            val target = existing.firstNotNullOfOrNull { u ->
                runCatching { resolver.openOutputStream(u, "wt")!!.use { it.write(body.toByteArray()) }; u }.getOrNull()
            }
            if (target == null) {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, "$listName.m3u")
                    put(MediaStore.MediaColumns.MIME_TYPE, "audio/x-mpegurl")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, m3uDir())
                }
                val uri = resolver.insert(filesUri(), values) ?: return
                resolver.openOutputStream(uri)!!.use { it.write(body.toByteArray()) }
            }
        }.onFailure { android.util.Log.w("ZazaListas", "No se pudo guardar $listName.m3u: ${it.message}") }
    }

    private fun deleteM3u(context: Context, listName: String) {
        findM3u(context, listName).forEach { u -> runCatching { context.contentResolver.delete(u, null, null) } }
    }

    /** Archivos .m3u de esa lista (también "Lista (2).m3u" que crea Android si el anterior no es nuestro). */
    private fun findM3u(context: Context, listName: String): List<Uri> = allM3u(context)
        .filter { baseName(it.second).equals(listName, ignoreCase = true) }
        .sortedByDescending { it.third }
        .map { it.first }

    private fun allM3u(context: Context): List<Triple<Uri, String, Long>> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return emptyList()
        val out = mutableListOf<Triple<Uri, String, Long>>()
        runCatching {
            context.contentResolver.query(
                filesUri(),
                arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME, MediaStore.MediaColumns.DATE_MODIFIED),
                "${MediaStore.MediaColumns.RELATIVE_PATH} = ? AND ${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ?",
                arrayOf(m3uDir(), "%.m3u"), null
            )?.use { c ->
                while (c.moveToNext()) out += Triple(ContentUris.withAppendedId(filesUri(), c.getLong(0)), c.getString(1), c.getLong(2))
            }
        }
        return out
    }

    private fun baseName(file: String) = file.removeSuffix(".m3u").replace(Regex("""\s\(\d+\)$"""), "")

    /** Reconstruye favoritas y listas leyendo los .m3u (tras reinstalar la app). */
    private fun importFromM3u(context: Context) {
        val files = allM3u(context).groupBy { baseName(it.second) }.mapValues { (_, v) -> v.maxBy { it.third }.first }
        if (files.isEmpty()) return
        // Nombre de archivo → content:// de las canciones descargadas
        val byName = DownloadStore.entries.value.filter { it.uri.isNotBlank() }
            .mapNotNull { e -> displayName(context, e.uri)?.let { it to e.uri } }.toMap()
        files.forEach { (name, uri) ->
            val songs = runCatching {
                context.contentResolver.openInputStream(uri)!!.bufferedReader().readLines()
            }.getOrDefault(emptyList())
                .map { it.trim() }
                .filter { it.isNotEmpty() && !it.startsWith("#") }
                .mapNotNull { byName[it.substringAfterLast('/')] }
            if (name.equals(FAVORITES_NAME, ignoreCase = true)) _favorites.value = songs.toSet()
            else _lists.value = _lists.value + MusicList(UUID.randomUUID().toString(), name, songs)
        }
        save(context)
    }

    private fun JSONArray.strings() = (0 until length()).map { getString(it) }
}
