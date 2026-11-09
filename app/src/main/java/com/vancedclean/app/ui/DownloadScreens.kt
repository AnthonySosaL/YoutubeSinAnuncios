package com.vancedclean.app.ui

import android.content.ClipboardManager
import android.content.Context
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.PlayerView
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.vancedclean.app.download.DlFormat
import com.vancedclean.app.download.DownloadEntry
import com.vancedclean.app.download.DownloadQueue
import com.vancedclean.app.download.DownloadStore
import com.vancedclean.app.download.DownloadTask
import com.vancedclean.app.download.TaskState
import com.vancedclean.app.download.MediaLibrary
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.rememberCoroutineScope
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.vancedclean.app.download.InstagramSession
import com.vancedclean.app.download.formatBytes
import com.vancedclean.app.navigation.Screen
import com.vancedclean.app.player.VideoPlayerManager
import com.vancedclean.app.ui.components.DownloadPicker

// ---------------------------------------------------------------- Descargar

/** Ajustes → Descargar: pegar un enlace de YouTube o Instagram y elegir formato y calidad. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadScreen(navController: NavController, initialUrl: String) {
    val context = LocalContext.current
    var text by remember { mutableStateOf(initialUrl) }
    var searched by remember { mutableStateOf(initialUrl.takeIf { it.isNotBlank() }) }
    val tasks by DownloadQueue.tasks.collectAsState()

    Scaffold(topBar = { BackTopBar("Descargar", navController) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "Pega el enlace de un video de YouTube o de un reel/post de Instagram.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        label = { Text("Enlace") },
                        singleLine = true,
                        trailingIcon = {
                            IconButton(onClick = { pasteFromClipboard(context)?.let { text = it } }) {
                                Icon(Icons.Default.ContentPaste, contentDescription = "Pegar")
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    InstagramSessionRow(navController)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { searched = text.trim().takeIf { it.isNotBlank() } }) { Text("Buscar calidades") }
                        FilledTonalButton(onClick = { navController.navigate(Screen.MyDownloads.route) }) { Text("Mis descargas") }
                    }
                }
            }
            searched?.let { url ->
                item(key = url) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)
                    ) {
                        Column(Modifier.padding(vertical = 12.dp)) { DownloadPicker(url = url) }
                    }
                }
            }
            if (tasks.isNotEmpty()) {
                item { SectionTitle("Descargas de esta sesión") }
                items(tasks, key = { it.id }) { TaskRow(it) }
            }
        }
    }
}

/** Estado de la sesión de Instagram (necesaria para muchos reels y posts). */
@Composable
private fun InstagramSessionRow(navController: NavController) {
    val context = LocalContext.current
    var logged by remember { mutableStateOf(InstagramSession.isLoggedIn(context)) }
    LaunchedEffect(navController.currentBackStackEntry) { logged = InstagramSession.isLoggedIn(context) }
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            if (logged) "Instagram: sesión iniciada" else "Instagram: sin sesión (muchos reels la piden)",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        if (logged) {
            TextButton(onClick = { InstagramSession.clear(context); logged = false }) { Text("Cerrar sesión") }
        } else {
            TextButton(onClick = { navController.navigate(Screen.InstagramLogin.route) }) { Text("Iniciar sesión") }
        }
    }
}

private fun pasteFromClipboard(context: Context): String? {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    return cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()
}

// ---------------------------------------------------------------- Mis descargas

