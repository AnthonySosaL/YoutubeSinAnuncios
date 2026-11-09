package com.vancedclean.app.ui.components

import android.app.Activity
import android.content.Context
import android.media.AudioManager
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrightnessHigh
import androidx.compose.material.icons.filled.BrightnessLow
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vancedclean.app.player.VideoPlayerManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun PlayerGestureOverlay(
    modifier: Modifier = Modifier,
    isFullscreen: Boolean = false,
    onToggleFullscreen: () -> Unit = {},
    onDragDown: (Float) -> Unit = {},
    onDragRelease: (velocityY: Float) -> Unit = {},
    minimized: Boolean = false,
    onMinimizedTap: () -> Unit = {},
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val audioManager = remember {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }
    val scope = rememberCoroutineScope()

    val isPlaying by VideoPlayerManager.isPlaying.collectAsState()
    val position by VideoPlayerManager.currentPosition.collectAsState()
    val duration by VideoPlayerManager.duration.collectAsState()

    var showControls by remember { mutableStateOf(true) }
    var hideJob by remember { mutableStateOf<Job?>(null) }
    var seekText by remember { mutableStateOf<String?>(null) }
    var seekSide by remember { mutableIntStateOf(0) }
    var volumeLevel by remember { mutableFloatStateOf(0f) }
    var brightnessLevel by remember { mutableFloatStateOf(0.5f) }
    var showVolume by remember { mutableStateOf(false) }
    var showBrightness by remember { mutableStateOf(false) }
    var isSeeking by remember { mutableStateOf(false) }
    var seekPosition by remember { mutableFloatStateOf(0f) }
    var layoutCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val currentOnDrag by rememberUpdatedState(onDragDown)
    val currentOnRelease by rememberUpdatedState(onDragRelease)
    val isMinimized by rememberUpdatedState(minimized)
    val currentOnMinimizedTap by rememberUpdatedState(onMinimizedTap)

    // Bloqueo de pantalla (solo pantalla completa): ignora toques y gestos hasta desbloquear.
    var locked by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    if (showSettings) PlayerSettingsSheet(onDismiss = { showSettings = false })
    var showLockHint by remember { mutableStateOf(false) }
    var lockHintJob by remember { mutableStateOf<Job?>(null) }

    fun flashLockHint() {
        lockHintJob?.cancel()
        showLockHint = true
        lockHintJob = scope.launch { delay(2500); showLockHint = false }
    }

    BackHandler(enabled = locked) { flashLockHint() }

    fun scheduleHide() {
        hideJob?.cancel()
        if (isPlaying) {
            hideJob = scope.launch { delay(6000); showControls = false }
        }
    }

    LaunchedEffect(isPlaying) {
        if (!isPlaying) {
            hideJob?.cancel()
            showControls = true
        } else if (showControls) {
            scheduleHide()
        }
    }

    Box(
        modifier = modifier
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = { offset ->
                        if (isMinimized) {
                            currentOnMinimizedTap()
                            return@detectTapGestures
                        }
                        if (locked) {
                            flashLockHint()
                            return@detectTapGestures
                        }
                        val forward = offset.x > size.width / 2
                        val delta = if (forward) 10_000L else -10_000L
                        VideoPlayerManager.seekTo(
                            VideoPlayerManager.currentPosition.value + delta
                        )
                        seekSide = if (forward) 1 else -1
                        seekText = if (forward) "+10s" else "-10s"
                        scope.launch { delay(700); seekText = null }
                    },
                    onTap = {
                        when {
                            isMinimized -> currentOnMinimizedTap()
                            locked -> flashLockHint()
                            else -> {
                                showControls = !showControls
                                if (showControls) scheduleHide()
                            }
                        }
                    }
                )
            }
            .onGloballyPositioned { layoutCoords = it }
            .pointerInput(isFullscreen) {
                if (isFullscreen) {
                    // Brillo (izquierda) y volumen (derecha): solo en pantalla completa.
                    var leftSide = false
                    var vLvl = 0f
                    var bLvl = 0f
                    val endLevels = {
                        showVolume = false
                        showBrightness = false
                    }
                    detectVerticalDragGestures(
                        onDragStart = { offset ->
                            leftSide = offset.x < size.width / 2
                            val maxV = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                            vLvl = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                                .toFloat() / maxV
                            val brt = activity?.window?.attributes?.screenBrightness ?: -1f
                            bLvl = if (brt < 0) try {
                                Settings.System.getInt(
                                    context.contentResolver,
                                    Settings.System.SCREEN_BRIGHTNESS
                                ) / 255f
                            } catch (_: Exception) {
                                0.5f
                            } else brt
                        },
                        onVerticalDrag = { change, dy ->
                            val delta = -dy / size.height * 1.5f
                            if (locked) {
                                // Bloqueado: sin brillo ni volumen.
                            } else if (leftSide) {
                                bLvl = (bLvl + delta).coerceIn(0.01f, 1f)
                                activity?.window?.let { w ->
                                    val a = w.attributes
                                    a.screenBrightness = bLvl
                                    w.attributes = a
                                }
                                brightnessLevel = bLvl
                                showBrightness = true
                            } else {
                                vLvl = (vLvl + delta).coerceIn(0f, 1f)
                                val maxV = audioManager
                                    .getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                                audioManager.setStreamVolume(
                                    AudioManager.STREAM_MUSIC,
                                    (vLvl * maxV).toInt().coerceIn(0, maxV),
                                    0
                                )
                                volumeLevel = vLvl
                                showVolume = true
                            }
                            change.consume()
                        },
                        onDragEnd = endLevels,
                        onDragCancel = endLevels
                    )
                } else {
                    // Pantalla normal: deslizar hacia abajo para minimizar. El reproductor
                    // se mueve con el dedo, así que medimos en coordenadas de la raíz.
                    val tracker = VelocityTracker()
                    var lastRootY = 0f
                    fun rootY(local: Offset): Float =
                        layoutCoords?.takeIf { it.isAttached }?.localToRoot(local)?.y ?: local.y
                    detectVerticalDragGestures(
                        onDragStart = { offset ->
                            tracker.resetTracking()
                            lastRootY = rootY(offset)
                        },
                        onVerticalDrag = { change, _ ->
                            val y = rootY(change.position)
                            val dy = y - lastRootY
                            lastRootY = y
                            tracker.addPosition(change.uptimeMillis, Offset(0f, y))
                            change.consume()
                            currentOnDrag(dy)
                        },
                        onDragEnd = { currentOnRelease(tracker.calculateVelocity().y) },
                        onDragCancel = { currentOnRelease(0f) }
                    )
                }
            }
    ) {
        // Player controls overlay
        AnimatedVisibility(
            visible = showControls && !minimized && !locked,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.35f))
            ) {
                // Repetir el video en bucle (como en Telegram).
                val loop by VideoPlayerManager.loopVideo.collectAsState()
                IconButton(
                    onClick = {
                        VideoPlayerManager.toggleLoopVideo()
                        android.widget.Toast.makeText(
                            context, if (!loop) "Repetir video: activado" else "Repetir video: desactivado",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                        scheduleHide()
                    },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 4.dp, end = 52.dp)
                ) {
                    Icon(
                        if (loop) Icons.Default.RepeatOne else Icons.Default.Repeat,
                        contentDescription = if (loop) "Repetir video: activado" else "Repetir video",
                        tint = if (loop) Color(0xFFFF4D4D) else Color.White
                    )
                }

                // Ajustes del video: calidad, velocidad y subtítulos.
                IconButton(
                    onClick = {
                        hideJob?.cancel()
                        showSettings = true
                    },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                ) {
                    Icon(
                        Icons.Default.Settings,
                        contentDescription = "Ajustes del video",
                        tint = Color.White
                    )
                }

                // Candado (como en la app de referencia): a la derecha, centrado.
                if (isFullscreen) {
                    IconButton(
                        onClick = {
                            locked = true
                            hideJob?.cancel()
                            flashLockHint()
                        },
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(end = 24.dp)
                            .size(52.dp)
                    ) {
                        Icon(
                            Icons.Default.LockOpen,
                            contentDescription = "Bloquear pantalla",
                            tint = Color.White,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                }

                Row(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalArrangement = Arrangement.spacedBy(36.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            VideoPlayerManager.seekTo(position - 10_000L)
                            scheduleHide()
                        },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            Icons.Default.Replay10,
                            contentDescription = "-10s",
                            tint = Color.White,
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    IconButton(
                        onClick = {
                            VideoPlayerManager.togglePlayPause()
                            scheduleHide()
                        },
                        modifier = Modifier
                            .size(62.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.3f))
                    ) {
                        Icon(
                            if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pausar" else "Reproducir",
                            tint = Color.White,
                            modifier = Modifier.size(42.dp)
                        )
                    }

                    IconButton(
                        onClick = {
                            VideoPlayerManager.seekTo(position + 10_000L)
                            scheduleHide()
                        },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            Icons.Default.Forward10,
                            contentDescription = "+10s",
                            tint = Color.White,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp)
                ) {
                    Slider(
                        value = if (isSeeking) seekPosition
                        else if (duration > 0) position.toFloat() / duration
                        else 0f,
                        onValueChange = {
                            isSeeking = true
                            seekPosition = it
                        },
                        onValueChangeFinished = {
                            VideoPlayerManager.seekTo((seekPosition * duration).toLong())
                            isSeeking = false
                            scheduleHide()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(24.dp),
                        colors = SliderDefaults.colors(
                            thumbColor = Color.Red,
                            activeTrackColor = Color.Red,
                            inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                        )
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${fmtTime(position)} / ${fmtTime(duration)}",
                            color = Color.White,
                            fontSize = 12.sp
                        )
                        IconButton(onClick = {
                            onToggleFullscreen()
                            scheduleHide()
                        }) {
                            Icon(
                                if (isFullscreen) Icons.Default.FullscreenExit
                                else Icons.Default.Fullscreen,
                                contentDescription = "Pantalla completa",
                                tint = Color.White
                            )
                        }
                    }
                }
            }
        }

        // Pantalla bloqueada: solo aparece el candado al tocar; tocarlo desbloquea.
        AnimatedVisibility(
            visible = locked && showLockHint,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 24.dp)
        ) {
            IconButton(
                onClick = {
                    locked = false
                    showLockHint = false
                    lockHintJob?.cancel()
                    showControls = true
                    scheduleHide()
                },
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.55f))
            ) {
                Icon(
                    Icons.Default.Lock,
                    contentDescription = "Desbloquear pantalla",
                    tint = Color.White,
                    modifier = Modifier.size(30.dp)
                )
            }
        }

        // Thin progress bar when controls are hidden
        if ((!showControls || minimized || locked) && duration > 0) {
            LinearProgressIndicator(
                progress = { position.toFloat() / duration },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(2.dp),
                color = Color.Red,
                trackColor = Color.Transparent,
            )
        }

        // Double-tap seek feedback
        AnimatedVisibility(
            visible = seekText != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(
                if (seekSide < 0) Alignment.CenterStart else Alignment.CenterEnd
            )
        ) {
            Box(
                modifier = Modifier
                    .padding(horizontal = 40.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        if (seekSide < 0) Icons.Default.FastRewind
                        else Icons.Default.FastForward,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                    Text(
                        text = seekText.orEmpty(),
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Volume indicator
        AnimatedVisibility(
            visible = showVolume,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 16.dp)
        ) {
            LevelIndicator(
                icon = when {
                    volumeLevel > .5f -> Icons.Default.VolumeUp
                    volumeLevel > 0f -> Icons.Default.VolumeDown
                    else -> Icons.Default.VolumeOff
                },
                level = volumeLevel
            )
        }

        // Brightness indicator
        AnimatedVisibility(
            visible = showBrightness,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 16.dp)
        ) {
            LevelIndicator(
                icon = if (brightnessLevel > .5f) Icons.Default.BrightnessHigh
                else Icons.Default.BrightnessLow,
                level = brightnessLevel
            )
        }
    }
}

@Composable
private fun LevelIndicator(icon: ImageVector, level: Float) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black.copy(alpha = .7f))
            .padding(12.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(24.dp))
            LinearProgressIndicator(
                progress = { level },
                modifier = Modifier
                    .width(80.dp)
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = Color.White,
                trackColor = Color.White.copy(alpha = .3f)
            )
            Text(
                "${(level * 100).toInt()}%",
                color = Color.White,
                fontSize = 11.sp
            )
        }
    }
}

private fun fmtTime(ms: Long): String {
    if (ms <= 0) return "0:00"
    val s = ms / 1000
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, sec)
    else String.format(Locale.US, "%d:%02d", m, sec)
}
