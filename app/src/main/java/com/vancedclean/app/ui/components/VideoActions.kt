package com.vancedclean.app.ui.components

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.WatchLater
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.vancedclean.app.data.PlaylistOption
import com.vancedclean.app.data.YouTubeAccount
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Extrae el id de un enlace de YouTube (watch?v=, youtu.be, shorts). */
fun videoIdOf(url: String): String? {
    Regex("""[?&]v=([\w-]{11})""").find(url)?.let { return it.groupValues[1] }
    Regex("""(?:youtu\.be/|/shorts/|/live/|/embed/)([\w-]{11})""").find(url)?.let { return it.groupValues[1] }
    return null
}

fun shareVideo(context: Context, url: String, title: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, title)
        putExtra(Intent.EXTRA_TEXT, url)
    }
    context.startActivity(Intent.createChooser(send, "Compartir video").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

fun toast(context: Context, message: String) =
    Toast.makeText(context.applicationContext, message, Toast.LENGTH_SHORT).show()

/** Ejecuta una acción de cuenta en segundo plano y avisa con un Toast. */
fun CoroutineScope.accountAction(
    context: Context,
    success: String?,
    onDone: (Boolean) -> Unit = {},
    block: (Context) -> Unit,
) = launch {
    val app = context.applicationContext
    val error = withContext(Dispatchers.IO) {
        runCatching { block(app) }.exceptionOrNull()
    }
    if (error == null) success?.let { toast(app, it) } else toast(app, error.message ?: "No se pudo completar")
    onDone(error == null)
}

/** Menú de opciones de un video (como la lista de opciones de la app vieja). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoOptionsSheet(
    url: String,
    title: String,
    onDismiss: () -> Unit,
    onRemove: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val videoId = remember(url) { videoIdOf(url) }
    var showPlaylists by remember { mutableStateOf(false) }

    if (showPlaylists && videoId != null) {
        SaveToPlaylistDialog(videoId = videoId, onDismiss = { showPlaylists = false; onDismiss() })
        return
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(bottom = 24.dp)) {
            Text(title, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), maxLines = 2)
            OptionRow(Icons.Default.WatchLater, "Guardar en Ver más tarde") {
                if (videoId == null) return@OptionRow
                scope.accountAction(context, "Guardado en Ver más tarde") {
                    YouTubeAccount.addToPlaylist(it, "WL", videoId)
                }
                onDismiss()
            }
            OptionRow(Icons.AutoMirrored.Filled.PlaylistAdd, "Guardar en una playlist") {
                if (!YouTubeAccount.isLoggedIn(context)) {
                    toast(context, "Inicia sesión para usar tus playlists")
                } else showPlaylists = true
            }
            OptionRow(Icons.Default.Share, "Compartir") {
                shareVideo(context, url, title)
                onDismiss()
            }
            if (onRemove != null) {
                OptionRow(Icons.Default.Delete, "Quitar de esta lista") {
                    onRemove()
                    onDismiss()
                }
            }
        }
    }
}

@Composable
private fun OptionRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(label) },
        leadingContent = { Icon(icon, contentDescription = null) },
        modifier = Modifier.clickable(onClick = onClick)
    )
}

/** Diálogo "Guardar en…": marca o desmarca las playlists del usuario y permite crear una nueva. */
@Composable
fun SaveToPlaylistDialog(videoId: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var options by remember { mutableStateOf<List<PlaylistOption>?>(null) }
    var creating by remember { mutableStateOf(false) }
    var newTitle by remember { mutableStateOf("") }

    LaunchedEffect(videoId) {
        options = withContext(Dispatchers.IO) {
            runCatching { YouTubeAccount.playlistsForVideo(context.applicationContext, videoId) }
                .onFailure { withContext(Dispatchers.Main) { toast(context, it.message ?: "Error cargando playlists") } }
                .getOrDefault(emptyList())
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (creating) "Nueva playlist" else "Guardar en…") },
        text = {
            when {
                creating -> OutlinedTextField(
                    value = newTitle,
                    onValueChange = { newTitle = it },
                    label = { Text("Nombre") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                options == null -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                else -> LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items(options.orEmpty(), key = { it.id }) { option ->
                        ListItem(
                            headlineContent = { Text(option.title) },
                            leadingContent = {
                                Checkbox(checked = option.containsVideo, onCheckedChange = null)
                            },
                            modifier = Modifier.clickable {
                                val add = !option.containsVideo
                                options = options?.map { if (it.id == option.id) it.copy(containsVideo = add) else it }
                                scope.accountAction(
                                    context,
                                    if (add) "Guardado en ${option.title}" else "Quitado de ${option.title}",
                                    onDone = { ok ->
                                        if (!ok) options = options?.map {
                                            if (it.id == option.id) it.copy(containsVideo = !add) else it
                                        }
                                    }
                                ) {
                                    if (add) YouTubeAccount.addToPlaylist(it, option.id, videoId)
                                    else YouTubeAccount.removeFromPlaylist(it, option.id, videoId)
                                }
                            }
                        )
                    }
                    item {
                        ListItem(
                            headlineContent = { Text("Crear playlist nueva") },
                            leadingContent = { Icon(Icons.Default.Add, contentDescription = null) },
                            modifier = Modifier.clickable { creating = true }
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (creating) {
                TextButton(
                    enabled = newTitle.isNotBlank(),
                    onClick = {
                        val title = newTitle.trim()
                        scope.accountAction(context, "Playlist \"$title\" creada") {
                            YouTubeAccount.createPlaylist(it, title, videoId)
                        }
                        onDismiss()
                    }
                ) { Text("Crear") }
            } else {
                TextButton(onClick = onDismiss) { Text("Listo") }
            }
        },
        dismissButton = {
            if (creating) TextButton(onClick = { creating = false }) { Text("Cancelar") }
        }
    )
}