/** Todo lo descargado: videos y audios, para ver sin internet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyDownloadsScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        scope.launch(Dispatchers.IO) { DownloadStore.refresh(context) }
    }
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) { DownloadStore.load(context) }
        val missing = MediaLibrary.readPermissions().filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) permissions.launch(missing.toTypedArray())
    }
    val entries by DownloadStore.entries.collectAsState()
    val tasks by DownloadQueue.tasks.collectAsState()
    var tab by remember { mutableIntStateOf(0) }
    var toDelete by remember { mutableStateOf<DownloadEntry?>(null) }

    toDelete?.let { DeleteEntryDialog(it) { toDelete = null } }

    Scaffold(
        topBar = { BackTopBar("Mis descargas", navController) },
        bottomBar = { MusicMiniBar { navController.navigate(Screen.NowPlaying.route) } }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = tab) {
                Tab(tab == 0, { tab = 0 }, text = { Text("Videos") })
                Tab(tab == 1, { tab = 1 }, text = { Text("Audio") })
            }
            val shown = entries.filter { if (tab == 0) it.format == DlFormat.MP4 else it.format == DlFormat.MP3 }
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
                val running = tasks.filter { it.state != TaskState.LISTO }
                if (running.isNotEmpty()) {
                    item { SectionTitle("En progreso") }
                    items(running, key = { it.id }) { TaskRow(it) }
                    item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
                }
                if (shown.isEmpty()) {
                    item { EmptyText(if (tab == 0) "Aún no descargaste videos" else "Aún no descargaste audios") }
                }
                items(shown, key = { it.id }) { e ->
                    EntryRow(e, onDelete = { toDelete = e }) {
                        if (e.format == DlFormat.MP4) navController.navigate(Screen.LocalVideo.createRoute(e.id))
                        else {
                            VideoPlayerManager.playLocal(context, listOf(e))
                            navController.navigate(Screen.NowPlaying.route)
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Video descargado

/** Reproduce un MP4 descargado, a pantalla completa y sin internet. */
@Composable
fun LocalVideoScreen(navController: NavController, id: String) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { DownloadStore.load(context) }
    val entries by DownloadStore.entries.collectAsState()
    val entry = entries.firstOrNull { it.id == id }

    LaunchedEffect(entry?.id) {
        if (entry != null && VideoPlayerManager.localId.value != entry.id) {
            VideoPlayerManager.playLocal(context, listOf(entry))
        }
    }
    DisposableEffect(Unit) { onDispose { VideoPlayerManager.pause() } }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        val player = remember { VideoPlayerManager.createPlayer(context) }
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                PlayerView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    useController = true
                    this.player = player
                }
            },
            update = { it.player = VideoPlayerManager.getPlayer() },
            onRelease = { it.player = null }
        )
        IconButton(onClick = { navController.popBackStack() }, modifier = Modifier.padding(top = 32.dp, start = 4.dp)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver", tint = Color.White)
        }
        if (entry == null) Text("No se encontró el archivo", color = Color.White, modifier = Modifier.align(Alignment.Center))
    }
}

// ---------------------------------------------------------------- Piezas comunes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BackTopBar(title: String, navController: NavController) {
    TopAppBar(
        title = { Text(title, fontWeight = FontWeight.Bold) },
        navigationIcon = {
            IconButton(onClick = { navController.popBackStack() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
            }
        }
    )
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp))
}

@Composable
private fun EmptyText(text: String) {
    Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(32.dp))
}

@Composable
private fun TaskRow(task: DownloadTask) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(task.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${task.format.label} • ${task.quality} • " + when (task.state) {
                        TaskState.LISTO -> "Listo"
                        TaskState.ERROR -> task.message
                        else -> task.message.ifBlank { "En cola" } + " ${(task.progress * 100).toInt()}%"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (task.state == TaskState.ERROR) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2
                )
            }
            if (task.state == TaskState.EN_COLA || task.state == TaskState.DESCARGANDO || task.state == TaskState.CONVIRTIENDO) {
                IconButton(onClick = { DownloadQueue.cancel(task.id) }) { Icon(Icons.Default.Close, contentDescription = "Cancelar") }
            }
        }
        if (task.state != TaskState.LISTO && task.state != TaskState.ERROR) {
            LinearProgressIndicator(progress = { task.progress }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
        }
    }
}

@Composable
private fun EntryRow(e: DownloadEntry, highlighted: Boolean = false, onDelete: () -> Unit, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .background(if (highlighted) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.width(112.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
            EntryArt(e, Modifier.fillMaxSize())
            Icon(
                if (e.format == DlFormat.MP3) Icons.Default.MusicNote else Icons.Default.VideoLibrary,
                contentDescription = null, tint = Color.White,
                modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp).size(18.dp)
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(e.title, maxLines = 2, overflow = TextOverflow.Ellipsis,
                fontWeight = if (highlighted) FontWeight.Bold else FontWeight.Normal)
            Text(
                listOf(e.author, e.source.label, e.quality, formatBytes(e.sizeBytes)).filter { it.isNotBlank() }.joinToString(" • "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2
            )
        }
        IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, contentDescription = "Borrar") }
    }
}
