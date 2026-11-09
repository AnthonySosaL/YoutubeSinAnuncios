package com.vancedclean.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.vancedclean.app.download.DlFormat
import com.vancedclean.app.download.DownloadOptions
import com.vancedclean.app.download.DownloadQueue
import com.vancedclean.app.download.DownloadResolver
import com.vancedclean.app.download.formatBytes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Hoja "Descargar" que sale desde el botón del video. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadSheet(url: String, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(bottom = 24.dp).verticalScroll(rememberScrollState())) {
            DownloadPicker(url = url, onStarted = onDismiss)
        }
    }
}

/**
 * Busca las calidades del enlace y deja elegir MP4 (calidad de video) o MP3 (calidad de audio).
 * Se usa tanto en la hoja del video como en Ajustes → Descargar.
 */
@Composable
fun DownloadPicker(url: String, onStarted: () -> Unit = {}) {
    val context = LocalContext.current
    var options by remember(url) { mutableStateOf<DownloadOptions?>(null) }
    var error by remember(url) { mutableStateOf<String?>(null) }
    var format by remember(url) { mutableStateOf(DlFormat.MP4) }
    var videoIndex by remember(url) { mutableIntStateOf(0) }
    var audioIndex by remember(url) { mutableIntStateOf(0) }

    LaunchedEffect(url) {
        options = null
        error = null
        withContext(Dispatchers.IO) {
            runCatching { DownloadResolver.resolve(url) }
                .onSuccess { o ->
                    options = o
                    // Por defecto 720p (o la más cercana por debajo) y el mejor audio.
                    videoIndex = o.videos.indexOfFirst { it.height <= 720 }.coerceAtLeast(0)
                    if (o.videos.isEmpty()) format = DlFormat.MP3
                }
                .onFailure { error = it.message ?: "No se pudo leer el enlace" }
        }
    }

    val o = options
    when {
        error != null -> Text(
            error.orEmpty(),
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(16.dp)
        )
        o == null -> Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        else -> Column(Modifier.padding(horizontal = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(
                    model = o.thumbnailUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.width(112.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(o.title, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        "${o.author} • ${o.source.label}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (o.videos.isNotEmpty()) {
                    FilterChip(format == DlFormat.MP4, { format = DlFormat.MP4 }, label = { Text("Video MP4") })
                }
                if (o.audios.isNotEmpty()) {
                    FilterChip(format == DlFormat.MP3, { format = DlFormat.MP3 }, label = { Text("Audio MP3") })
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                if (format == DlFormat.MP4) "Calidad de video" else "Calidad de audio",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(vertical = 4.dp)
            )
            if (format == DlFormat.MP4) {
                o.videos.forEachIndexed { i, v ->
                    ChoiceRow(v.label, formatBytes(v.approxBytes), i == videoIndex) { videoIndex = i }
                }
            } else {
                o.audios.forEachIndexed { i, a ->
                    ChoiceRow(a.label, formatBytes(a.approxBytes), i == audioIndex) { audioIndex = i }
                }
            }
            if (format == DlFormat.MP3 && o.source.name == "YOUTUBE") {
                Text(
                    "Los MP3 de YouTube aparecen en el Reproductor de música.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    if (format == DlFormat.MP4) {
                        o.videos.getOrNull(videoIndex)?.let { DownloadQueue.enqueueVideo(context, o, it) }
                    } else {
                        o.audios.getOrNull(audioIndex)?.let { DownloadQueue.enqueueAudio(context, o, it) }
                    }
                    toast(context, "Descarga iniciada")
                    onStarted()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Download, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Descargar")
            }
        }
    }
}

@Composable
private fun ChoiceRow(label: String, size: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(label, modifier = Modifier.weight(1f))
        if (size.isNotBlank()) {
            Text("~$size", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
