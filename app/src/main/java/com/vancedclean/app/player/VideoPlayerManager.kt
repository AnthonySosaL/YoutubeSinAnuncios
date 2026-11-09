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

    private val _blockedByCall = MutableStateFlow(false)
    /** true si el usuario quiere reproducir pero una llamada en curso no deja (Android no da el audio). */
    val blockedByCall: StateFlow<Boolean> = _blockedByCall.asStateFlow()
    private var wantsToPlay = false
    private var handlesAudioFocus = true
    private var callCheckJob: Job? = null

    private val mediaAudioAttributes = AudioAttributes.Builder()
        .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
        .setUsage(C.USAGE_MEDIA)
        .build()

    private val _shuffle = MutableStateFlow(false)
    val shuffle: StateFlow<Boolean> = _shuffle.asStateFlow()

    private val _repeatMode = MutableStateFlow(Player.REPEAT_MODE_OFF)
    /** Player.REPEAT_MODE_OFF / ALL / ONE */
    val repeatMode: StateFlow<Int> = _repeatMode.asStateFlow()

    private val _sleepAt = MutableStateFlow<Long?>(null)
    /** Hora (ms) a la que se apagará la música, o null. -1 = al terminar la canción. */
    val sleepAt: StateFlow<Long?> = _sleepAt.asStateFlow()
    private var sleepJob: Job? = null

    private val _queue = MutableStateFlow<List<com.vancedclean.app.download.DownloadEntry>>(emptyList())
    /** Canciones de la lista que suena ahora. */
    val queue: StateFlow<List<com.vancedclean.app.download.DownloadEntry>> = _queue.asStateFlow()

    private val _loopVideo = MutableStateFlow(false)
    /** Repetir el video de YouTube o descargado en bucle (como en Telegram). */
    val loopVideo: StateFlow<Boolean> = _loopVideo.asStateFlow()

    fun toggleLoopVideo() {
        _loopVideo.value = !_loopVideo.value
        if (_localId.value == null || _queue.value.size <= 1) {
            player?.repeatMode = if (_loopVideo.value) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
        }
    }

    private val _localId = MutableStateFlow<String?>(null)
    /** Id de la descarga que suena ahora (reproducción sin internet), o null. */
    val localId: StateFlow<String?> = _localId.asStateFlow()

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
            if (isPlaying) _blockedByCall.value = false
            if (!isPlaying) _localId.value?.let { id -> rememberLast(id, player?.currentPosition ?: 0L) }
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

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val item = mediaItem ?: return
            if (_localId.value == null) return
            if (_sleepAt.value == -1L && reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                // Temporizador "al terminar la canción".
                player?.pause()
                _sleepAt.value = null
            }
            rememberLast(item.mediaId, 0L)
            _localId.value = item.mediaId
            _nowPlaying.value = NowPlaying(
                url = item.localConfiguration?.uri?.toString().orEmpty(),
                title = item.mediaMetadata.title?.toString().orEmpty(),
                channelName = item.mediaMetadata.artist?.toString().orEmpty(),
                thumbnailUrl = item.mediaMetadata.artworkUri?.toString().orEmpty(),
            )
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

        handlesAudioFocus = true
        val newPlayer = ExoPlayer.Builder(applicationContext)
            .setAudioAttributes(mediaAudioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()

        newPlayer.addListener(playerListener)
        player = newPlayer
        return newPlayer
    }

    fun getPlayer(): ExoPlayer? = player

    /**
     * Reproduce archivos descargados (sin internet). Con varias canciones arma una lista
     * que avanza sola; cierra antes el video de YouTube que estuviera abierto.
     */
    fun playLocal(context: Context, items: List<com.vancedclean.app.download.DownloadEntry>, startIndex: Int = 0, shuffle: Boolean = false) {
        if (items.isEmpty()) return
        if (PlayerSheet.videoUrl.value != null) PlayerSheet.close()
        val p = createPlayer(context)
        _error.value = null
        _source.value = null
        _quality.value = null
        _subtitle.value = null
        _blockedByCall.value = false
        val mediaItems = items.map { e ->
            MediaItem.Builder()
                .setUri(e.playUri)
                .setMediaId(e.id)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(e.title)
                        .setArtist(e.author)
                        .setArtworkUri(e.thumbnailUrl?.let { Uri.parse(it) })
                        .build()
                )
                .build()
        }
        val start = items[startIndex.coerceIn(0, items.lastIndex)]
        _localId.value = start.id
        _nowPlaying.value = NowPlaying(start.playUri.toString(), start.title, start.author, start.thumbnailUrl.orEmpty())
        restoreAudioFocusIfNoCall()
        _queue.value = items
        _shuffle.value = shuffle
        p.shuffleModeEnabled = shuffle
        p.repeatMode = _repeatMode.value
        p.setMediaItems(mediaItems, startIndex.coerceIn(0, items.lastIndex), 0L)
        p.prepare()
        p.setPlaybackSpeed(1f)
        p.playWhenReady = true
        wantsToPlay = true
        checkBlockedByCall()
        appContext?.let { startBackgroundPlayback(it) }
    }

    fun next() { player?.takeIf { it.hasNextMediaItem() }?.seekToNextMediaItem() }

    fun playQueueIndex(index: Int) {
        val p = player ?: return
        if (index in 0 until p.mediaItemCount) {
            p.seekTo(index, 0L)
            p.play()
        }
    }

    fun toggleShuffle() {
        _shuffle.value = !_shuffle.value
        player?.shuffleModeEnabled = _shuffle.value
    }

    /** Sin repetir → repetir todas → repetir una. */
    fun cycleRepeat() {
        _repeatMode.value = when (_repeatMode.value) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        player?.repeatMode = _repeatMode.value
    }

    /** Apaga la música en [minutes] minutos; 0 = cancelar; -1 = al terminar la canción. */
    fun setSleepTimer(minutes: Int) {
        sleepJob?.cancel()
        when {
            minutes == 0 -> _sleepAt.value = null
            minutes < 0 -> _sleepAt.value = -1L
            else -> {
                val at = System.currentTimeMillis() + minutes * 60_000L
                _sleepAt.value = at
                sleepJob = scope.launch {
                    delay(minutes * 60_000L)
                    player?.pause()
                    _sleepAt.value = null
                }
            }
        }
    }

    private const val PREFS = "musica"

    /** Guarda la última canción y posición para "Continuar" al volver a abrir la app. */
    private fun rememberLast(id: String, position: Long) {
        appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)?.edit()
            ?.putString("last_id", id)?.putLong("last_pos", position)?.apply()
    }

    fun lastPlayed(context: Context): Pair<String, Long>? {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val id = p.getString("last_id", null) ?: return null
        return id to p.getLong("last_pos", 0L)
    }

    /** Reanuda una lista en una canción y posición concretas. */
    fun resumeLocal(context: Context, items: List<com.vancedclean.app.download.DownloadEntry>, id: String, position: Long) {
        val index = items.indexOfFirst { it.id == id }.takeIf { it >= 0 } ?: return
        playLocal(context, items, index)
        player?.seekTo(index, position)
    }

    fun previous() { player?.seekToPrevious() }

    /** Reproduce un video eligiendo la calidad según Ajustes (o la indicada). */
    fun playSource(
        source: PlaybackSource,
        metadata: NowPlaying,
        quality: QualityOption? = source.pick(CurrentSettings.value.defaultQuality),
        subtitle: SubtitleOption? = null,
        startPositionMs: Long = 0L,
    ) {
        val currentPlayer = player ?: return

        _localId.value = null
        player?.shuffleModeEnabled = false
        player?.repeatMode = if (_loopVideo.value) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
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
        restoreAudioFocusIfNoCall()
        currentPlayer.playWhenReady = true
        wantsToPlay = true
        checkBlockedByCall()

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
            wantsToPlay = false
            _blockedByCall.value = false
            return
        }
        wantsToPlay = true
        restoreAudioFocusIfNoCall()
        checkBlockedByCall()
        if (currentPlayer.playbackState == Player.STATE_IDLE) {
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
        wantsToPlay = false
        _blockedByCall.value = false
    }

    /** Reproduce aunque haya una llamada: deja de pedir el audio en exclusiva a Android. */
    fun playDuringCall() {
        val currentPlayer = player ?: return
        handlesAudioFocus = false
        currentPlayer.setAudioAttributes(mediaAudioAttributes, false)
        _blockedByCall.value = false
        wantsToPlay = true
        if (currentPlayer.playbackState == Player.STATE_IDLE) currentPlayer.prepare()
        currentPlayer.play()
    }

    private fun isInCall(): Boolean {
        val am = appContext?.getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager ?: return false
        return am.mode == android.media.AudioManager.MODE_IN_CALL ||
            am.mode == android.media.AudioManager.MODE_IN_COMMUNICATION ||
            am.mode == android.media.AudioManager.MODE_RINGTONE
    }

    /** Tras colgar, volvemos a pedir el audio como cualquier reproductor (pausa con llamadas, etc.). */
    private fun restoreAudioFocusIfNoCall() {
        if (!handlesAudioFocus && !isInCall()) {
            handlesAudioFocus = true
            player?.setAudioAttributes(mediaAudioAttributes, true)
        }
    }

    /** Si el usuario quiso reproducir y no arrancó por una llamada, lo avisamos. */
    private fun checkBlockedByCall() {
        callCheckJob?.cancel()
        callCheckJob = scope.launch {
            delay(1200)
            val p = player ?: return@launch
            _blockedByCall.value = wantsToPlay && handlesAudioFocus && !p.isPlaying && isInCall()
        }
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
        _blockedByCall.value = false
        wantsToPlay = false
        _localId.value = null
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
