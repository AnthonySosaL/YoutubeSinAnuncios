package com.vancedclean.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.WatchLater
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.ui.platform.LocalContext
import com.vancedclean.app.data.PlaylistSummary
import com.vancedclean.app.data.YouTubeAccount
import com.vancedclean.app.ui.components.PlaylistRow
import com.vancedclean.app.ui.components.accountAction
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.vancedclean.app.player.PlayerSheet
import com.vancedclean.app.navigation.Screen
import com.vancedclean.app.ui.components.AppBottomSection
import com.vancedclean.app.ui.components.VideoCard
import com.vancedclean.app.viewmodel.LibraryViewModel
import com.vancedclean.app.viewmodel.LoginViewModel

private enum class LibrarySection(
    val label: String,
    val icon: ImageVector,
    val playlistId: String
) {
    HISTORY("Historial", Icons.Default.History, "https://www.youtube.com/feed/history"),
    LIKED("Videos que me gustan", Icons.Default.ThumbUp, "https://www.youtube.com/playlist?list=LL"),
    WATCH_LATER("Ver más tarde", Icons.Default.WatchLater, "https://www.youtube.com/playlist?list=WL"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    navController: NavController,
    viewModel: LibraryViewModel = viewModel(),
    loginViewModel: LoginViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val isLoggedIn by loginViewModel.isLoggedIn.collectAsState()
    var selectedSection by remember { mutableStateOf<LibrarySection?>(null) }
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    BackHandler(enabled = selectedSection != null) {
        selectedSection = null
    }

    val playlists by viewModel.playlists.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var toDelete by remember { mutableStateOf<PlaylistSummary?>(null) }
    LaunchedEffect(isLoggedIn) { if (isLoggedIn) viewModel.loadPlaylists() }

    toDelete?.let { playlist ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Eliminar playlist") },
            text = { Text("Se eliminará \"${playlist.title}\" de tu cuenta de YouTube. No se puede deshacer.") },
            confirmButton = {
                TextButton(onClick = {
                    toDelete = null
                    scope.accountAction(context, "Playlist eliminada", onDone = { ok ->
                        if (ok) viewModel.removePlaylistLocally(playlist.id)
                    }) { YouTubeAccount.deletePlaylist(it, playlist.id) }
                }) { Text("Eliminar") }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Cancelar") } }
        )
    }

    LaunchedEffect(isLoggedIn, selectedSection) {
        if (isLoggedIn && selectedSection != null) {
            viewModel.loadPlaylist(selectedSection!!.playlistId)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        selectedSection?.label ?: "Biblioteca",
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            AppBottomSection(navController = navController, currentRoute = currentRoute)
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                !isLoggedIn -> {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.VideoLibrary,
                            contentDescription = null,
                            modifier = Modifier.size(80.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "Inicia sesión para ver tu biblioteca",
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { navController.navigate(Screen.Login.route) }) {
                            Text("Iniciar sesión")
                        }
                    }
                }
                selectedSection == null -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        items(LibrarySection.entries.toList()) { section ->
                            LibrarySectionRow(
                                icon = section.icon,
                                label = section.label,
                                onClick = { selectedSection = section }
                            )
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant
                            )
                        }
                        item {
                            Text(
                                "Tus playlists",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 8.dp)
                            )
                        }
                        when {
                            playlists == null -> item {
                                CircularProgressIndicator(Modifier.padding(16.dp).size(28.dp))
                            }
                            playlists!!.isEmpty() -> item {
                                Text(
                                    "Todavía no tienes playlists. Créalas desde \"Guardar\" en un video.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 16.dp)
                                )
                            }
                            else -> items(playlists!!, key = { it.id }) { playlist ->
                                PlaylistRow(
                                    playlist = playlist,
                                    onClick = {
                                        navController.navigate(Screen.Playlist.createRoute(playlist.id, playlist.title))
                                    },
                                    onMore = { toDelete = playlist }
                                )
                            }
                        }
                    }
                }
                uiState.isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                uiState.error != null -> {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.ErrorOutline,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            uiState.error ?: "Error",
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = {
                            selectedSection?.let { viewModel.loadPlaylist(it.playlistId) }
                        }) {
                            Text("Reintentar")
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = { selectedSection = null }) {
                            Text("Volver")
                        }
                    }
                }
                else -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedSection = null }
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                selectedSection!!.icon,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                selectedSection!!.label,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

                        if (uiState.videos.isEmpty()) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "No hay videos",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(bottom = 8.dp)
                            ) {
                                items(uiState.videos) { video ->
                                    VideoCard(
                                        video = video,
                                        onClick = {
                                            PlayerSheet.open(video.url)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LibrarySectionRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(28.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}
