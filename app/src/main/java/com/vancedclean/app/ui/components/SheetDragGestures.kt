package com.vancedclean.app.ui.components

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned

/**
 * Toque + arrastre vertical para un elemento que se mueve con el dedo (el panel del
 * reproductor). Los desplazamientos se miden en coordenadas de la raíz para que el
 * movimiento del propio elemento no los altere.
 */
fun Modifier.sheetDragGestures(
    onTap: () -> Unit,
    onDrag: (Float) -> Unit,
    onRelease: (velocityY: Float) -> Unit,
): Modifier = composed {
    val currentTap by rememberUpdatedState(onTap)
    val currentDrag by rememberUpdatedState(onDrag)
    val currentRelease by rememberUpdatedState(onRelease)
    val coords = remember { arrayOfNulls<LayoutCoordinates>(1) }

    fun rootY(local: Offset): Float =
        coords[0]?.takeIf { it.isAttached }?.localToRoot(local)?.y ?: local.y

    this
        .onGloballyPositioned { coords[0] = it }
        .pointerInput(Unit) { detectTapGestures(onTap = { currentTap() }) }
        .pointerInput(Unit) {
            val tracker = VelocityTracker()
            var lastY = 0f
            detectVerticalDragGestures(
                onDragStart = { offset ->
                    tracker.resetTracking()
                    lastY = rootY(offset)
                },
                onVerticalDrag = { change, _ ->
                    val y = rootY(change.position)
                    currentDrag(y - lastY)
                    lastY = y
                    tracker.addPosition(change.uptimeMillis, Offset(0f, y))
                    change.consume()
                },
                onDragEnd = { currentRelease(tracker.calculateVelocity().y) },
                onDragCancel = { currentRelease(0f) }
            )
        }
}
