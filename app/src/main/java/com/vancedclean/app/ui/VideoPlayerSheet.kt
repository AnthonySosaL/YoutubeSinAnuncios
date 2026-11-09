package com.vancedclean.app.ui

import android.app.Activity
import android.content.pm.ActivityInfo
import android.view.View
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.ui.PlayerView
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.vancedclean.app.navigation.Screen
import com.vancedclean.app.player.NowPlaying
import com.vancedclean.app.player.PlayerSheet
import com.vancedclean.app.player.VideoPlayerManager
import com.vancedclean.app.ui.components.PlayerGestureOverlay
import com.vancedclean.app.ui.components.sheetDragGestures
import com.vancedclean.app.ui.components.toast
import com.vancedclean.app.data.CurrentSettings
import com.vancedclean.app.viewmodel.PlayerUiState
import com.vancedclean.app.viewmodel.VideoDetail
import com.vancedclean.app.viewmodel.VideoPlayerViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import com.vancedclean.app.R
import kotlin.math.roundToInt

private val MINI_HEIGHT = 64.dp
private val DEFAULT_NAV_HEIGHT = 80.dp
private val ROUTES_WITH_NAV_BAR = setOf(Screen.Home.route, Screen.Subscriptions.route, Screen.Library.route)

/** Fracción final del recorrido en la que el video se encoge hasta la miniatura (como en Vanced). */
private const val SHRINK_START = 0.8f

/**
 * Reproductor como panel por encima de la navegación (igual que la app de referencia):
 * - progress = 0: expandido; progress = 1: minimizado en la barra encima de la navegación.
 * - Al arrastrar, todo el panel baja con el dedo y el inicio se ve detrás; cerca del
 *   final el video se encoge hasta quedar como miniatura reproduciéndose a la izquierda.
 */
