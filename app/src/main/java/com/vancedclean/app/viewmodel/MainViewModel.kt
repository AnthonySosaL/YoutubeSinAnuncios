package com.vancedclean.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.services.youtube.YoutubeService
import org.schabi.newpipe.extractor.stream.StreamInfoItem

data class VideoItem(
    val url: String,
    val title: String,
    val thumbnailUrl: String,
    val uploaderName: String,
    val uploaderAvatarUrl: String?,
    val viewCount: Long,
    val duration: String,
)

data class MainUiState(
    val isLoading: Boolean = false,
    val videos: List<VideoItem> = emptyList(),
    val error: String? = null,
)

class MainViewModel(application: android.app.Application) : androidx.lifecycle.AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    init {
        loadTrending()
    }

    fun loadTrending() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = MainUiState(isLoading = true)
            // Con sesión: el inicio personalizado de la cuenta (como en la app oficial).
            val app = getApplication<android.app.Application>()
            if (com.vancedclean.app.data.CookieStorage.isLoggedIn(app)) {
                val home = runCatching { com.vancedclean.app.data.YouTubeAuthApi.fetchHome(app) }.getOrDefault(emptyList())
                if (home.isNotEmpty()) {
                    _uiState.value = MainUiState(videos = home.map { r ->
                        VideoItem(
                            url = r.url,
                            title = r.title,
                            thumbnailUrl = r.thumbnailUrl,
                            uploaderName = r.channelName,
                            uploaderAvatarUrl = r.channelAvatarUrl,
                            viewCount = r.viewCountText?.let { parseViews(it) } ?: -1,
                            duration = r.durationText ?: "",
                        )
                    })
                    return@launch
                }
            }
            try {
                com.vancedclean.app.data.NewPipeSetup.ensure()
                val service = NewPipe.getService(ServiceList.YouTube.serviceId)
                val kioskList = service.kioskList
                val extractor = kioskList.getDefaultKioskExtractor(null)
                extractor.fetchPage()

                val videos = extractor.initialPage.items
                    .filterIsInstance<StreamInfoItem>()
                    .map { item ->
                        VideoItem(
                            url = item.url,
                            title = item.name,
                            thumbnailUrl = item.thumbnails.firstOrNull()?.url ?: "",
                            uploaderName = item.uploaderName ?: "",
                            uploaderAvatarUrl = item.uploaderAvatars.firstOrNull()?.url,
                            viewCount = item.viewCount,
                            duration = if (item.streamType.name == "LIVE_STREAM") "EN VIVO" else formatDuration(item.duration),
                        )
                    }

                _uiState.value = MainUiState(videos = videos)
            } catch (e: Exception) {
                _uiState.value = MainUiState(error = e.message ?: "Error cargando tendencias")
            }
        }
    }

    fun search(query: String) {
        if (query.isBlank()) return

        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = MainUiState(isLoading = true)
            try {
                val service = NewPipe.getService(ServiceList.YouTube.serviceId)
                val extractor = service.getSearchExtractor(query)
                extractor.fetchPage()

                val videos = extractor.initialPage.items
                    .filterIsInstance<StreamInfoItem>()
                    .map { item ->
                        VideoItem(
                            url = item.url,
                            title = item.name,
                            thumbnailUrl = item.thumbnails.firstOrNull()?.url ?: "",
                            uploaderName = item.uploaderName ?: "",
                            uploaderAvatarUrl = item.uploaderAvatars.firstOrNull()?.url,
                            viewCount = item.viewCount,
                            duration = if (item.streamType.name == "LIVE_STREAM") "EN VIVO" else formatDuration(item.duration),
                        )
                    }

                _uiState.value = MainUiState(videos = videos)
            } catch (e: Exception) {
                _uiState.value = MainUiState(error = e.message ?: "Error en búsqueda")
            }
        }
    }

    /** "1,2 M de visualizaciones" → 1200000 (aprox.); -1 si no se entiende. */
    private fun parseViews(text: String): Long {
        val t = text.lowercase()
        val match = Regex("""(\d+(?:[.,]\d+)?)\s*(mm|m|k|mil)?\b""").find(t) ?: return -1
        val (numText, unit) = match.destructured
        val mult = when (unit) {
            "mm" -> 1_000_000_000.0
            "m" -> 1_000_000.0
            "k", "mil" -> 1_000.0
            else -> return t.filter { it.isDigit() }.toLongOrNull() ?: -1
        }
        val num = numText.replace(",", ".").toDoubleOrNull() ?: return -1
        return (num * mult).toLong()
    }

    private fun formatDuration(seconds: Long): String {
        if (seconds <= 0) return ""
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return if (h > 0) String.format("%d:%02d:%02d", h, m, s)
        else String.format("%d:%02d", m, s)
    }
}
