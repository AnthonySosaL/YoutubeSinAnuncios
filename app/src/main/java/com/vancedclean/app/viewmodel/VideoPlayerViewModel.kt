package com.vancedclean.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vancedclean.app.data.LikeStatus
import com.vancedclean.app.data.NewPipeSetup
import com.vancedclean.app.data.VideoAccountState
import com.vancedclean.app.data.YouTubeAccount
import com.vancedclean.app.player.PlaybackSource
import com.vancedclean.app.ui.components.videoIdOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

data class VideoDetail(
    val url: String,
    val videoId: String?,
    val title: String,
    val channelName: String,
    val channelAvatarUrl: String?,
    val channelUrl: String,
    val channelSubscribers: Long,
    val description: String,
    val viewCount: Long,
    val likeCount: Long,
    val uploadDate: String,
    val thumbnailUrl: String,
    val source: PlaybackSource,
    val relatedVideos: List<VideoItem>,
)

sealed class PlayerUiState {
    data object Loading : PlayerUiState()
    data class Success(val video: VideoDetail) : PlayerUiState()
    data class Error(val message: String) : PlayerUiState()
}

class VideoPlayerViewModel(application: Application) : AndroidViewModel(application) {

    private var videoUrl: String = ""
    private var loadJob: Job? = null

    private val _uiState = MutableStateFlow<PlayerUiState>(PlayerUiState.Loading)
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private val _account = MutableStateFlow(VideoAccountState())
    /** Like y suscripción reales de la cuenta para el video actual. */
    val account: StateFlow<VideoAccountState> = _account.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    /** Mensajes cortos para mostrar como Toast (errores de cuenta, etc.). */
    val messages: SharedFlow<String> = _messages

    /** Comentarios del video actual (se cargan al abrir la sección). */
    val comments = CommentsLoader(viewModelScope)

    private val app get() = getApplication<Application>()

    /** Carga un video; no hace nada si ya es el que está cargado. */
    fun load(url: String) {
        if (url == videoUrl) return
        videoUrl = url
        _uiState.value = PlayerUiState.Loading
        loadVideo()
    }

    fun retry() {
        _uiState.value = PlayerUiState.Loading
        loadVideo()
    }

    private fun loadVideo() {
        val videoUrl = videoUrl
        loadJob?.cancel()
        _account.value = VideoAccountState()
        comments.reset(videoUrl)
        loadJob = viewModelScope.launch(Dispatchers.IO) {
            if (videoUrl.isBlank()) {
                _uiState.value = PlayerUiState.Error("URL de video no válida")
                return@launch
            }
            val videoId = videoIdOf(videoUrl)
            if (videoId != null && YouTubeAccount.isLoggedIn(app)) {
                launch { loadAccountState(videoId) }
                // Igual que la app oficial: el video aparece en tu historial de YouTube.
                launch { runCatching { YouTubeAccount.addToHistory(app, videoId) } }
            }

            try {
                NewPipeSetup.ensure()

                val streamInfo = StreamInfo.getInfo(NewPipe.getService(0), videoUrl)
                val source = PlaybackSource.from(streamInfo)
                if (source.isEmpty) {
                    _uiState.value = PlayerUiState.Error("No se encontró un stream reproducible para este video")
                    return@launch
                }

                val relatedVideos = streamInfo.relatedItems
                    .filterIsInstance<StreamInfoItem>()
                    .map { item ->
                        VideoItem(
                            url = item.url,
                            title = item.name,
                            thumbnailUrl = item.thumbnails.firstOrNull()?.url ?: "",
                            uploaderName = item.uploaderName ?: "",
                            uploaderAvatarUrl = item.uploaderAvatars.firstOrNull()?.url,
                            viewCount = item.viewCount,
                            duration = if (item.streamType.name == "LIVE_STREAM") "EN VIVO" else formatDuration(item.duration),
                        )
                    }

                val uploadDate = streamInfo.uploadDate?.offsetDateTime()?.toString()
                    ?: streamInfo.textualUploadDate.orEmpty()

                _uiState.value = PlayerUiState.Success(
                    VideoDetail(
                        url = videoUrl,
                        videoId = videoId ?: streamInfo.id,
                        title = streamInfo.name.orEmpty(),
                        channelName = streamInfo.uploaderName.orEmpty(),
                        channelAvatarUrl = streamInfo.uploaderAvatars.firstOrNull()?.url,
                        channelUrl = streamInfo.uploaderUrl.orEmpty(),
                        channelSubscribers = streamInfo.uploaderSubscriberCount,
                        description = androidx.core.text.HtmlCompat.fromHtml(
                            streamInfo.description?.content.orEmpty(),
                            androidx.core.text.HtmlCompat.FROM_HTML_MODE_LEGACY
                        ).toString().trim(),
                        viewCount = streamInfo.viewCount,
                        likeCount = streamInfo.likeCount,
                        uploadDate = uploadDate,
                        thumbnailUrl = streamInfo.thumbnails.maxByOrNull { it.height }?.url ?: "",
                        source = source,
                        relatedVideos = relatedVideos,
                    )
                )
                if (_account.value.channelId == null) {
                    channelIdFrom(streamInfo.uploaderUrl)?.let {
                        _account.value = _account.value.copy(channelId = it)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = PlayerUiState.Error(e.message ?: "Error cargando el video")
            }
        }
    }

    private suspend fun loadAccountState(videoId: String) {
        try {
            val state = YouTubeAccount.videoState(app, videoId)
            _account.value = state.copy(channelId = state.channelId ?: _account.value.channelId)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
        }
    }

    // ---------- Me gusta / No me gusta / Suscribirse ----------

    fun toggleLike() =
        setLike(if (_account.value.likeStatus == LikeStatus.LIKE) LikeStatus.NONE else LikeStatus.LIKE)

    fun toggleDislike() =
        setLike(if (_account.value.likeStatus == LikeStatus.DISLIKE) LikeStatus.NONE else LikeStatus.DISLIKE)

    private fun setLike(status: LikeStatus) {
        val videoId = videoIdOf(videoUrl) ?: return
        if (!requireLogin()) return
        val previous = _account.value.likeStatus
        _account.value = _account.value.copy(likeStatus = status)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                YouTubeAccount.setLike(app, videoId, status)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _account.value = _account.value.copy(likeStatus = previous)
                _messages.tryEmit(e.message ?: "No se pudo guardar el me gusta")
            }
        }
    }