@Composable
fun VideoPlayerSheet(navController: NavController) {
    val videoUrl by PlayerSheet.videoUrl.collectAsState()
    val url = videoUrl ?: return

    val viewModel: VideoPlayerViewModel = viewModel()
    LaunchedEffect(url) { viewModel.load(url) }
    val uiState by viewModel.uiState.collectAsState()

    val context = LocalContext.current
    val activity = context as? Activity
    val player = remember { VideoPlayerManager.createPlayer(context) }
    val nowPlaying by VideoPlayerManager.nowPlaying.collectAsState()
    val isPlaying by VideoPlayerManager.isPlaying.collectAsState()
    val playerError by VideoPlayerManager.error.collectAsState()
    val blockedByCall by VideoPlayerManager.blockedByCall.collectAsState()

    // Cerrado desde la notificación: quitamos el panel.
    var hadMedia by remember { mutableStateOf(false) }
    LaunchedEffect(nowPlaying) {
        if (nowPlaying != null) hadMedia = true
        else if (hadMedia) PlayerSheet.onPlaybackClosed()
    }

    val video = (uiState as? PlayerUiState.Success)?.video
    val account by viewModel.account.collectAsState()
    DisposableEffect(video?.url) {
        video?.let { VideoPlayerManager.playSource(it.source, it.toNowPlaying()) }
        onDispose { }
    }

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { toast(context, it) }
    }

    // Reproducción automática: al terminar, pasa al primer video relacionado.
    val endedCount by VideoPlayerManager.endedCount.collectAsState()
    var lastEnded by remember { mutableStateOf(endedCount) }
    LaunchedEffect(endedCount) {
        if (endedCount == lastEnded) return@LaunchedEffect
        lastEnded = endedCount
        val next = video?.relatedVideos?.firstOrNull()
        if (CurrentSettings.value.autoplay && next != null) PlayerSheet.open(next.url)
    }

    var isFullscreen by remember { mutableStateOf(false) }
    fun setFullscreen(enabled: Boolean) {
        isFullscreen = enabled
        activity?.requestedOrientation = if (enabled) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        @Suppress("DEPRECATION")
        activity?.window?.decorView?.systemUiVisibility = if (enabled) {
            View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        } else View.SYSTEM_UI_FLAG_VISIBLE
    }
    DisposableEffect(Unit) { onDispose { if (isFullscreen) setFullscreen(false) } }
    BackHandler(enabled = isFullscreen) { setFullscreen(false) }

    // Ventanita (PiP) o pantalla completa: solo el video, sin el resto de la app.
    val inPip by PlayerSheet.inPip.collectAsState()
    if (isFullscreen || inPip) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            PlayerSurface(player, Modifier.fillMaxSize())
            if (!inPip) {
                PlayerGestureOverlay(
                    modifier = Modifier.fillMaxSize(),
                    isFullscreen = true,
                    onToggleFullscreen = { setFullscreen(false) }
                )
            }
            if (blockedByCall && !inPip) CallBlockedBox()
        }
        return
    }

    // ---- Geometría del panel ----
    val density = LocalDensity.current
    val route = navController.currentBackStackEntryAsState().value?.destination?.route
    val measuredNav by PlayerSheet.navBarHeightPx.collectAsState()
    val statusTop = WindowInsets.statusBars.getTop(density).toFloat()
    val systemBottom = WindowInsets.navigationBars.getBottom(density).toFloat()
    val miniH = with(density) { MINI_HEIGHT.toPx() }
    val navH = if (route in ROUTES_WITH_NAV_BAR) {
        measuredNav.takeIf { it > 0 }?.toFloat()
            ?: (with(density) { DEFAULT_NAV_HEIGHT.toPx() } + systemBottom)
    } else systemBottom

    // El progreso se lee solo en las fases de layout y dibujo: durante la animación no se
    // recompone nada, solo se recoloca y se escala (por eso va fluido).
    var progress by remember { mutableFloatStateOf(1f) }
    var animJob by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()
    val panelColor = MaterialTheme.colorScheme.background
    val miniColor = MaterialTheme.colorScheme.surfaceVariant

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val fullW = constraints.maxWidth.toFloat()
        val fullH = constraints.maxHeight.toFloat()
        val minTop = (fullH - navH - miniH).coerceAtLeast(1f)
        val geo = remember(fullW, fullH, minTop, statusTop, miniH) {
            SheetGeometry(fullW, fullH, minTop, statusTop, miniH)
        }

        fun settle(target: Float, velocityPx: Float = 0f) {
            animJob?.cancel()
            animJob = scope.launch {
                animate(
                    initialValue = progress,
                    targetValue = target,
                    initialVelocity = velocityPx / minTop,
                    animationSpec = spring(dampingRatio = 1f, stiffness = Spring.StiffnessMediumLow)
                ) { value, _ -> progress = value }
                PlayerSheet.onSettled(minimized = target == 1f)
            }
        }

        fun dragBy(dy: Float) {
            animJob?.cancel()
            progress = (progress + dy / minTop).coerceIn(0f, 1f)
        }

        fun release(velocity: Float) {
            val fling = with(density) { 1000.dp.toPx() }
            val target = when {
                velocity > fling -> 1f
                velocity < -fling -> 0f
                progress > 0.5f -> 1f
                else -> 0f
            }
            settle(target, velocity)
        }

        val requestId by PlayerSheet.requestId.collectAsState()
        LaunchedEffect(requestId) {
            settle(if (PlayerSheet.target.value == PlayerSheet.Target.MINIMIZED) 1f else 0f)
        }

        // Solo estos booleanos provocan recomposición, y solo cuando cambian.
        val minimized by remember { derivedStateOf { progress > 0.02f } }
        val fullyMini by remember { derivedStateOf { progress >= 0.999f } }
        val canCollapse by remember { derivedStateOf { progress < 0.5f } }
        BackHandler(enabled = canCollapse) { settle(1f) }

        // El video se dibuja a tamaño completo y se escala: los toques llegan en coordenadas
        // escaladas, así que se corrigen para que el arrastre siga al dedo.
        val dragFromVideo: (Float) -> Unit = { dy -> dragBy(dy * geo.at(progress).scale) }
        val releaseFromVideo: (Float) -> Unit = { v -> release(v * geo.at(progress).scale) }

        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
            Layout(
                modifier = Modifier
                    .fillMaxSize()
                    .drawWithContent {
                        val g = geo.at(progress)
                        drawRect(
                            color = if (g.shrink > 0f) miniColor else panelColor,
                            topLeft = Offset(0f, g.topY),
                            size = Size(fullW, g.panelH)
                        )
                        if (g.videoTopInset > 0f) {
                            drawRect(Color.Black, Offset(0f, g.topY), Size(fullW, g.videoTopInset))
                        }
                        clipRect(0f, g.topY, fullW, g.topY + g.panelH) {
                            this@drawWithContent.drawContent()
                        }
                    },
                content = {
                    // 0: video
                    Box(Modifier.background(Color.Black)) {
                        PlayerSurface(player, Modifier.fillMaxSize())
                        PlayerGestureOverlay(
                            modifier = Modifier.fillMaxSize(),
                            isFullscreen = false,
                            minimized = minimized,
                            onMinimizedTap = { settle(0f) },
                            onToggleFullscreen = { setFullscreen(true) },
                            onDragDown = dragFromVideo,
                            onDragRelease = releaseFromVideo
                        )
                        // Avisos encima de la capa de gestos para que sus botones reciban el toque.
                        when {
                            uiState is PlayerUiState.Loading && !fullyMini -> CircularProgressIndicator(
                                modifier = Modifier.align(Alignment.Center),
                                color = Color.White
                            )
                            blockedByCall && !minimized -> CallBlockedBox()
                            uiState is PlayerUiState.Error && !minimized ->
                                PlayerErrorBox((uiState as PlayerUiState.Error).message) { viewModel.retry() }
                            playerError != null && !minimized -> PlayerErrorBox(playerError.orEmpty()) {
                                VideoPlayerManager.clearError()
                                video?.let { VideoPlayerManager.playSource(it.source, it.toNowPlaying()) }
                            }
                        }
                    }
                    // 1: título y botones del mini reproductor
                    MiniInfo(
                        title = video?.title ?: nowPlaying?.title.orEmpty(),
                        channel = video?.channelName ?: nowPlaying?.channelName.orEmpty(),
                        isPlaying = isPlaying,
                        modifier = Modifier.sheetDragGestures(
                            onTap = { settle(0f) }, onDrag = ::dragBy, onRelease = ::release
                        )
                    )
                    // 2: detalles del video
                    Box {
                        when (val state = uiState) {
                            is PlayerUiState.Success -> VideoDetails(
                                video = state.video,
                                account = account,
                                comments = viewModel.comments,
                                onLike = viewModel::toggleLike,
                                onDislike = viewModel::toggleDislike,
                                onSubscribe = viewModel::toggleSubscribe,
                                modifier = Modifier.fillMaxSize(),
                                onChannelClick = { channelUrl ->
                                    settle(1f)
                                    navController.navigate(Screen.Channel.createRoute(channelUrl))
                                },
                                onRelatedClick = { PlayerSheet.open(it) }
                            )
                            is PlayerUiState.Error -> LoadErrorBox(state.message) { viewModel.retry() }
                            PlayerUiState.Loading -> Unit
                        }
                    }
                }
            ) { measurables, _ ->
                val g = geo.at(progress)
                val w = fullW.roundToInt()
                val fullVideoH = (fullW * 9f / 16f).roundToInt()
                // Tamaños fijos: los hijos no se vuelven a medir mientras se arrastra.
                val videoP = measurables[0].measure(Constraints.fixed(w, fullVideoH))
                val miniP = measurables[1].measure(
                    Constraints.fixed((fullW - miniH * 16f / 9f).roundToInt().coerceAtLeast(0), miniH.roundToInt())
                )
                val detailsP = measurables[2].measure(
                    Constraints.fixed(w, (fullH - statusTop - fullVideoH).roundToInt().coerceAtLeast(0))
                )
                layout(w, fullH.roundToInt()) {
                    val videoY = g.topY + g.videoTopInset
                    videoP.placeWithLayer(0, videoY.roundToInt()) {
                        scaleX = g.scale
                        scaleY = g.scale
                        transformOrigin = TransformOrigin(0f, 0f)
                    }
                    // Los hijos ocultos se colocan fuera de la pantalla en vez de quitarse: así
                    // conservan lo ya dibujado y no hay tirón al volver a aparecer.
                    val offscreen = fullH.roundToInt()
                    miniP.placeWithLayer(g.videoW.roundToInt(), if (g.shrink > 0f) videoY.roundToInt() else offscreen) {
                        alpha = g.shrink
                        compositingStrategy = CompositingStrategy.ModulateAlpha
                    }
                    detailsP.placeWithLayer(
                        0,
                        if (g.detailsAlpha > 0f) (videoY + g.videoH).roundToInt() else offscreen
                    ) {
                        alpha = g.detailsAlpha
                        compositingStrategy = CompositingStrategy.ModulateAlpha
                    }
                }
            }
        }
    }
}

