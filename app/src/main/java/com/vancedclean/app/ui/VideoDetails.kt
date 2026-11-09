package com.vancedclean.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Download
import com.vancedclean.app.ui.components.DownloadSheet
import androidx.compose.material.icons.filled.WatchLater
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.vancedclean.app.data.LikeStatus
import com.vancedclean.app.data.VideoAccountState
import com.vancedclean.app.data.YouTubeAccount
import com.vancedclean.app.ui.components.SaveToPlaylistDialog
import com.vancedclean.app.ui.components.VideoCard
import com.vancedclean.app.ui.components.accountAction
import com.vancedclean.app.ui.components.shareVideo
import com.vancedclean.app.ui.components.toast
import com.vancedclean.app.viewmodel.CommentItem
import com.vancedclean.app.viewmodel.CommentsLoader
import com.vancedclean.app.viewmodel.VideoDetail
import com.vancedclean.app.viewmodel.formatCompact
import com.vancedclean.app.viewmodel.formatDate
import com.vancedclean.app.viewmodel.formatSubscriberCount
import com.vancedclean.app.viewmodel.formatViewCount

/** Información del video debajo del reproductor: título, canal, acciones, comentarios y relacionados. */
@Composable
fun VideoDetails(
    video: VideoDetail,
    account: VideoAccountState,
    comments: CommentsLoader,
    onLike: () -> Unit,
    onDislike: () -> Unit,
    onSubscribe: () -> Unit,
    onChannelClick: (String) -> Unit,
    onRelatedClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) = key(video.url) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isDescriptionExpanded by remember { mutableStateOf(false) }
    var showComments by remember { mutableStateOf(false) }
    var showSaveDialog by remember { mutableStateOf(false) }
    var showDownload by remember { mutableStateOf(false) }
    if (showDownload) DownloadSheet(url = video.url, onDismiss = { showDownload = false })
    val commentsState by comments.state.collectAsState()

    if (showSaveDialog && video.videoId != null) {
        SaveToPlaylistDialog(videoId = video.videoId, onDismiss = { showSaveDialog = false })
    }

    LazyColumn(modifier = modifier, state = rememberLazyListState()) {
        item {
            Text(
                text = video.title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 12.dp)
            )
        }

        item {
            val meta = listOf(formatViewCount(video.viewCount), formatDate(video.uploadDate))
                .filter { it.isNotBlank() }.joinToString(" • ")
            if (meta.isNotEmpty()) {
                Text(
                    text = meta,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                )
            }
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clickable(enabled = video.channelUrl.isNotBlank()) { onChannelClick(video.channelUrl) },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AsyncImage(
                        model = video.channelAvatarUrl,
                        contentDescription = video.channelName,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = video.channelName,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        val subs = formatSubscriberCount(video.channelSubscribers)
                        if (subs.isNotBlank()) {
                            Text(
                                subs,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                if (account.subscribed) {
                    FilledTonalButton(onClick = onSubscribe) { Text("Suscrito") }
                } else {
                    Button(onClick = onSubscribe) { Text("Suscribirse") }
                }
            }
        }

        // Acciones: me gusta, no me gusta, compartir, ver más tarde, guardar.
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val liked = account.likeStatus == LikeStatus.LIKE
                val disliked = account.likeStatus == LikeStatus.DISLIKE
                val likes = video.likeCount.takeIf { it >= 0 }
                ActionChip(
                    icon = if (liked) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp,
                    label = likes?.let { formatCompact(it) } ?: "Me gusta",
                    selected = liked,
                    onClick = onLike
                )
                ActionChip(
                    icon = if (disliked) Icons.Filled.ThumbDown else Icons.Outlined.ThumbDown,
                    label = "No me gusta",
                    selected = disliked,
                    onClick = onDislike
                )
                ActionChip(Icons.Default.Share, "Compartir") { shareVideo(context, video.url, video.title) }
                ActionChip(Icons.Default.Download, "Descargar") { showDownload = true }
                ActionChip(Icons.Default.WatchLater, "Ver más tarde") {
                    val id = video.videoId ?: return@ActionChip
                    scope.accountAction(context, "Guardado en Ver más tarde") {
                        YouTubeAccount.addToPlaylist(it, "WL", id)
                    }
                }
                ActionChip(Icons.AutoMirrored.Filled.PlaylistAdd, "Guardar") {
                    if (YouTubeAccount.isLoggedIn(context)) showSaveDialog = true
                    else toast(context, "Inicia sesión para usar tus playlists")
                }
            }
        }

        if (video.description.isNotBlank()) {
            item {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .clickable { isDescriptionExpanded = !isDescriptionExpanded },
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        text = video.description,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = if (isDescriptionExpanded) Int.MAX_VALUE else 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }

        // Comentarios: se cargan al tocar la tarjeta.
        item {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp)
                    .clickable {
                        showComments = !showComments
                        if (showComments && !commentsState.loaded) comments.loadMore()
                    },
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                val countText = if (commentsState.count >= 0) " • ${formatCompact(commentsState.count.toLong())}" else ""
                Text(
                    text = if (showComments) "Comentarios$countText  (ocultar)" else "Comentarios$countText  (ver)",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }

        if (showComments) {
            when {
                commentsState.disabled -> item { InfoText("Los comentarios están desactivados") }
                commentsState.error != null && commentsState.comments.isEmpty() -> item {
                    TextButton(onClick = { comments.loadMore() }, modifier = Modifier.padding(horizontal = 4.dp)) {
                        Text("No se pudieron cargar. Reintentar")
                    }
                }
                commentsState.loaded && commentsState.comments.isEmpty() -> item { InfoText("Aún no hay comentarios") }
            }
            items(commentsState.comments) { CommentRow(it) }
            if (commentsState.isLoading) {
                item {
                    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(28.dp))
                    }
                }
            } else if (commentsState.hasMore) {
                item {
                    LaunchedEffect(commentsState.comments.size) { comments.loadMore() }
                }
            }
        }

        item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }

        item {
            Text(
                text = "Videos relacionados",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
            )
        }

        items(video.relatedVideos) { related ->
            VideoCard(video = related, onClick = { onRelatedClick(related.url) })
        }
    }
}

@Composable
private fun ActionChip(icon: ImageVector, label: String, selected: Boolean = false, onClick: () -> Unit) {
    AssistChip(
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp)) },
        shape = RoundedCornerShape(50),
        colors = if (selected) AssistChipDefaults.assistChipColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            leadingIconContentColor = MaterialTheme.colorScheme.primary
        ) else AssistChipDefaults.assistChipColors()
    )
}

@Composable
private fun InfoText(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
    )
}

@Composable
private fun CommentRow(comment: CommentItem) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        AsyncImage(
            model = comment.avatarUrl,
            contentDescription = comment.author,
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = buildString {
                    if (comment.pinned) append("📌 ")
                    append(comment.author)
                    if (comment.date.isNotBlank()) append(" • ").append(comment.date)
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                comment.text,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = if (expanded) Int.MAX_VALUE else 4,
                overflow = TextOverflow.Ellipsis
            )
            val extra = listOfNotNull(
                comment.likeCount.takeIf { it > 0 }?.let { "👍 ${formatCompact(it.toLong())}" },
                comment.replyCount.takeIf { it > 0 }?.let { "$it respuestas" }
            ).joinToString("   ")
            if (extra.isNotBlank()) {
                Text(
                    extra,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}
