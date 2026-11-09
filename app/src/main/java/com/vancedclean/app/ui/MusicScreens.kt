package com.vancedclean.app.ui

import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.media3.common.Player
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.vancedclean.app.download.DownloadEntry
import com.vancedclean.app.download.DownloadStore
import com.vancedclean.app.download.MediaLibrary
import com.vancedclean.app.download.MusicLists
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import com.vancedclean.app.download.formatBytes
import com.vancedclean.app.navigation.Screen
import com.vancedclean.app.player.VideoPlayerManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

private enum class SongSort(val label: String) { RECIENTES("Recientes"), TITULO("Título"), ARTISTA("Artista") }

/** Reproductor de música: solo los MP3 de YouTube descargados con Zaza (Music/Zaza). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val entries by DownloadStore.entries.collectAsState()
    val current by VideoPlayerManager.localId.collectAsState()
    var query by remember { mutableStateOf("") }
    var sort by remember { mutableStateOf(SongSort.RECIENTES) }
    var toDelete by remember { mutableStateOf<DownloadEntry?>(null) }
    var tab by remember { mutableStateOf(0) }
    val favorites by MusicLists.favorites.collectAsState()

    // Permiso de lectura: hace falta para volver a encontrar las canciones tras reinstalar la app.
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        scope.launch(Dispatchers.IO) { DownloadStore.refresh(context); MusicLists.reimportIfEmpty(context) }
    }
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) { DownloadStore.load(context); MusicLists.load(context) }
        val missing = MediaLibrary.readPermissions().filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) permissions.launch(missing.toTypedArray())
    }

    val songs = entries.filter { it.isMusic }
    val shown = songs
        .filter { tab != 1 || it.uri in favorites }
        .filter { query.isBlank() || it.title.contains(query, true) || it.author.contains(query, true) }
        .let { list ->
            when (sort) {
                SongSort.RECIENTES -> list.sortedByDescending { it.createdAt }
                SongSort.TITULO -> list.sortedBy { it.title.lowercase(Locale.ROOT) }
                SongSort.ARTISTA -> list.sortedWith(compareBy({ it.author.lowercase(Locale.ROOT) }, { it.title.lowercase(Locale.ROOT) }))
            }
        }
    val last = remember(songs.size) { VideoPlayerManager.lastPlayed(context) }
    val lastSong = last?.let { (id, _) -> songs.firstOrNull { it.id == id } }

    toDelete?.let { DeleteEntryDialog(it) { toDelete = null } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Reproductor de música", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                }
            )
        },
        bottomBar = { MusicMiniBar { navController.navigate(Screen.NowPlaying.route) } }
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 16.dp)) {
            item {
                TabRow(selectedTabIndex = tab) {
                    Tab(tab == 0, { tab = 0 }, text = { Text("Todas") })
                    Tab(tab == 1, { tab = 1 }, text = { Text("Favoritas (${favorites.size})") })
                    Tab(tab == 2, { tab = 2 }, text = { Text("Listas") })
                }
            }
            if (tab == 2) {
                item { MusicListsTab(navController) }
                return@LazyColumn
            }
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Buscar canción o artista") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Default.Close, "Borrar") }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
            item {
                Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(enabled = shown.isNotEmpty(), onClick = {
                        VideoPlayerManager.playLocal(context, shown)
                        navController.navigate(Screen.NowPlaying.route)
                    }) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Reproducir todo")
                    }
                    FilledTonalButton(enabled = shown.isNotEmpty(), onClick = {
                        VideoPlayerManager.playLocal(context, shown, startIndex = shown.indices.random(), shuffle = true)
                        navController.navigate(Screen.NowPlaying.route)
                    }) {
                        Icon(Icons.Default.Shuffle, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Aleatorio")
                    }
                }
            }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(SongSort.entries) { s -> FilterChip(sort == s, { sort = s }, label = { Text(s.label) }) }
                }
            }
            if (lastSong != null && current == null) {
                item {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).clickable {
                            VideoPlayerManager.resumeLocal(context, songs, lastSong.id, last.second)
                            navController.navigate(Screen.NowPlaying.route)
                        }
                    ) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Continuar donde lo dejaste", style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary)
                                Text("${lastSong.title} · ${formatTime(last.second)}", maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
            item {
                Text(
                    "${songs.size} canciones" + (if (query.isNotBlank()) " · ${shown.size} encontradas" else "") + " · Funciona sin internet",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                )
            }
            if (songs.isEmpty()) {
                item {
                    Text(
                        "Descarga canciones en MP3 desde YouTube (botón Descargar de un video) y aparecerán aquí.\n" +
                            "Se guardan en la carpeta Música/Zaza: si reinstalas la app, vuelven a aparecer.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(32.dp)
                    )
                }
            }
            itemsIndexed(shown, key = { _, s -> s.id }) { index, song ->
                SongRow(song, highlighted = song.id == current, actions = { SongActions(song, onDelete = { toDelete = song }) }) {
                    VideoPlayerManager.playLocal(context, shown, startIndex = index)
                }
            }
        }
    }
}

/** Pantalla "Reproduciendo": portada, barra para adelantar, aleatorio, repetir y temporizador. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingScreen(navController: NavController) {
    val context = LocalContext.current
    val queue by VideoPlayerManager.queue.collectAsState()
    val currentId by VideoPlayerManager.localId.collectAsState()
    val playing by VideoPlayerManager.isPlaying.collectAsState()
    val position by VideoPlayerManager.currentPosition.collectAsState()
    val duration by VideoPlayerManager.duration.collectAsState()
    val shuffle by VideoPlayerManager.shuffle.collectAsState()
    val repeat by VideoPlayerManager.repeatMode.collectAsState()
    val sleepAt by VideoPlayerManager.sleepAt.collectAsState()
    val song = queue.firstOrNull { it.id == currentId }
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }
    var sleepMenu by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Reproduciendo") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                actions = {
                    song?.let { s ->
                        val favs by MusicLists.favorites.collectAsState()
                        val fav = s.uri in favs
                        IconButton(onClick = { MusicLists.toggleFavorite(context, s.uri) }) {
                            Icon(if (fav) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                if (fav) "Quitar de favoritas" else "Agregar a favoritas",
                                tint = if (fav) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                        }
                    }
                    Box {
                        IconButton(onClick = { sleepMenu = true }) {
                            Icon(Icons.Default.Bedtime, contentDescription = "Temporizador",
                                tint = if (sleepAt != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                        }
                        DropdownMenu(expanded = sleepMenu, onDismissRequest = { sleepMenu = false }) {
                            listOf(15, 30, 45, 60).forEach { m ->
                                DropdownMenuItem(text = { Text("Apagar en $m min") }, onClick = {
                                    VideoPlayerManager.setSleepTimer(m); sleepMenu = false
                                })
                            }
                            DropdownMenuItem(text = { Text("Al terminar la canción") }, onClick = {
                                VideoPlayerManager.setSleepTimer(-1); sleepMenu = false
                            })
                            if (sleepAt != null) {
                                DropdownMenuItem(text = { Text("Quitar temporizador") }, onClick = {
                                    VideoPlayerManager.setSleepTimer(0); sleepMenu = false
                                })
                            }
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (song == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("No hay música sonando", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@Scaffold
        }
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                Column(Modifier.padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    EntryArt(song, Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(16.dp)))
                    Spacer(Modifier.height(20.dp))
                    Text(song.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(song.author, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    sleepAt?.let { at ->
                        Text(
                            if (at < 0) "Se apagará al terminar la canción"
                            else "Se apagará a las " + java.text.SimpleDateFormat("HH:mm", Locale.getDefault()).format(java.util.Date(at)),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Slider(
                        value = if (dragging) dragValue else if (duration > 0) position.toFloat() / duration else 0f,
                        onValueChange = { dragging = true; dragValue = it },
                        onValueChangeFinished = {
                            VideoPlayerManager.seekTo((dragValue * duration).toLong())
                            dragging = false
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(Modifier.fillMaxWidth()) {
                        Text(formatTime(if (dragging) (dragValue * duration).toLong() else position), style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.weight(1f))
                        Text(formatTime(duration), style = MaterialTheme.typography.bodySmall)
                    }
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { VideoPlayerManager.toggleShuffle() }) {
                            Icon(Icons.Default.Shuffle, "Aleatorio",
                                tint = if (shuffle) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { VideoPlayerManager.previous() }, modifier = Modifier.size(56.dp)) {
                            Icon(Icons.Default.SkipPrevious, "Anterior", modifier = Modifier.size(36.dp))
                        }
                        Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(72.dp).clickable { VideoPlayerManager.togglePlayPause() }) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    if (playing) "Pausar" else "Reproducir", tint = Color.White, modifier = Modifier.size(40.dp))
                            }
                        }
                        IconButton(onClick = { VideoPlayerManager.next() }, modifier = Modifier.size(56.dp)) {
                            Icon(Icons.Default.SkipNext, "Siguiente", modifier = Modifier.size(36.dp))
                        }
                        IconButton(onClick = { VideoPlayerManager.cycleRepeat() }) {
                            Icon(
                                if (repeat == Player.REPEAT_MODE_ONE) Icons.Default.RepeatOne else Icons.Default.Repeat,
                                when (repeat) { Player.REPEAT_MODE_ONE -> "Repetir una"; Player.REPEAT_MODE_ALL -> "Repetir todas"; else -> "Sin repetir" },
                                tint = if (repeat != Player.REPEAT_MODE_OFF) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            item {
                Text("Lista (${queue.size})", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp))
            }
            itemsIndexed(queue, key = { _, s -> "q-" + s.id }) { index, s ->
                SongRow(s, highlighted = s.id == currentId) { VideoPlayerManager.playQueueIndex(index) }
            }
        }
    }
}

/** Barra fija de la música que suena (se toca para abrir "Reproduciendo"). */
@Composable
fun MusicMiniBar(onOpen: () -> Unit) {
    val local by VideoPlayerManager.localId.collectAsState()
    val now by VideoPlayerManager.nowPlaying.collectAsState()
    val playing by VideoPlayerManager.isPlaying.collectAsState()
    val position by VideoPlayerManager.currentPosition.collectAsState()
    val duration by VideoPlayerManager.duration.collectAsState()
    val item = now ?: return
    if (local == null) return
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.clickable(onClick = onOpen)) {
        Column {
            LinearProgressIndicator(
                progress = { if (duration > 0) position.toFloat() / duration else 0f },
                modifier = Modifier.fillMaxWidth().height(2.dp)
            )
            Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.MusicNote, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(item.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                    Text(item.channelName, maxLines = 1, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { VideoPlayerManager.previous() }) { Icon(Icons.Default.SkipPrevious, "Anterior") }
                IconButton(onClick = { VideoPlayerManager.togglePlayPause() }) {
                    Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, if (playing) "Pausar" else "Reproducir")
                }
                IconButton(onClick = { VideoPlayerManager.next() }) { Icon(Icons.Default.SkipNext, "Siguiente") }
            }
        }
    }
}

