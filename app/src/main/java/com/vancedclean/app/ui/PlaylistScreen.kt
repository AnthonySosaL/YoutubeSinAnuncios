package com.vancedclean.app.ui

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.vancedclean.app.data.YouTubeAccount
import com.vancedclean.app.data.YouTubeAuthApi
import com.vancedclean.app.navigation.Screen
import com.vancedclean.app.player.PlayerSheet
import com.vancedclean.app.ui.components.AppBottomSection
import com.vancedclean.app.ui.components.VideoCard
import com.vancedclean.app.ui.components.accountAction
import com.vancedclean.app.ui.components.videoIdOf
import com.vancedclean.app.viewmodel.VideoItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class PlaylistUiState(
    val isLoading: Boolean = true,
    val videos: List<VideoItem> = emptyList(),
    val error: String? = null,
)

class PlaylistViewModel(application: Application, handle: SavedStateHandle) : AndroidViewModel(application) {
    val playlistId: String = handle.get<String>(Screen.Playlist.ARG_ID).orEmpty()
    val title: String = handle.get<String>(Screen.Playlist.ARG_TITLE).orEmpty()

    private val _state = MutableStateFlow(PlaylistUiState())
    val state = _state.asStateFlow()

    init { load() }

    fun load() {
        _state.value = PlaylistUiState()
        viewModelScope.launch(Dispatchers.IO) {
            _state.value = try {
                val videos = YouTubeAuthApi.fetchPlaylist(getApplication(), playlistId).map {
                    VideoItem(it.url, it.title, it.thumbnailUrl, it.channelName, it.channelAvatarUrl, -1, it.durationText ?: "")
                }
                PlaylistUiState(isLoading = false, videos = videos)
            } catch (e: Exception) {
                PlaylistUiState(isLoading = false, error = e.message ?: "Error cargando la playlist")
            }
        }
    }

    fun removeLocally(url: String) {
        _state.value = _state.value.copy(videos = _state.value.videos.filterNot { it.url == url })
    }
}

/** Contenido de una playlist (propia, Ver más tarde, Me gusta o de un canal). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistScreen(navController: NavController, viewModel: PlaylistViewModel = viewModel()) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val canEdit = YouTubeAccount.isLoggedIn(context)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(viewModel.title.ifBlank { "Playlist" }, maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                actions = {
                    if (state.videos.isNotEmpty()) {
                        IconButton(onClick = { PlayerSheet.open(state.videos.first().url) }) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "Reproducir todo")
                        }
                    }
                }
            )
        },
        bottomBar = { AppBottomSection(navController = navController, currentRoute = null, showNavBar = false) }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                state.isLoading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                state.error != null -> Column(
                    Modifier.align(Alignment.Center).padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(state.error.orEmpty())
                    Button(onClick = viewModel::load, modifier = Modifier.padding(top = 12.dp)) { Text("Reintentar") }
                }
                state.videos.isEmpty() -> Text(
                    "Esta playlist está vacía",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Center)
                )
                else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 8.dp)) {
                    items(state.videos, key = { it.url }) { video ->
                        VideoCard(
                            video = video,
                            onClick = { PlayerSheet.open(video.url) },
                            onRemove = if (!canEdit) null else {
                                {
                                    val id = videoIdOf(video.url)
                                    if (id != null) {
                                        scope.accountAction(
                                            context, "Quitado de la playlist",
                                            onDone = { ok -> if (ok) viewModel.removeLocally(video.url) }
                                        ) { YouTubeAccount.removeFromPlaylist(it, viewModel.playlistId, id) }
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}
