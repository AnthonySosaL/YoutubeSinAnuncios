package com.vancedclean.app.download

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

enum class TaskState { EN_COLA, DESCARGANDO, CONVIRTIENDO, LISTO, ERROR }

/** Una descarga en curso (o recién terminada) con su progreso. */
data class DownloadTask(
    val id: String,
    val title: String,
    val format: DlFormat,
    val quality: String,
    val state: TaskState = TaskState.EN_COLA,
    val progress: Float = 0f,
    val message: String = "",
)

/** Cola de descargas: una a la vez, con un servicio en primer plano para que no se corte. */
@OptIn(ExperimentalCoroutinesApi::class)
object DownloadQueue {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))
    private val jobs = mutableMapOf<String, Job>()

    private val _tasks = MutableStateFlow<List<DownloadTask>>(emptyList())
    val tasks: StateFlow<List<DownloadTask>> = _tasks.asStateFlow()

    val active get() = _tasks.value.count { it.state == TaskState.EN_COLA || it.state == TaskState.DESCARGANDO || it.state == TaskState.CONVIRTIENDO }

    fun enqueueVideo(context: Context, options: DownloadOptions, choice: VideoChoice) =
        enqueue(context, DownloadTask(newId(), options.title, DlFormat.MP4, choice.label)) { task ->
            downloadVideo(context.applicationContext, options, choice, task)
        }

    fun enqueueAudio(context: Context, options: DownloadOptions, choice: AudioChoice) =
        enqueue(context, DownloadTask(newId(), options.title, DlFormat.MP3, "MP3 ${choice.mp3Kbps} kbps")) { task ->
            downloadAudio(context.applicationContext, options, choice, task)
        }

    fun cancel(id: String) {
        jobs.remove(id)?.cancel()
        update(id) { it.copy(state = TaskState.ERROR, message = "Cancelada") }
    }

    fun clearFinished() {
        _tasks.value = _tasks.value.filter { it.state != TaskState.LISTO && it.state != TaskState.ERROR }
    }

    private fun enqueue(context: Context, task: DownloadTask, work: suspend (DownloadTask) -> DownloadEntry) {
        val app = context.applicationContext
        DownloadStore.load(app)
        _tasks.value = _tasks.value + task
        startService(app)
        jobs[task.id] = scope.launch {
            try {
                val entry = work(task)
                DownloadStore.add(app, entry)
                update(task.id) { it.copy(state = TaskState.LISTO, progress = 1f, message = "Guardado") }
            } catch (e: CancellationException) {
                update(task.id) { it.copy(state = TaskState.ERROR, message = "Cancelada") }
            } catch (e: Throwable) {
                update(task.id) { it.copy(state = TaskState.ERROR, message = e.message ?: "Error en la descarga") }
            } finally {
                jobs.remove(task.id)
                DownloadService.refresh(app)
            }
        }
    }

    private suspend fun downloadVideo(app: Context, o: DownloadOptions, c: VideoChoice, t: DownloadTask): DownloadEntry {
        val target = File(DownloadStore.workDir(app), "${t.id}.mp4")
        val headers = if (o.source == DlSource.INSTAGRAM) InstagramResolver.mediaHeaders else emptyMap()
        if (c.audioUrl == null) {
            fetch(c.videoUrl, target, t.id, 0f, 1f, headers)
        } else {
            val tmpV = File(app.cacheDir, "${t.id}.video")
            val tmpA = File(app.cacheDir, "${t.id}.audio")
            try {
                fetch(c.videoUrl, tmpV, t.id, 0f, 0.85f, headers)
                fetch(c.audioUrl, tmpA, t.id, 0.85f, 0.97f, headers)
                update(t.id) { it.copy(state = TaskState.CONVIRTIENDO, message = "Uniendo video y audio…") }
                MediaTools.muxMp4(tmpV, tmpA, target)
            } catch (e: Throwable) {
                target.delete(); throw e
            } finally {
                tmpV.delete(); tmpA.delete()
            }
        }
        return publish(app, o, DlFormat.MP4, c.label, target)
    }

    private suspend fun downloadAudio(app: Context, o: DownloadOptions, c: AudioChoice, t: DownloadTask): DownloadEntry {
        val target = File(DownloadStore.workDir(app), "${t.id}.mp3")
        val tmp = File(app.cacheDir, "${t.id}.src")
        val headers = if (o.source == DlSource.INSTAGRAM) InstagramResolver.mediaHeaders else emptyMap()
        try {
            fetch(c.url, tmp, t.id, 0f, 0.5f, headers)
            update(t.id) { it.copy(state = TaskState.CONVIRTIENDO, message = "Convirtiendo a MP3…") }
            val cover = o.thumbnailUrl?.let { url ->
                val jpg = url.replace("/vi_webp/", "/vi/").replace(".webp", ".jpg")
                runCatching { HttpDownloader.bytes(jpg) }.getOrNull()
            }
            MediaTools.toMp3(tmp, target, c.mp3Kbps, o.title, o.author, albumOf(o), cover) { p ->
                update(t.id) { it.copy(progress = 0.5f + p * 0.5f) }
            }
        } catch (e: Throwable) {
            target.delete(); throw e
        } finally {
            tmp.delete()
        }
        return publish(app, o, DlFormat.MP3, "MP3 ${c.mp3Kbps} kbps", target)
    }

    private fun albumOf(o: DownloadOptions) =
        if (o.source == DlSource.INSTAGRAM) MediaLibrary.ALBUM_INSTAGRAM else MediaLibrary.ALBUM_YOUTUBE

    /** Mueve el archivo terminado a Music/Zaza o Movies/Zaza (si falla, queda en la carpeta privada). */
    private fun publish(app: Context, o: DownloadOptions, f: DlFormat, quality: String, temp: File): DownloadEntry {
        val size = temp.length()
        val uri = runCatching { MediaLibrary.publish(app, temp, f, o.title, o.author, albumOf(o)) }.getOrNull()
        val path = if (uri != null) {
            temp.delete(); ""
        } else {
            val keep = DownloadStore.uniqueFile(File(app.getExternalFilesDir(null) ?: app.filesDir, "descargas"), o.title, f.ext)
            keep.parentFile?.mkdirs()
            temp.renameTo(keep)
            keep.absolutePath
        }
        return entry(o, f, quality, path, uri.orEmpty(), size)
    }

    private suspend fun fetch(url: String, file: File, id: String, from: Float, to: Float, headers: Map<String, String>) {
        update(id) { it.copy(state = TaskState.DESCARGANDO, message = "Descargando…") }
        HttpDownloader.download(url, file, headers) { done, total ->
            if (total > 0) update(id) { it.copy(progress = from + (to - from) * done / total) }
        }
    }

    private fun entry(o: DownloadOptions, f: DlFormat, quality: String, path: String, uri: String, size: Long) = DownloadEntry(
        id = newId(), title = o.title, author = o.author, thumbnailUrl = o.thumbnailUrl, source = o.source,
        format = f, quality = quality, path = path, sizeBytes = size,
        createdAt = System.currentTimeMillis(), uri = uri,
    )

    private fun update(id: String, change: (DownloadTask) -> DownloadTask) {
        _tasks.value = _tasks.value.map { if (it.id == id) change(it) else it }
    }

    private fun newId() = UUID.randomUUID().toString()

    private fun startService(app: Context) {
        runCatching { ContextCompat.startForegroundService(app, Intent(app, DownloadService::class.java)) }
    }
}
