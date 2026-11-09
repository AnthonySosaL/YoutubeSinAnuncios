package com.vancedclean.app.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vancedclean.app.navigation.Screen
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.schabi.newpipe.extractor.Image
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.channel.ChannelInfo
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabInfo
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabs
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.util.Locale

data class ChannelDetail(
    val id: String,
    val name: String,
    val avatarUrl: String?,
    val bannerUrl: String?,
    val description: String,
    val subscriberCount: Long,
)

sealed class ChannelUiState {
    data object Loading : ChannelUiState()
    data class Success(val channel: ChannelDetail) : ChannelUiState()
    data class Error(val message: String) : ChannelUiState()
}

data class ChannelVideosState(
    val videos: List<VideoItem> = emptyList(),
    val playlists: List<com.vancedclean.app.data.PlaylistSummary> = emptyList(),
    val isLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = false,
    val error: String? = null,
)

class ChannelViewModel(
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val channelUrl: String =
        savedStateHandle.get<String>(Screen.Channel.ARG_CHANNEL_URL).orEmpty()

    private val _uiState = MutableStateFlow<ChannelUiState>(ChannelUiState.Loading)
    val uiState: StateFlow<ChannelUiState> = _uiState.asStateFlow()

    private val _videosState = MutableStateFlow(ChannelVideosState())
    val videosState: StateFlow<ChannelVideosState> = _videosState.asStateFlow()

    private var loadJob: Job? = null
    private var videosJob: Job? = null
    private var videosTab: ListLinkHandler? = null
    private var nextPage: Page? = null
    private var channelDetail: ChannelDetail? = null
    private var allTabs: List<ListLinkHandler> = emptyList()

    /** Pestaña activa: ChannelTabs.VIDEOS, SHORTS, LIVESTREAMS o PLAYLISTS. */
    private val _selectedTab = MutableStateFlow(ChannelTabs.VIDEOS)
    val selectedTab: StateFlow<String> = _selectedTab.asStateFlow()

    private val _subscribed = MutableStateFlow<Boolean?>(null)
    /** null = desconocido (sin sesión o cargando). */
    val subscribed: StateFlow<Boolean?> = _subscribed.asStateFlow()

    fun selectTab(filter: String) {
        if (filter == _selectedTab.value) return
        _selectedTab.value = filter
        val detail = channelDetail ?: return
        videosTab = allTabs.firstOrNull { it.contentFilters.contains(filter) }
        nextPage = null
        videosJob?.cancel()
        videosJob = viewModelScope.launch(Dispatchers.IO) { loadFirstVideos(detail) }
    }

    fun toggleSubscribe(context: android.content.Context, onMessage: (String) -> Unit) {
        val detail = channelDetail ?: return
        val app = context.applicationContext
        if (!com.vancedclean.app.data.YouTubeAccount.isLoggedIn(app)) {
            onMessage("Inicia sesión con Google para suscribirte")
            return
        }
        val target = _subscribed.value != true
        _subscribed.value = target
        viewModelScope.launch(Dispatchers.IO) {
            val result = runCatching { com.vancedclean.app.data.YouTubeAccount.setSubscribed(app, detail.id, target) }
            kotlinx.coroutines.withContext(Dispatchers.Main) {
                if (result.isSuccess) onMessage(if (target) "Suscripción añadida" else "Suscripción eliminada")
                else {
                    _subscribed.value = !target
                    onMessage(result.exceptionOrNull()?.message ?: "No se pudo cambiar la suscripción")
                }
            }
        }
    }

    fun loadSubscription(context: android.content.Context) {
        val detail = channelDetail ?: return
        val app = context.applicationContext
        if (!com.vancedclean.app.data.YouTubeAccount.isLoggedIn(app) || !detail.id.startsWith("UC")) return
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { com.vancedclean.app.data.YouTubeAccount.isSubscribed(app, detail.id) }
                .onSuccess { _subscribed.value = it }
        }
    }

    init {
        loadChannel()
    }

    fun retry() {
        loadChannel()
    }

    fun retryVideos() {
        val detail = channelDetail ?: return
        videosJob?.cancel()
        videosJob = viewModelScope.launch(Dispatchers.IO) {
            loadFirstVideos(detail)
        }
    }

    fun loadMore() {
        val tab = videosTab ?: return
        val page = nextPage ?: return
        val detail = channelDetail ?: return
        val current = _videosState.value
        if (current.isLoading || current.isLoadingMore) return

        _videosState.value = current.copy(isLoadingMore = true)
        videosJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val result = ChannelTabInfo.getMoreItems(NewPipe.getService(0), tab, page)
                nextPage = result.nextPage
                val known = _videosState.value.videos.map { it.url }.toSet()
                val newItems = result.items.toVideoItems(detail).filter { it.url !in known }
                _videosState.value = _videosState.value.copy(
                    videos = _videosState.value.videos + newItems,
                    playlists = (_videosState.value.playlists + result.items.toPlaylists()).distinctBy { it.id },
                    isLoadingMore = false,
                    hasMore = nextPage != null
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _videosState.value = _videosState.value.copy(isLoadingMore = false)
            }
        }
    }

    private fun loadChannel() {
        loadJob?.cancel()
        videosJob?.cancel()
        _uiState.value = ChannelUiState.Loading
        _videosState.value = ChannelVideosState()
        videosTab = null
        nextPage = null
        channelDetail = null

        loadJob = viewModelScope.launch(Dispatchers.IO) {
            if (channelUrl.isBlank()) {
                _uiState.value = ChannelUiState.Error("URL de canal no válida")
                return@launch
            }

            try {
                com.vancedclean.app.data.NewPipeSetup.ensure()

                val info = ChannelInfo.getInfo(NewPipe.getService(0), channelUrl)

                val detail = ChannelDetail(
                    id = info.id.orEmpty(),
                    name = info.name.orEmpty(),
                    avatarUrl = info.avatars.bestUrl(),
                    bannerUrl = info.banners.bestUrl(),
                    description = info.description.orEmpty(),
                    subscriberCount = info.subscriberCount,
                )

                channelDetail = detail
                allTabs = info.tabs
                videosTab = info.tabs.firstOrNull { it.contentFilters.contains(_selectedTab.value) }
                _uiState.value = ChannelUiState.Success(detail)

                loadFirstVideos(detail)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = ChannelUiState.Error(e.message ?: "Error cargando el canal")
            }
        }
    }

    private suspend fun loadFirstVideos(detail: ChannelDetail) {
        val tab = videosTab
        if (tab == null) {
            _videosState.value = ChannelVideosState(isLoading = false)
            return
        }

        _videosState.value = ChannelVideosState(isLoading = true)
        try {
            val tabInfo = ChannelTabInfo.getInfo(NewPipe.getService(0), tab)
            nextPage = tabInfo.nextPage
            _videosState.value = ChannelVideosState(
                videos = tabInfo.relatedItems.toVideoItems(detail),
                playlists = tabInfo.relatedItems.toPlaylists(),
                isLoading = false,
                hasMore = nextPage != null
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _videosState.value = ChannelVideosState(
                isLoading = false,
                error = e.message ?: "Error cargando los videos"
            )
        }
    }
}