    fun toggleSubscribe() {
        if (!requireLogin()) return
        val channelId = _account.value.channelId ?: run {
            _messages.tryEmit("No se encontró el canal")
            return
        }
        val subscribe = !_account.value.subscribed
        _account.value = _account.value.copy(subscribed = subscribe)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                YouTubeAccount.setSubscribed(app, channelId, subscribe)
                _messages.tryEmit(if (subscribe) "Suscripción añadida" else "Suscripción eliminada")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _account.value = _account.value.copy(subscribed = !subscribe)
                _messages.tryEmit(e.message ?: "No se pudo cambiar la suscripción")
            }
        }
    }

    private fun requireLogin(): Boolean {
        if (YouTubeAccount.isLoggedIn(app)) return true
        _messages.tryEmit("Inicia sesión con Google para usar esta función")
        return false
    }

    private fun channelIdFrom(url: String?): String? =
        url?.let { Regex("""/channel/(UC[\w-]+)""").find(it)?.groupValues?.get(1) }

    private fun formatDuration(seconds: Long): String {
        if (seconds <= 0) return ""
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
        else String.format(Locale.US, "%d:%02d", m, s)
    }
}

fun formatViewCount(count: Long): String {
    if (count < 0) return ""
    return when {
        count >= 1_000_000_000 -> "${compactNumber(count, 1_000_000_000)} MM de vistas"
        count >= 1_000_000 -> "${compactNumber(count, 1_000_000)} M de vistas"
        count >= 1_000 -> "${compactNumber(count, 1_000)} K vistas"
        else -> "$count vistas"
    }
}

/** Número compacto sin la palabra "vistas" (para me gusta, comentarios...). */
fun formatCompact(count: Long): String = when {
    count < 0 -> ""
    count >= 1_000_000_000 -> "${compactNumber(count, 1_000_000_000)} MM"
    count >= 1_000_000 -> "${compactNumber(count, 1_000_000)} M"
    count >= 1_000 -> "${compactNumber(count, 1_000)} K"
    else -> count.toString()
}

private fun compactNumber(count: Long, divisor: Long): String {
    val value = count.toDouble() / divisor
    return if (value >= 100) {
        value.toLong().toString()
    } else {
        String.format(Locale.forLanguageTag("es"), "%.1f", value).removeSuffix(",0")
    }
}

fun formatDate(date: String): String {
    if (date.isBlank()) return ""
    return try {
        OffsetDateTime.parse(date).format(
            DateTimeFormatter.ofPattern("d 'de' MMM 'de' yyyy", Locale.forLanguageTag("es"))
        )
    } catch (e: Exception) {
        date
    }
}