/** Posiciones del panel para un progreso dado (reutiliza el objeto: sin asignaciones por fotograma). */
private class SheetGeometry(
    private val fullW: Float,
    private val fullH: Float,
    private val minTop: Float,
    private val statusTop: Float,
    private val miniH: Float,
) {
    var shrink = 0f; private set
    var topY = 0f; private set
    var videoTopInset = 0f; private set
    var videoW = 0f; private set
    var videoH = 0f; private set
    var panelH = 0f; private set
    var detailsAlpha = 0f; private set
    var scale = 1f; private set

    fun at(p: Float): SheetGeometry {
        shrink = ((p - SHRINK_START) / (1f - SHRINK_START)).coerceIn(0f, 1f)
        topY = lerp(0f, minTop, p)
        videoTopInset = statusTop * (1f - p)
        videoW = lerp(fullW, miniH * 16f / 9f, shrink)
        videoH = videoW * 9f / 16f
        panelH = maxOf(lerp(fullH, miniH, p), videoTopInset + videoH)
        detailsAlpha = (1f - p / 0.7f).coerceIn(0f, 1f)
        scale = videoW / fullW
        return this
    }
}

@Composable
private fun PlayerSurface(player: androidx.media3.common.Player, modifier: Modifier) {
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            (android.view.LayoutInflater.from(ctx).inflate(R.layout.player_texture_view, null) as PlayerView).apply {
                this.player = player
            }
        },
        update = { view -> if (view.player !== player) view.player = player },
        onRelease = { view -> view.player = null }
    )
}