private fun List<Image>.bestUrl(): String? =
    maxByOrNull { it.height }?.url?.takeIf { it.isNotBlank() }

private fun List<InfoItem>.toVideoItems(channel: ChannelDetail): List<VideoItem> =
    filterIsInstance<StreamInfoItem>()
        .map { item ->
            VideoItem(
                url = item.url,
                title = item.name,
                thumbnailUrl = item.thumbnails.firstOrNull()?.url ?: "",
                uploaderName = item.uploaderName?.takeIf { it.isNotBlank() } ?: channel.name,
                uploaderAvatarUrl = item.uploaderAvatars.firstOrNull()?.url ?: channel.avatarUrl,
                viewCount = item.viewCount,
                duration = if (item.streamType.name == "LIVE_STREAM") "EN VIVO" else formatChannelDuration(item.duration),
            )
        }
        .distinctBy { it.url }

private fun List<InfoItem>.toPlaylists(): List<com.vancedclean.app.data.PlaylistSummary> =
    filterIsInstance<org.schabi.newpipe.extractor.playlist.PlaylistInfoItem>().mapNotNull { item ->
        val id = Regex("""[?&]list=([\w-]+)""").find(item.url)?.groupValues?.get(1) ?: return@mapNotNull null
        com.vancedclean.app.data.PlaylistSummary(
            id = id,
            title = item.name.orEmpty(),
            thumbnailUrl = item.thumbnails.maxByOrNull { it.height }?.url,
            subtitle = if (item.streamCount >= 0) "${item.streamCount} videos" else ""
        )
    }

private fun formatChannelDuration(seconds: Long): String {
    if (seconds <= 0) return ""
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
    else String.format(Locale.US, "%d:%02d", m, s)
}

fun formatSubscriberCount(count: Long): String {
    if (count < 0) return ""
    return when {
        count >= 1_000_000_000 -> "${compactSubscribers(count, 1_000_000_000)} MM de suscriptores"
        count >= 1_000_000 -> "${compactSubscribers(count, 1_000_000)} M de suscriptores"
        count >= 1_000 -> "${compactSubscribers(count, 1_000)} K suscriptores"
        count == 1L -> "1 suscriptor"
        else -> "$count suscriptores"
    }
}

private fun compactSubscribers(count: Long, divisor: Long): String {
    val value = count.toDouble() / divisor
    return if (value >= 100) {
        value.toLong().toString()
    } else {
        String.format(Locale.forLanguageTag("es"), "%.1f", value).removeSuffix(",0")
    }
}
