package com.vancedclean.app.player

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.source.SingleSampleMediaSource
import com.vancedclean.app.data.CurrentSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class NowPlaying(
    val url: String,
    val title: String,
    val channelName: String,
    val thumbnailUrl: String,
)

object VideoPlayerManager {

    private const val POSITION_UPDATE_INTERVAL_MS = 500L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var player: ExoPlayer? = null
    private var appContext: Context? = null
    private var positionJob: Job? = null

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _nowPlaying = MutableStateFlow<NowPlaying?>(null)
    val nowPlaying: StateFlow<NowPlaying?> = _nowPlaying.asStateFlow()

    private val _source = MutableStateFlow<PlaybackSource?>(null)
    /** Calidades y subtítulos disponibles del video actual. */
    val source: StateFlow<PlaybackSource?> = _source.asStateFlow()

    private val _quality = MutableStateFlow<QualityOption?>(null)
    val quality: StateFlow<QualityOption?> = _quality.asStateFlow()

    private val _subtitle = MutableStateFlow<SubtitleOption?>(null)
    val subtitle: StateFlow<SubtitleOption?> = _subtitle.asStateFlow()

    private val _speed = MutableStateFlow(1f)
    val speed: StateFlow<Float> = _speed.asStateFlow()

    /** Calidades que ya fallaron en este video (para probar otra automáticamente). */
    private val failedQualities = mutableSetOf<String>()
    private var hlsTried = false
    private var fallbackForUrl: String? = null

    private val _endedCount = MutableStateFlow(0)
    /** Aumenta cada vez que un video termina (para la reproducción automática). */
    val endedCount: StateFlow<Int> = _endedCount.asStateFlow()

    private val httpFactory = DefaultHttpDataSource.Factory()
        .setAllowCrossProtocolRedirects(true)
        .setConnectTimeoutMs(20_000)
        .setReadTimeoutMs(30_000)

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _isPlaying.value = isPlaying
            if (isPlaying) {
                startPositionUpdates()
            } else {
                stopPositionUpdates()
                syncState()
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            syncState()
            if (playbackState == Player.STATE_ENDED) _endedCount.value++
        }

        override fun onPlayerError(error: PlaybackException) {
            handlePlayerError(error)
        }

