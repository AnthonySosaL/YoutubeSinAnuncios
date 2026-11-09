package com.vancedclean.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.util.Locale

/** Resultado de búsqueda: video, canal o playlist. */
sealed class SearchResult {
    data class Video(val video: VideoItem) : SearchResult()
    data class Channel(val url: String, val name: String, val avatarUrl: String?, val subtitle: String) : SearchResult()
    data class Playlist(val playlist: com.vancedclean.app.data.PlaylistSummary) : SearchResult()
}

/** Filtros de búsqueda de YouTube (los mismos ids que usa NewPipe). */
enum class SearchFilter(val label: String, val id: String) {
    ALL("Todo", "all"), VIDEOS("Videos", "videos"), CHANNELS("Canales", "channels"), PLAYLISTS("Playlists", "playlists")
}

@OptIn(FlowPreview::class)
class SearchViewModel : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchResults = MutableStateFlow<List<SearchResult>>(emptyList())
    val searchResults: StateFlow<List<SearchResult>> = _searchResults.asStateFlow()

    private val _filter = MutableStateFlow(SearchFilter.ALL)
    val filter: StateFlow<SearchFilter> = _filter.asStateFlow()

    fun setFilter(value: SearchFilter) {
        if (value == _filter.value) return
        _filter.value = value
        lastSearchedQuery?.let { search(it) }
    }

    private val _searchSuggestions = MutableStateFlow<List<String>>(emptyList())
    val searchSuggestions: StateFlow<List<String>> = _searchSuggestions.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _hasSearched = MutableStateFlow(false)
    val hasSearched: StateFlow<Boolean> = _hasSearched.asStateFlow()

    private var searchJob: Job? = null
    private var lastSearchedQuery: String? = null

    init {
        com.vancedclean.app.data.NewPipeSetup.ensure()
        observeQuery()
    }

    fun onQueryChange(query: String) {
        if (query.trim() != lastSearchedQuery) {
            lastSearchedQuery = null
        }
        _searchQuery.value = query
        if (query.isBlank()) {
            _searchSuggestions.value = emptyList()
        }
    }

    fun clearQuery() {
        lastSearchedQuery = null
        _searchQuery.value = ""
        _searchSuggestions.value = emptyList()
    }

    fun search(query: String) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return

        _searchQuery.value = trimmed
        lastSearchedQuery = trimmed
        _searchSuggestions.value = emptyList()

        searchJob?.cancel()
        searchJob = viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            _error.value = null
            _hasSearched.value = true
            _searchResults.value = emptyList()
            try {
                val filterId = _filter.value.id
                val extractor = if (filterId == "all") NewPipe.getService(0).getSearchExtractor(trimmed)
                else NewPipe.getService(0).getSearchExtractor(trimmed, listOf(filterId), "")
                extractor.fetchPage()

                _searchResults.value = extractor.initialPage.items.mapNotNull { item ->
                    when (item) {
                        is StreamInfoItem -> SearchResult.Video(
                            VideoItem(
                                url = item.url,
                                title = item.name,
                                thumbnailUrl = item.thumbnails.firstOrNull()?.url ?: "",
                                uploaderName = item.uploaderName ?: "",
                                uploaderAvatarUrl = item.uploaderAvatars.firstOrNull()?.url,
                                viewCount = item.viewCount,
                                duration = if (item.streamType.name == "LIVE_STREAM") "EN VIVO" else formatDuration(item.duration),
                            )
                        )
                        is org.schabi.newpipe.extractor.channel.ChannelInfoItem -> SearchResult.Channel(
                            url = item.url,
                            name = item.name.orEmpty(),
                            avatarUrl = item.thumbnails.maxByOrNull { it.height }?.url,
                            subtitle = formatSubscriberCount(item.subscriberCount),
                        )
                        is org.schabi.newpipe.extractor.playlist.PlaylistInfoItem -> {
                            val id = Regex("""[?&]list=([\w-]+)""").find(item.url)?.groupValues?.get(1)
                            id?.let {
                                SearchResult.Playlist(
                                    com.vancedclean.app.data.PlaylistSummary(
                                        id = it,
                                        title = item.name.orEmpty(),
                                        thumbnailUrl = item.thumbnails.maxByOrNull { t -> t.height }?.url,
                                        subtitle = listOfNotNull(
                                            item.uploaderName,
                                            item.streamCount.takeIf { c -> c >= 0 }?.let { c -> "$c videos" }
                                        ).joinToString(" • ")
                                    )
                                )
                            }
                        }
                        else -> null
                    }
                }
                _isLoading.value = false
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _error.value = e.message ?: "Error en la búsqueda"
                _isLoading.value = false
            }
        }
    }

    private fun observeQuery() {
        viewModelScope.launch {
            _searchQuery
                .debounce(SUGGESTION_DEBOUNCE_MS)
                .map { it.trim() }
                .distinctUntilChanged()
                .collectLatest { query -> loadSuggestions(query) }
        }
    }

    private suspend fun loadSuggestions(query: String) {
        if (query.isEmpty() || query == lastSearchedQuery) {
            _searchSuggestions.value = emptyList()
            return
        }

        try {
            val suggestions = withContext(Dispatchers.IO) {
                NewPipe.getService(0).suggestionExtractor.suggestionList(query)
            }
            if (query != lastSearchedQuery) {
                _searchSuggestions.value = suggestions.take(MAX_SUGGESTIONS)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _searchSuggestions.value = emptyList()
        }
    }

    private fun formatDuration(seconds: Long): String {
        if (seconds <= 0) return ""
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
        else String.format(Locale.US, "%d:%02d", m, s)
    }

    private companion object {
        const val SUGGESTION_DEBOUNCE_MS = 300L
        const val MAX_SUGGESTIONS = 10
    }
}
