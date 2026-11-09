package com.vancedclean.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vancedclean.app.data.YouTubeAuthApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class LibraryUiState(
    val isLoading: Boolean = false,
    val videos: List<VideoItem> = emptyList(),
    val error: String? = null,
)

class LibraryViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    private val _playlists = MutableStateFlow<List<com.vancedclean.app.data.PlaylistSummary>?>(null)
    /** Playlists de la cuenta (null = cargando). Sin las automáticas "Me gusta" y "Ver más tarde". */
    val playlists: StateFlow<List<com.vancedclean.app.data.PlaylistSummary>?> = _playlists.asStateFlow()

    fun loadPlaylists() {
        viewModelScope.launch(Dispatchers.IO) {
            _playlists.value = runCatching {
                com.vancedclean.app.data.YouTubeAccount.libraryPlaylists(getApplication())
                    .filter { it.id != "LL" && it.id != "WL" }
            }.getOrDefault(emptyList())
        }
    }

    fun removePlaylistLocally(id: String) {
        _playlists.value = _playlists.value?.filterNot { it.id == id }
    }

    fun loadPlaylist(url: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = LibraryUiState(isLoading = true)
            try {
                val ctx: Application = getApplication()
                val results = when {
                    url.contains("/feed/history") -> YouTubeAuthApi.fetchHistory(ctx)
                    url.contains("list=LL") -> YouTubeAuthApi.fetchPlaylist(ctx, "LL")
                    url.contains("list=WL") -> YouTubeAuthApi.fetchPlaylist(ctx, "WL")
                    else -> emptyList()
                }

                val videos = results.map { r ->
                    VideoItem(
                        url = r.url,
                        title = r.title,
                        thumbnailUrl = r.thumbnailUrl,
                        uploaderName = r.channelName,
                        uploaderAvatarUrl = r.channelAvatarUrl,
                        viewCount = parseViewCount(r.viewCountText),
                        duration = r.durationText ?: "",
                    )
                }
                _uiState.value = LibraryUiState(videos = videos)
            } catch (e: Exception) {
                _uiState.value = LibraryUiState(
                    error = e.message ?: "Error cargando contenido"
                )
            }
        }
    }

    private fun parseViewCount(text: String?): Long {
        if (text == null) return -1
        val cleaned = text.replace(Regex("[^0-9]"), "")
        return cleaned.toLongOrNull() ?: -1
    }
}