@Composable
private fun MiniInfo(title: String, channel: String, isPlaying: Boolean, modifier: Modifier) {
    Row(modifier.padding(start = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = channel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(onClick = { VideoPlayerManager.togglePlayPause() }) {
            Icon(
                if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (isPlaying) "Pausar" else "Reproducir",
                modifier = Modifier.size(28.dp)
            )
        }
        IconButton(onClick = { PlayerSheet.close() }) {
            Icon(Icons.Default.Close, contentDescription = "Cerrar")
        }
    }
}

/** Aviso cuando una llamada en curso impide reproducir (Android no da el audio a otras apps). */
@Composable
private fun CallBlockedBox() {
    Column(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.8f)).padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "Hay una llamada en curso. Android pausa los videos mientras dura.",
            color = Color.White,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(12.dp))
        Button(onClick = { VideoPlayerManager.playDuringCall() }) { Text("Reproducir igual") }
    }
}

@Composable
private fun PlayerErrorBox(message: String, onRetry: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.75f)).padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(message, color = Color.White, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        Button(onClick = onRetry) { Text("Reintentar") }
    }
}

@Composable
private fun LoadErrorBox(message: String, onRetry: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Default.ErrorOutline,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.error
        )
        Spacer(Modifier.height(16.dp))
        Text(message, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        Button(onClick = onRetry) { Text("Reintentar") }
    }
}

private fun VideoDetail.toNowPlaying() = NowPlaying(
    url = url,
    title = title,
    channelName = channelName,
    thumbnailUrl = thumbnailUrl
)