        override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
            // Hay video pero el teléfono no puede decodificarlo (solo sonaría el audio): otra calidad.
            if (tracks.containsType(C.TRACK_TYPE_VIDEO) && !tracks.isTypeSupported(C.TRACK_TYPE_VIDEO)) {
                android.util.Log.w("ZazaPlayer", "Video no soportado por el decodificador")
                tryFallback()
            }
        }
    }

    fun createPlayer(context: Context): ExoPlayer {
        player?.let { return it }

        val applicationContext = context.applicationContext
        appContext = applicationContext

        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .setUsage(C.USAGE_MEDIA)
            .build()

        val newPlayer = ExoPlayer.Builder(applicationContext)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()

        newPlayer.addListener(playerListener)
        player = newPlayer
        return newPlayer
    }

    fun getPlayer(): ExoPlayer? = player

    /** Reproduce un video eligiendo la calidad según Ajustes (o la indicada). */
    fun playSource(
        source: PlaybackSource,
        metadata: NowPlaying,
        quality: QualityOption? = source.pick(CurrentSettings.value.defaultQuality),
        subtitle: SubtitleOption? = null,
        startPositionMs: Long = 0L,
    ) {
        val currentPlayer = player ?: return

        if (fallbackForUrl != metadata.url) {
            fallbackForUrl = metadata.url
            failedQualities.clear()
            hlsTried = false
        }
        if (quality == null) hlsTried = true

        _error.value = null
        _currentPosition.value = startPositionMs
        if (startPositionMs == 0L) _duration.value = 0L
        _nowPlaying.value = metadata
        _source.value = source
        _quality.value = quality
        _subtitle.value = subtitle

        val mediaMetadata = MediaMetadata.Builder()
            .setTitle(metadata.title)
            .setArtist(metadata.channelName)
            .setArtworkUri(metadata.thumbnailUrl.takeIf { it.isNotBlank() }?.let { Uri.parse(it) })
            .build()

        val mediaSource = buildMediaSource(source, quality, subtitle, mediaMetadata) ?: run {
            _error.value = "No se encontró un stream reproducible para este video"
            return
        }

        currentPlayer.trackSelectionParameters = currentPlayer.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, subtitle == null)
            .setPreferredTextLanguage(subtitle?.language)
            .build()
        currentPlayer.setMediaSource(mediaSource, startPositionMs)
        currentPlayer.prepare()
        currentPlayer.setPlaybackSpeed(_speed.value)
        currentPlayer.playWhenReady = true

        appContext?.let { startBackgroundPlayback(it) }
    }

    /** Cambia de calidad sin perder la posición. */
    fun setQuality(option: QualityOption) = reload(quality = option, subtitle = _subtitle.value)

    /** Activa (o quita con null) los subtítulos sin perder la posición. */
    fun setSubtitle(option: SubtitleOption?) = reload(quality = _quality.value, subtitle = option)

    fun setSpeed(value: Float) {
        _speed.value = value
        player?.setPlaybackSpeed(value)
    }

    private fun reload(quality: QualityOption?, subtitle: SubtitleOption?) {
        val src = _source.value ?: return
        val meta = _nowPlaying.value ?: return
        val pos = player?.currentPosition ?: 0L
        val wasPlaying = player?.playWhenReady ?: true
        playSource(src, meta, quality, subtitle, startPositionMs = pos)
        player?.playWhenReady = wasPlaying
    }

    @OptIn(UnstableApi::class)
    private fun buildMediaSource(
        source: PlaybackSource,
        quality: QualityOption?,
        subtitle: SubtitleOption?,
        metadata: MediaMetadata,
    ): MediaSource? {
        val main: MediaSource = when {
            quality != null -> {
                val video = ProgressiveMediaSource.Factory(httpFactory).createMediaSource(
                    MediaItem.Builder().setUri(quality.videoUrl).setMediaMetadata(metadata).build()
                )
                if (quality.audioUrl == null) video
                else MergingMediaSource(
                    true,
                    video,
                    ProgressiveMediaSource.Factory(httpFactory)
                        .createMediaSource(MediaItem.fromUri(quality.audioUrl))
                )
            }
            !source.hlsUrl.isNullOrBlank() -> HlsMediaSource.Factory(httpFactory).createMediaSource(
                MediaItem.Builder().setUri(source.hlsUrl).setMediaMetadata(metadata).build()
            )
            else -> return null
        }
        if (subtitle == null) return main
        val config = MediaItem.SubtitleConfiguration.Builder(Uri.parse(subtitle.url))
            .setMimeType(subtitle.mimeType)
            .setLanguage(subtitle.language)
            .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
            .build()
        val text = SingleSampleMediaSource.Factory(httpFactory).createMediaSource(config, C.TIME_UNSET)
        return MergingMediaSource(true, main, text)
    }

    fun startBackgroundPlayback(context: Context) {
        if (player == null) return
        val applicationContext = context.applicationContext
        try {
            applicationContext.startService(
                Intent(applicationContext, BackgroundPlaybackService::class.java)
            )
        } catch (_: IllegalStateException) {
            _error.value = "No se pudo iniciar la reproducción en segundo plano"
        } catch (_: SecurityException) {
            _error.value = "No se pudo iniciar la reproducción en segundo plano"
        }
    }

    fun stopBackgroundPlayback() {
        val context = appContext ?: return
        try {
            context.stopService(Intent(context, BackgroundPlaybackService::class.java))
        } catch (_: Exception) {}
    }

    fun togglePlayPause() {
        val currentPlayer = player ?: return
        if (currentPlayer.isPlaying) {
            currentPlayer.pause()
        } else if (currentPlayer.playbackState == Player.STATE_IDLE) {
            // Tras un error el reproductor queda parado: hay que volver a prepararlo.
            _error.value = null
            currentPlayer.prepare()
            currentPlayer.play()
        } else {
            if (currentPlayer.playbackState == Player.STATE_ENDED) {
                currentPlayer.seekTo(0L)
            }
            currentPlayer.play()
        }
    }

    fun pause() {
        player?.pause()
    }

    fun resume() {
        val currentPlayer = player ?: return
        if (currentPlayer.playbackState == Player.STATE_IDLE) currentPlayer.prepare()
        if (currentPlayer.playbackState == Player.STATE_ENDED) {
            currentPlayer.seekTo(0L)
        }
        currentPlayer.play()
    }

    fun seekTo(positionMs: Long) {
        val currentPlayer = player ?: return
        currentPlayer.seekTo(positionMs.coerceAtLeast(0L))
        syncState()
    }

    fun clearError() {
        _error.value = null
    }

    fun closePlayer() {
        release()
    }

    fun release() {
        stopBackgroundPlayback()
        stopPositionUpdates()
        player?.let {
            it.removeListener(playerListener)
            it.release()
        }
        player = null
        _isPlaying.value = false
        _currentPosition.value = 0L
        _duration.value = 0L
        _error.value = null
        _nowPlaying.value = null
        _source.value = null
        _quality.value = null
        _subtitle.value = null
    }

    fun handlePlayerError(error: PlaybackException) {
        if (error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
            player?.apply {
                seekToDefaultPosition()
                prepare()
            }
            return
        }

        if (tryFallback()) return

        stopPositionUpdates()
        _isPlaying.value = false
        _error.value = when (error.errorCode) {
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ->
                "Error de red. Comprueba tu conexión a internet"
            PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS ->
                "El servidor rechazó la solicitud del video"
            PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND ->
                "No se encontró el video"
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
            PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED ->
                "El formato del video no es válido"
            PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
            PlaybackException.ERROR_CODE_DECODING_FAILED ->
                "No se pudo decodificar el video"
            else -> error.message ?: "Error de reproducción desconocido"
        } + " (${error.errorCodeName})"
    }

    /**
     * Si falla una calidad (el decodificador del teléfono no la soporta o YouTube rechaza
     * el enlace), prueba otra sin molestar: primero las que traen audio integrado (H.264,
     * compatibles con casi todo), luego calidades más bajas y al final HLS.
     */
    private fun tryFallback(): Boolean {
        val src = _source.value ?: return false
        val meta = _nowPlaying.value ?: return false
        val current = _quality.value
        current?.let { failedQualities += it.videoUrl }
        val remaining = src.qualities.filter { it.videoUrl !in failedQualities }
        val next = remaining.filter { it.audioUrl == null }.maxByOrNull { it.height }
            ?: remaining.filter { current != null && it.height < current.height }.maxByOrNull { it.height }
        val position = player?.currentPosition ?: 0L
        android.util.Log.w("ZazaPlayer", "Falló ${current?.label ?: "HLS"}; probando ${next?.label ?: if (!hlsTried) "HLS" else "nada"}")
        return when {
            next != null -> { playSource(src, meta, next, _subtitle.value, position); true }
            !hlsTried && !src.hlsUrl.isNullOrBlank() -> { playSource(src, meta, null, _subtitle.value, position); true }
            else -> false
        }
    }

    private fun syncState() {
        val currentPlayer = player ?: return
        _currentPosition.value = currentPlayer.currentPosition.coerceAtLeast(0L)
        _duration.value = if (currentPlayer.duration == C.TIME_UNSET) 0L else currentPlayer.duration
    }

    private fun startPositionUpdates() {
        positionJob?.cancel()
        positionJob = scope.launch {
            while (isActive) {
                syncState()
                delay(POSITION_UPDATE_INTERVAL_MS)
            }
        }
    }

    private fun stopPositionUpdates() {
        positionJob?.cancel()
        positionJob = null
    }
}
