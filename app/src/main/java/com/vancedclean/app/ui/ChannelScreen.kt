package com.vancedclean.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.vancedclean.app.data.PlaylistSummary
import com.vancedclean.app.navigation.Screen
import com.vancedclean.app.ui.components.PlaylistRow
import com.vancedclean.app.ui.components.toast
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabs
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.vancedclean.app.player.PlayerSheet
import com.vancedclean.app.ui.components.VideoCard
import com.vancedclean.app.viewmodel.ChannelDetail
import com.vancedclean.app.viewmodel.ChannelUiState
import com.vancedclean.app.viewmodel.ChannelVideosState
import com.vancedclean.app.viewmodel.ChannelViewModel
import com.vancedclean.app.viewmodel.formatSubscriberCount

private enum class ChannelTab(val label: String, val filter: String) {
    Videos("Videos", ChannelTabs.VIDEOS),
    Shorts("Shorts", ChannelTabs.SHORTS),
    Live("En vivo", ChannelTabs.LIVESTREAMS),
    Playlists("Playlists", ChannelTabs.PLAYLISTS)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelScreen(
    navController: NavController,
    viewModel: ChannelViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val videosState by viewModel.videosState.collectAsState()
    val selectedFilter by viewModel.selectedTab.collectAsState()
    val subscribed by viewModel.subscribed.collectAsState()
    val context = LocalContext.current
    LaunchedEffect(uiState is ChannelUiState.Success) {
        if (uiState is ChannelUiState.Success) viewModel.loadSubscription(context)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = (uiState as? ChannelUiState.Success)?.channel?.name.orEmpty(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Volver"
                        )
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (val state = uiState) {
                is ChannelUiState.Loading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                is ChannelUiState.Error -> {
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
                            text = state.message,
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { viewModel.retry() }) {
                            Text("Reintentar")
                        }
                    }
                }
                is ChannelUiState.Success -> {
                    ChannelContent(
                        channel = state.channel,
                        videosState = videosState,
                        selectedTab = ChannelTab.entries.first { it.filter == selectedFilter },
                        onSelectTab = { viewModel.selectTab(it.filter) },
                        subscribed = subscribed,
                        onSubscribe = { viewModel.toggleSubscribe(context) { msg -> toast(context, msg) } },
                        onPlaylistClick = { p ->
                            navController.navigate(Screen.Playlist.createRoute(p.id, p.title))
                        },
                        onVideoClick = { url ->
                            PlayerSheet.open(url)
                        },
                        onLoadMore = { viewModel.loadMore() },
                        onRetryVideos = { viewModel.retryVideos() }
                    )
                }
            }
        }
    }
}

@Composable
private fun ChannelContent(
    channel: ChannelDetail,
    videosState: ChannelVideosState,
    selectedTab: ChannelTab,
    onSelectTab: (ChannelTab) -> Unit,
    subscribed: Boolean?,
    onSubscribe: () -> Unit,
    onPlaylistClick: (PlaylistSummary) -> Unit,
    onVideoClick: (String) -> Unit,
    onLoadMore: () -> Unit,
    onRetryVideos: () -> Unit
) {
    val listState = rememberLazyListState()
    var isDescriptionExpanded by remember { mutableStateOf(false) }

    val shouldLoadMore by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            info.totalItemsCount > 0 && last >= info.totalItemsCount - 3
        }
    }

    LaunchedEffect(shouldLoadMore, videosState.videos.size, selectedTab) {
        if (shouldLoadMore) {
            onLoadMore()
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            ChannelHeader(channel = channel, subscribed = subscribed, onSubscribe = onSubscribe)
        }

        if (channel.description.isNotBlank()) {
            item {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .clickable { isDescriptionExpanded = !isDescriptionExpanded },
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        text = channel.description,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = if (isDescriptionExpanded) Int.MAX_VALUE else 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }

        item {
            ScrollableTabRow(selectedTabIndex = selectedTab.ordinal, edgePadding = 8.dp) {
                ChannelTab.entries.forEach { tab ->
                    Tab(
                        selected = tab == selectedTab,
                        onClick = { onSelectTab(tab) },
                        text = { Text(tab.label) }
                    )
                }
            }
        }

        when {
            videosState.isLoading -> {
                item {
                    LoadingIndicator()
                }
            }
            videosState.error != null -> {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = videosState.error.orEmpty(),
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = onRetryVideos) {
                            Text("Reintentar")
                        }
                    }
                }
            }
            selectedTab == ChannelTab.Playlists && videosState.playlists.isNotEmpty() -> {
                items(videosState.playlists, key = { it.id }) { playlist ->
                    PlaylistRow(playlist = playlist, onClick = { onPlaylistClick(playlist) })
                }
                if (videosState.isLoadingMore) {
                    item { LoadingIndicator() }
                }
            }
            videosState.videos.isEmpty() -> {
                item {
                    CenteredMessage(
                        when (selectedTab) {
                            ChannelTab.Videos -> "Este canal no tiene videos"
                            ChannelTab.Shorts -> "Este canal no tiene Shorts"
                            ChannelTab.Live -> "Este canal no tiene directos"
                            ChannelTab.Playlists -> "Este canal no tiene playlists"
                        }
                    )
                }
            }
            else -> {
                items(videosState.videos, key = { it.url }) { video ->
                    VideoCard(
                        video = video,
                        onClick = { onVideoClick(video.url) }
                    )
                }
                if (videosState.isLoadingMore) {
                    item {
                        LoadingIndicator()
                    }
                }
            }
        }
    }
}

@Composable
private fun ChannelHeader(channel: ChannelDetail, subscribed: Boolean?, onSubscribe: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 5f)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                if (!channel.bannerUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = channel.bannerUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
            }

            AsyncImage(
                model = channel.avatarUrl,
                contentDescription = channel.name,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .offset(x = 16.dp, y = 36.dp)
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .border(3.dp, MaterialTheme.colorScheme.surface, CircleShape),
                contentScale = ContentScale.Crop
            )
        }

        Spacer(modifier = Modifier.height(44.dp))

        Row(
            modifier = Modifier.fillMaxWidth().padding(end = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
        Column(modifier = Modifier.weight(1f).padding(horizontal = 16.dp)) {
            Text(
                text = channel.name,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            val subscribers = formatSubscriberCount(channel.subscriberCount)
            if (subscribers.isNotEmpty()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subscribers,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
            if (subscribed == true) {
                FilledTonalButton(onClick = onSubscribe) { Text("Suscrito") }
            } else {
                Button(onClick = onSubscribe) { Text("Suscribirse") }
            }
        }
    }
}

@Composable
private fun LoadingIndicator() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun CenteredMessage(message: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(48.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}
