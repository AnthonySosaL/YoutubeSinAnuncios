package com.vancedclean.app.player

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Estado global del panel del reproductor (estilo Vanced/YouTube): el video vive
 * en una capa encima de la navegación y se expande o se minimiza a la barra
 * inferior sin destruir el reproductor.
 */
object PlayerSheet {

    enum class Target { EXPANDED, MINIMIZED }

    private val _videoUrl = MutableStateFlow<String?>(null)
    /** URL del video abierto, o null si el panel está cerrado. */
    val videoUrl: StateFlow<String?> = _videoUrl.asStateFlow()

    private val _target = MutableStateFlow(Target.EXPANDED)
    /** Estado al que el panel debe animarse. */
    val target: StateFlow<Target> = _target.asStateFlow()

    private val _requestId = MutableStateFlow(0)
    /** Cambia en cada petición, para repetir la animación aunque el destino sea igual. */
    val requestId: StateFlow<Int> = _requestId.asStateFlow()

    /** Altura medida de la barra de navegación inferior (px), 0 si aún no se midió. */
    val navBarHeightPx = MutableStateFlow(0)

    private val _inPip = MutableStateFlow(false)
    /** true mientras la app está en modo ventanita (picture-in-picture): solo se dibuja el video. */
    val inPip: StateFlow<Boolean> = _inPip.asStateFlow()

    fun setInPip(value: Boolean) {
        _inPip.value = value
    }

    private val _isMinimized = MutableStateFlow(false)
    /** true cuando el panel está totalmente minimizado (lo usan las pantallas para dejar espacio). */
    val isMinimized: StateFlow<Boolean> = _isMinimized.asStateFlow()

    fun open(url: String) {
        _videoUrl.value = url
        expand()
    }

    /** Muestra otro video sin abrir ni cerrar el panel (paso automático al siguiente). */
    fun switchTo(url: String) {
        _videoUrl.value = url
    }

    fun expand() = request(Target.EXPANDED)

    fun minimize() = request(Target.MINIMIZED)

    fun close() {
        _videoUrl.value = null
        _isMinimized.value = false
        VideoPlayerManager.closePlayer()
    }

    internal fun onSettled(minimized: Boolean) {
        _isMinimized.value = minimized
        _target.value = if (minimized) Target.MINIMIZED else Target.EXPANDED
    }

    /** Cuando se cierra desde la notificación ya no queda nada que mostrar. */
    internal fun onPlaybackClosed() {
        _videoUrl.value = null
        _isMinimized.value = false
    }

    private fun request(target: Target) {
        _target.value = target
        _requestId.value++
    }
}
