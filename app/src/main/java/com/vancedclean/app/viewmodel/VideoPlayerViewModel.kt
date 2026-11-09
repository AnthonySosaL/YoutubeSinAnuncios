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

/** StreamInfo recientes (el siguiente video ya precargado se abre al instante y sin red). */
private object StreamCache {
    private const val MAX_AGE_MS = 4 * 60 * 60 * 1000L // los enlaces de YouTube duran ~6 h
    private val map = object : LinkedHashMap<String, Pair<Long, StreamInfo>>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Pair<Long, StreamInfo>>?) = size > 8
    }

    @Synchronized
    fun get(url: String): StreamInfo? =
        map[url]?.takeIf { System.currentTimeMillis() - it.first < MAX_AGE_MS }?.second

    @Synchronized
    fun put(url: String, info: StreamInfo) {
        map[url] = System.currentTimeMillis() to info
    }
}

private fun Throwable.isNetwork(): Boolean = generateSequence(this) { it.cause }.any {
    it is java.net.UnknownHostException || it is java.net.SocketTimeoutException ||
        it is java.net.ConnectException || it is java.net.NoRouteToHostException ||
        it is javax.net.ssl.SSLException
}

/** Mensaje claro en español en vez del texto técnico. */
private fun friendlyError(e: Throwable): String = when {
    generateSequence(e) { it.cause }.any { it is java.net.SocketTimeoutException } ->
        "La conexión está muy lenta. Toca Reintentar."
    e.isNetwork() -> "Sin conexión a internet. Revisa el Wi‑Fi o los datos y toca Reintentar."
    else -> e.message ?: "Error cargando el video"
}

class VideoPlayerViewModel(application: Application) : AndroidViewModel(application) {

    private var lastErrorWasNetwork = false

    /** Cuando vuelve la red tras un error de conexión, reintentar solo. */
    private val networkCallback = object : android.net.ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: android.net.Network) {
            if (lastErrorWasNetwork && _uiState.value is PlayerUiState.Error) {
                viewModelScope.launch { retry() }
            }
        }
    }

    init {
        runCatching {
            (application.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager)
                .registerDefaultNetworkCallback(networkCallback)
        }
    }

    override fun onCleared() {
        runCatching {
            (app.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager)
                .unregisterNetworkCallback(networkCallback)
        }
        super.onCleared()
    }

    private var prepareJob: Job? = null

    /**
     * Precarga el primer video relacionado (si la reproducción automática está activada) y lo
     * entrega a [onReady] para dejarlo en la cola del reproductor.
     */
    fun prepareNext(current: VideoDetail, onReady: (PlaybackSource, com.vancedclean.app.player.NowPlaying) -> Unit) {
        prepareJob?.cancel()
        val next = current.relatedVideos.firstOrNull() ?: return
        if (!com.vancedclean.app.data.CurrentSettings.value.autoplay) return
        prepareJob = viewModelScope.launch(Dispatchers.IO) {
            kotlinx.coroutines.delay(8_000) // dejar que arranque primero el video actual
            val info = StreamCache.get(next.url) ?: run {
                var result: StreamInfo? = null
                for (attempt in 0 until 3) {
                    result = runCatching { StreamInfo.getInfo(NewPipe.getService(0), next.url) }.getOrNull()
                    if (result != null) break
                    kotlinx.coroutines.delay(5_000L * (attempt + 1))
                }
                result?.also { StreamCache.put(next.url, it) }
            } ?: return@launch
            val source = PlaybackSource.from(info)
            if (source.isEmpty) return@launch
            val meta = com.vancedclean.app.player.NowPlaying(
                url = next.url,
                title = info.name.orEmpty(),
                channelName = info.uploaderName.orEmpty(),
                thumbnailUrl = info.thumbnails.maxByOrNull { it.height }?.url ?: next.thumbnailUrl,
            )
            kotlinx.coroutines.withContext(Dispatchers.Main) { onReady(source, meta) }
        }
    }

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

                // Precargado (siguiente video) o pedido con reintentos si la red falla un momento.
                var streamInfo: StreamInfo? = StreamCache.get(videoUrl)
                var attempt = 0
                while (streamInfo == null) {
                    try {
                        streamInfo = StreamInfo.getInfo(NewPipe.getService(0), videoUrl)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        if (!e.isNetwork() || attempt >= 3) throw e
                        attempt++
                        kotlinx.coroutines.delay(3_000L * attempt)
                    }
                }
                StreamCache.put(videoUrl, streamInfo)
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
                lastErrorWasNetwork = e.isNetwork()
                _uiState.value = PlayerUiState.Error(friendlyError(e))
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