@Composable
fun SongRow(song: DownloadEntry, highlighted: Boolean, actions: (@Composable () -> Unit)? = null, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .background(if (highlighted) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        EntryArt(song, Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(song.title, maxLines = 2, overflow = TextOverflow.Ellipsis,
                fontWeight = if (highlighted) FontWeight.Bold else FontWeight.Normal)
            Text(
                listOf(song.author, formatBytes(song.sizeBytes)).filter { it.isNotBlank() }.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1
            )
        }
        actions?.invoke()
    }
}

/** Miniatura de internet o, sin ella (tras reinstalar), la portada incrustada en el MP3. */
@Composable
fun EntryArt(entry: DownloadEntry, modifier: Modifier) {
    val context = LocalContext.current
    Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
        if (entry.thumbnailUrl != null) {
            AsyncImage(model = entry.thumbnailUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            val art by produceState<android.graphics.Bitmap?>(null, entry.uri) {
                value = withContext(Dispatchers.IO) {
                    entry.uri.takeIf { it.isNotBlank() }?.let { MediaLibrary.embeddedArt(context, it) }
                        ?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
                }
            }
            art?.let { Image(it.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
                ?: Icon(Icons.Default.MusicNote, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Confirmación para borrar; si el archivo es de una instalación anterior, Android pide permiso. */
@Composable
fun DeleteEntryDialog(entry: DownloadEntry, onDone: () -> Unit) {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { r ->
        if (r.resultCode == android.app.Activity.RESULT_OK) {
            DownloadStore.forget(context, entry.id); MusicLists.forgetSong(context, entry.uri)
        }
        onDone()
    }
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text("Borrar descarga") },
        text = { Text("Se borrará \"${entry.title}\" de este teléfono.") },
        confirmButton = {
            TextButton(onClick = {
                val ask = runCatching {
                    if (entry.uri.isNotBlank()) MediaLibrary.delete(context, entry.uri)
                    else { java.io.File(entry.path).delete(); null }
                }.getOrNull()
                if (ask != null) launcher.launch(IntentSenderRequest.Builder(ask).build())
                else { DownloadStore.forget(context, entry.id); MusicLists.forgetSong(context, entry.uri); onDone() }
            }) { Text("Borrar") }
        },
        dismissButton = { TextButton(onClick = onDone) { Text("Cancelar") } }
    )
}

fun formatTime(ms: Long): String {
    if (ms <= 0) return "0:00"
    val s = ms / 1000
    return if (s >= 3600) String.format(Locale.US, "%d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60)
    else String.format(Locale.US, "%d:%02d", s / 60, s % 60)
}
