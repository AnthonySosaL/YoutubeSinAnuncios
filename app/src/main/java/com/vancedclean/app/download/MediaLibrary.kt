package com.vancedclean.app.download

import android.app.RecoverableSecurityException
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.IntentSender
import android.media.MediaMetadataRetriever
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File

/**
 * Carpetas públicas de Zaza: Music/Zaza (MP3) y Movies/Zaza (MP4). Android no las borra al
 * desinstalar la app, así que al reinstalar se vuelven a encontrar con [rescan].
 * Solo se leen esas dos carpetas: los MP3 que ya tenías en el teléfono no aparecen.
 */
object MediaLibrary {

    const val FOLDER = "Zaza"
    const val ALBUM_YOUTUBE = "Zaza · YouTube"
    const val ALBUM_INSTAGRAM = "Zaza · Instagram"

    private fun relativeDir(format: DlFormat) =
        (if (format == DlFormat.MP3) Environment.DIRECTORY_MUSIC else Environment.DIRECTORY_MOVIES) + "/$FOLDER/"

    private fun collection(format: DlFormat): Uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        if (format == DlFormat.MP3) MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        else MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
    } else {
        if (format == DlFormat.MP3) MediaStore.Audio.Media.EXTERNAL_CONTENT_URI else MediaStore.Video.Media.EXTERNAL_CONTENT_URI
    }

    /** Copia [temp] a la carpeta pública y devuelve su content:// (o file:// en Android 9 o menos). */
    fun publish(context: Context, temp: File, format: DlFormat, title: String, artist: String, album: String): String {
        val name = safeName(title) + "." + format.ext
        val mime = if (format == DlFormat.MP3) "audio/mpeg" else "video/mp4"
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, mime)
                put(MediaStore.MediaColumns.RELATIVE_PATH, relativeDir(format))
                put(MediaStore.MediaColumns.TITLE, title)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
                if (format == DlFormat.MP3) {
                    put(MediaStore.Audio.Media.ARTIST, artist)
                    put(MediaStore.Audio.Media.ALBUM, album)
                }
            }
            val uri = resolver.insert(collection(format), values) ?: error("No se pudo guardar en $FOLDER")
            try {
                resolver.openOutputStream(uri)!!.use { out -> temp.inputStream().use { it.copyTo(out) } }
                resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
            } catch (e: Throwable) {
                resolver.delete(uri, null, null)
                throw e
            }
            uri.toString()
        } else {
            @Suppress("DEPRECATION")
            val dir = File(Environment.getExternalStoragePublicDirectory(
                if (format == DlFormat.MP3) Environment.DIRECTORY_MUSIC else Environment.DIRECTORY_MOVIES), FOLDER)
            dir.mkdirs()
            val target = DownloadStore.uniqueFile(dir, title, format.ext)
            temp.copyTo(target)
            MediaScannerConnection.scanFile(context, arrayOf(target.absolutePath), arrayOf(mime), null)
            Uri.fromFile(target).toString()
        }
    }

    /** Busca en Music/Zaza y Movies/Zaza todo lo descargado (también tras reinstalar la app). */
    fun rescan(context: Context): List<DownloadEntry> = DlFormat.entries.flatMap { scan(context, it) }

    private fun scan(context: Context, format: DlFormat): List<DownloadEntry> {
        val projection = mutableListOf(
            MediaStore.MediaColumns._ID, MediaStore.MediaColumns.TITLE, MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.DATE_ADDED, MediaStore.MediaColumns.DISPLAY_NAME,
        )
        if (format == DlFormat.MP3) projection += listOf(MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.ALBUM)
        val (selection, args) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?" to arrayOf(relativeDir(format) + "%")
        } else {
            @Suppress("DEPRECATION")
            "${MediaStore.MediaColumns.DATA} LIKE ?" to arrayOf("%/" + relativeDir(format) + "%")
        }
        val out = mutableListOf<DownloadEntry>()
        runCatching {
            context.contentResolver.query(collection(format), projection.toTypedArray(), selection, args, null)?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getLong(0)
                    val uri = ContentUris.withAppendedId(collection(format), id)
                    val album = if (format == DlFormat.MP3) c.getString(6).orEmpty() else ""
                    val artist = if (format == DlFormat.MP3) c.getString(5).orEmpty().takeIf { it != "<unknown>" } ?: "" else ""
                    out += DownloadEntry(
                        id = "ms-${format.name}-$id",
                        title = c.getString(1)?.takeIf { it.isNotBlank() } ?: c.getString(4).substringBeforeLast('.'),
                        author = artist,
                        thumbnailUrl = null,
                        source = if (album == ALBUM_INSTAGRAM) DlSource.INSTAGRAM else DlSource.YOUTUBE,
                        format = format,
                        quality = "",
                        path = "",
                        sizeBytes = c.getLong(2),
                        createdAt = c.getLong(3) * 1000,
                        uri = uri.toString(),
                    )
                }
            }
        }
        return out
    }

    fun exists(context: Context, uri: String): Boolean = runCatching {
        context.contentResolver.openFileDescriptor(Uri.parse(uri), "r")?.use { true } ?: false
    }.getOrDefault(false)

    /**
     * Borra el archivo. Si es de una instalación anterior, Android pide permiso al usuario:
     * en ese caso devuelve el IntentSender para mostrar ese aviso.
     */
    fun delete(context: Context, uri: String): IntentSender? {
        val u = Uri.parse(uri)
        if (u.scheme == "file") { File(u.path!!).delete(); return null }
        return try {
            context.contentResolver.delete(u, null, null)
            null
        } catch (e: SecurityException) {
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ->
                    MediaStore.createDeleteRequest(context.contentResolver, listOf(u)).intentSender
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && e is RecoverableSecurityException ->
                    e.userAction.actionIntent.intentSender
                else -> throw e
            }
        }
    }

    /** Portada incrustada en el MP3 (para cuando no hay miniatura de internet). */
    fun embeddedArt(context: Context, uri: String): ByteArray? = runCatching {
        MediaMetadataRetriever().run {
            setDataSource(context, Uri.parse(uri))
            embeddedPicture.also { release() }
        }
    }.getOrNull()

    fun readPermissions(): Array<String> = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
            arrayOf(android.Manifest.permission.READ_MEDIA_AUDIO, android.Manifest.permission.READ_MEDIA_VIDEO)
        else -> arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    private fun safeName(title: String) =
        title.replace(Regex("""[\\/:*?"<>|\n\r\t]"""), " ").trim().take(80).ifBlank { "descarga" }
}
