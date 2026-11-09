package com.vancedclean.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.vancedclean.app.viewmodel.VideoItem
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

@Composable
fun VideoCard(video: VideoItem, onClick: () -> Unit, onRemove: (() -> Unit)? = null) {
    var menuOpen by remember { mutableStateOf(false) }
    if (menuOpen) {
        VideoOptionsSheet(
            url = video.url,
            title = video.title,
            onDismiss = { menuOpen = false },
            onRemove = onRemove
        )
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(bottom = 12.dp)
    ) {
        AsyncImage(
            model = video.thumbnailUrl,
            contentDescription = video.title,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f),
            contentScale = ContentScale.Crop
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            AsyncImage(
                model = video.uploaderAvatarUrl,
                contentDescription = video.uploaderName,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = video.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = buildString {
                        append(video.uploaderName)
                        if (video.viewCount > 0) {
                            append(" • ")
                            append(formatViewCount(video.viewCount))
                        }
                        if (video.duration.isNotEmpty()) {
                            append(" • ")
                            append(video.duration)
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Default.MoreVert,
                    contentDescription = "Más opciones",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun formatViewCount(count: Long): String {
    return when {
        count >= 1_000_000_000 -> "${count / 1_000_000_000}B vistas"
        count >= 1_000_000 -> "${count / 1_000_000}M vistas"
        count >= 1_000 -> "${count / 1_000}K vistas"
        else -> "$count vistas"
    }
}
