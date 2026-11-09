package com.vancedclean.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.vancedclean.app.download.DownloadEntry
import com.vancedclean.app.download.DownloadStore
import com.vancedclean.app.download.MusicList
import com.vancedclean.app.download.MusicLists
import com.vancedclean.app.navigation.Screen
import com.vancedclean.app.player.VideoPlayerManager
import com.vancedclean.app.ui.components.toast

/** Corazón de favorita + menú ⋮ (agregar a lista, quitar de esta lista, borrar). */
@Composable
fun SongActions(song: DownloadEntry, onDelete: (() -> Unit)?, listId: String? = null) {
    val context = LocalContext.current
    val favorites by MusicLists.favorites.collectAsState()
    var menu by remember { mutableStateOf(false) }
    var addToList by remember { mutableStateOf(false) }
    if (addToList) AddToListDialog(song) { addToList = false }

    val fav = song.uri in favorites
    IconButton(onClick = { MusicLists.toggleFavorite(context, song.uri) }) {
        Icon(
            if (fav) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
            contentDescription = if (fav) "Quitar de favoritas" else "Agregar a favoritas",
            tint = if (fav) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    Box {
        IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, contentDescription = "Más opciones") }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(
                text = { Text("Agregar a una lista") },
                leadingIcon = { Icon(Icons.AutoMirrored.Filled.PlaylistAdd, null) },
                onClick = { menu = false; addToList = true }
            )
            if (listId != null) {
                DropdownMenuItem(
                    text = { Text("Quitar de esta lista") },
                    leadingIcon = { Icon(Icons.Default.RemoveCircleOutline, null) },
                    onClick = { menu = false; MusicLists.remove(context, listId, song.uri); toast(context, "Quitada de la lista") }
                )
            }
            if (onDelete != null) {
                DropdownMenuItem(
                    text = { Text("Borrar del teléfono") },
                    leadingIcon = { Icon(Icons.Default.Delete, null) },
                    onClick = { menu = false; onDelete() }
                )
            }
        }
    }
}

/** Elegir en qué listas va la canción, o crear una nueva. */
@Composable
fun AddToListDialog(song: DownloadEntry, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val lists by MusicLists.lists.collectAsState()
    var creating by remember { mutableStateOf(lists.isEmpty()) }
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (creating) "Nueva lista" else "Agregar a…") },
        text = {
            if (creating) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Nombre de la lista") },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
            } else {
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items(lists, key = { it.id }) { l ->
                        val inside = song.uri in l.songs
                        ListItem(
                            headlineContent = { Text(l.name) },
                            supportingContent = { Text("${l.songs.size} canciones") },
                            leadingContent = { Icon(Icons.AutoMirrored.Filled.QueueMusic, null) },
                            trailingContent = { if (inside) Icon(Icons.Default.Check, "Ya está", tint = MaterialTheme.colorScheme.primary) },
                            modifier = Modifier.clickable {
                                if (inside) MusicLists.remove(context, l.id, song.uri) else MusicLists.add(context, l.id, song.uri)
                            }
                        )
                    }
                    item {
                        ListItem(
                            headlineContent = { Text("Crear lista nueva") },
                            leadingContent = { Icon(Icons.Default.Add, null) },
                            modifier = Modifier.clickable { creating = true }
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (creating) {
                TextButton(enabled = name.isNotBlank(), onClick = {
                    val l = MusicLists.create(context, name, song.uri)
                    toast(context, "Agregada a \"${l.name}\"")
                    onDismiss()
                }) { Text("Crear") }
            } else TextButton(onClick = onDismiss) { Text("Listo") }
        },
        dismissButton = { if (creating && lists.isNotEmpty()) TextButton(onClick = { creating = false }) { Text("Atrás") } }
    )
}

/** Pestaña "Listas" del reproductor: listas propias y botón para crear. */
@Composable
fun MusicListsTab(navController: NavController) {
    val context = LocalContext.current
    val lists by MusicLists.lists.collectAsState()
    val favorites by MusicLists.favorites.collectAsState()
    var creating by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }

    if (creating) {
        AlertDialog(
            onDismissRequest = { creating = false },
            title = { Text("Nueva lista") },
            text = {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Nombre de la lista") },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
            },
            confirmButton = {
                TextButton(enabled = name.isNotBlank(), onClick = {
                    val l = MusicLists.create(context, name)
                    creating = false; name = ""
                    navController.navigate(Screen.MusicList.createRoute(l.id))
                }) { Text("Crear") }
            },
            dismissButton = { TextButton(onClick = { creating = false }) { Text("Cancelar") } }
        )
    }

    Column {
        Button(onClick = { creating = true }, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Icon(Icons.Default.Add, null)
            Spacer(Modifier.width(6.dp))
            Text("Nueva lista")
        }
        ListRow(MusicLists.FAVORITES_NAME, favorites.size, Icons.Default.Favorite) {
            navController.navigate(Screen.MusicList.createRoute(FAVORITES_ID))
        }
        lists.forEach { l ->
            ListRow(l.name, l.songs.size, Icons.AutoMirrored.Filled.QueueMusic) {
                navController.navigate(Screen.MusicList.createRoute(l.id))
            }
        }
        if (lists.isEmpty()) {
            Text(
                "Crea listas con tus canciones: toca ⋮ en una canción → \"Agregar a una lista\".",
                color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(24.dp)
            )
        }
    }
}

