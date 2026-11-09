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

data class SubscriptionsUiState(
    val isLoading: Boolean = false,
    val videos: List<VideoItem> = emptyList(),
    val error: String? = null,
)

class SubscriptionsViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(SubscriptionsUiState())
    val uiState: StateFlow<SubscriptionsUiState> = _uiState.asStateFlow()

    private val _channels = MutableStateFlow<List<com.vancedclean.app.data.ChannelSummary>>(emptyList())
    /** Canales a los que está suscrita la cuenta. */
    val channels: StateFlow<List<com.vancedclean.app.data.ChannelSummary>> = _channels.asStateFlow()

    fun loadFeed() {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { com.vancedclean.app.data.YouTubeAccount.subscribedChannels(getApplication()) }
                .onSuccess { _channels.value = it }
        }
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.value = SubscriptionsUiState(isLoading = true)
            try {
                val results = YouTubeAuthApi.fetchSubscriptions(getApplication())
                if (results.isEmpty()) {
                    _uiState.value = SubscriptionsUiState(
                        error = "No se encontraron videos. Verifica tu sesión."
                    )
                } else {
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
                    _uiState.value = SubscriptionsUiState(videos = videos)
                }
            } catch (e: Exception) {
                _uiState.value = SubscriptionsUiState(
                    error = e.message ?: "Error cargando suscripciones"
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
