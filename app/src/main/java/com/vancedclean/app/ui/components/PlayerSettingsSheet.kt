package com.vancedclean.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.vancedclean.app.player.VideoPlayerManager

private val SPEEDS = listOf(0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)

private enum class Page { MAIN, QUALITY, SPEED, SUBTITLES }

/** Ajustes del video en reproducción: calidad, velocidad y subtítulos. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerSettingsSheet(onDismiss: () -> Unit) {
    val source by VideoPlayerManager.source.collectAsState()
    val quality by VideoPlayerManager.quality.collectAsState()
    val subtitle by VideoPlayerManager.subtitle.collectAsState()
    val speed by VideoPlayerManager.speed.collectAsState()
    var page by remember { mutableStateOf(Page.MAIN) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .navigationBarsPadding()
                .padding(bottom = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            when (page) {
                Page.MAIN -> {
                    Row(Icons.Default.HighQuality, "Calidad",
                        quality?.label ?: if (source?.isLive == true) "Auto (en vivo)" else "Auto") {
                        if (!source?.qualities.isNullOrEmpty()) page = Page.QUALITY
                    }
                    Row(Icons.Default.Speed, "Velocidad", speedLabel(speed)) { page = Page.SPEED }
                    Row(Icons.Default.ClosedCaption, "Subtítulos", subtitle?.label ?: "Desactivados") {
                        page = Page.SUBTITLES
                    }
                }
                Page.QUALITY -> {
                    Back("Calidad") { page = Page.MAIN }
                    source?.qualities.orEmpty().forEach { option ->
                        Choice(option.label, option == quality) {
                            VideoPlayerManager.setQuality(option)
                            onDismiss()
                        }
                    }
                }
                Page.SPEED -> {
                    Back("Velocidad") { page = Page.MAIN }
                    SPEEDS.forEach { value ->
                        Choice(speedLabel(value), value == speed) {
                            VideoPlayerManager.setSpeed(value)
                            onDismiss()
                        }
                    }
                }
                Page.SUBTITLES -> {
                    Back("Subtítulos") { page = Page.MAIN }
                    Choice("Desactivados", subtitle == null) {
                        VideoPlayerManager.setSubtitle(null)
                        onDismiss()
                    }
                    val subs = source?.subtitles.orEmpty()
                    if (subs.isEmpty()) {
                        Text(
                            "Este video no tiene subtítulos",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                    subs.forEach { option ->
                        Choice(option.label, option == subtitle) {
                            VideoPlayerManager.setSubtitle(option)
                            onDismiss()
                        }
                    }
                }
            }
        }
    }
}

private fun speedLabel(value: Float) = if (value == 1f) "Normal" else "${value}x".replace(".0x", "x")

@Composable
private fun Row(icon: ImageVector, title: String, value: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(value) },
        leadingContent = { Icon(icon, contentDescription = null) },
        modifier = Modifier.clickable(onClick = onClick)
    )
}

@Composable
private fun Back(title: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title, style = MaterialTheme.typography.titleMedium) },
        leadingContent = { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver") },
        modifier = Modifier.clickable(onClick = onClick)
    )
}

@Composable
private fun Choice(label: String, selected: Boolean, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(label) },
        trailingContent = { if (selected) Icon(Icons.Default.Check, contentDescription = "Seleccionado") },
        modifier = Modifier.clickable(onClick = onClick)
    )
}