@Composable
private fun ListRow(name: String, count: Int, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(name, fontWeight = FontWeight.Medium) },
        supportingContent = { Text("$count canciones") },
        leadingContent = {
            Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                modifier = Modifier.size(48.dp)) {
                Box(contentAlignment = Alignment.Center) { Icon(icon, null, tint = MaterialTheme.colorScheme.primary) }
            }
        },
        modifier = Modifier.clickable(onClick = onClick)
    )
}

const val FAVORITES_ID = "favoritas"

/** Una lista (o Favoritas): reproducir, aleatorio, renombrar, borrar y quitar canciones. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicListScreen(navController: NavController, id: String) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { DownloadStore.load(context); MusicLists.load(context) }
    val entries by DownloadStore.entries.collectAsState()
    val lists by MusicLists.lists.collectAsState()
    val favorites by MusicLists.favorites.collectAsState()
    val current by VideoPlayerManager.localId.collectAsState()
    val isFav = id == FAVORITES_ID
    val list: MusicList? = if (isFav) null else lists.firstOrNull { it.id == id }
    val title = if (isFav) MusicLists.FAVORITES_NAME else list?.name.orEmpty()
    val byUri = entries.associateBy { it.uri }
    val songs = (if (isFav) entries.filter { it.uri in favorites } else list?.songs?.mapNotNull { byUri[it] }.orEmpty())
    var menu by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var confirmDelete by remember { mutableStateOf(false) }
    var toDelete by remember { mutableStateOf<DownloadEntry?>(null) }
    toDelete?.let { DeleteEntryDialog(it) { toDelete = null } }

    if (renaming && list != null) {
        AlertDialog(
            onDismissRequest = { renaming = false },
            title = { Text("Renombrar lista") },
            text = { OutlinedTextField(value = newName, onValueChange = { newName = it }, singleLine = true, modifier = Modifier.fillMaxWidth()) },
            confirmButton = { TextButton(onClick = { MusicLists.rename(context, list.id, newName); renaming = false }) { Text("Guardar") } },
            dismissButton = { TextButton(onClick = { renaming = false }) { Text("Cancelar") } }
        )
    }
    if (confirmDelete && list != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Borrar lista") },
            text = { Text("Se borrará la lista \"${list.name}\". Las canciones no se borran.") },
            confirmButton = { TextButton(onClick = { MusicLists.delete(context, list.id); confirmDelete = false; navController.popBackStack() }) { Text("Borrar") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") } }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver") }
                },
                actions = {
                    if (list != null) {
                        Box {
                            IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Opciones de la lista") }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                DropdownMenuItem(text = { Text("Renombrar") }, leadingIcon = { Icon(Icons.Default.Edit, null) },
                                    onClick = { menu = false; newName = list.name; renaming = true })
                                DropdownMenuItem(text = { Text("Borrar lista") }, leadingIcon = { Icon(Icons.Default.Delete, null) },
                                    onClick = { menu = false; confirmDelete = true })
                            }
                        }
                    }
                }
            )
        },
        bottomBar = { MusicMiniBar { navController.navigate(Screen.NowPlaying.route) } }
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 16.dp)) {
            item {
                Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(enabled = songs.isNotEmpty(), onClick = {
                        VideoPlayerManager.playLocal(context, songs); navController.navigate(Screen.NowPlaying.route)
                    }) { Icon(Icons.Default.PlayArrow, null); Spacer(Modifier.width(6.dp)); Text("Reproducir") }
                    FilledTonalButton(enabled = songs.isNotEmpty(), onClick = {
                        VideoPlayerManager.playLocal(context, songs, songs.indices.random(), shuffle = true)
                        navController.navigate(Screen.NowPlaying.route)
                    }) { Icon(Icons.Default.Shuffle, null); Spacer(Modifier.width(6.dp)); Text("Aleatorio") }
                }
                Text("${songs.size} canciones", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp))
            }
            if (songs.isEmpty()) {
                item {
                    Text(
                        if (isFav) "Toca el corazón ♡ de una canción para guardarla aquí."
                        else "Lista vacía. En el reproductor, toca ⋮ en una canción → \"Agregar a una lista\".",
                        color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(32.dp)
                    )
                }
            }
            itemsIndexed(songs, key = { _, s -> s.id }) { index, song ->
                SongRow(song, highlighted = song.id == current,
                    actions = { SongActions(song, onDelete = { toDelete = song }, listId = list?.id) }) {
                    VideoPlayerManager.playLocal(context, songs, startIndex = index)
                }
            }
        }
    }
}
