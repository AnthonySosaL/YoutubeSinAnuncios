package com.vancedclean.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.NorthWest
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.vancedclean.app.player.PlayerSheet
import com.vancedclean.app.navigation.Screen
import com.vancedclean.app.ui.components.AppBottomSection
import com.vancedclean.app.ui.components.VideoCard
import com.vancedclean.app.viewmodel.SearchViewModel
import com.vancedclean.app.viewmodel.SearchFilter
import com.vancedclean.app.viewmodel.SearchResult
import com.vancedclean.app.ui.components.PlaylistRow
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.ui.draw.clip
import coil.compose.AsyncImage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    navController: NavController,
    viewModel: SearchViewModel = viewModel()
) {
    val query by viewModel.searchQuery.collectAsState()
    val results by viewModel.searchResults.collectAsState()
    val filter by viewModel.filter.collectAsState()
    val suggestions by viewModel.searchSuggestions.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val hasSearched by viewModel.hasSearched.collectAsState()

    var showSuggestions by remember { mutableStateOf(!hasSearched) }
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    LaunchedEffect(Unit) {
        if (!hasSearched) {
            focusRequester.requestFocus()
        }
    }

    val performSearch: (String) -> Unit = { text ->
        if (text.isNotBlank()) {
            showSuggestions = false
            focusManager.clearFocus()
            keyboardController?.hide()
            viewModel.search(text)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    TextField(
                        value = query,
                        onValueChange = {
                            showSuggestions = true
                            viewModel.onQueryChange(it)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester)
                            .onFocusChanged { if (it.isFocused) showSuggestions = true },
                        placeholder = { Text("Buscar en YouTube") },
                        singleLine = true,
                        trailingIcon = {
                            if (query.isNotEmpty()) {
                                IconButton(
                                    onClick = {
                                        viewModel.clearQuery()
                                        showSuggestions = true
                                        focusRequester.requestFocus()
                                    }
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Borrar búsqueda"
                                    )
                                }
                            }
                        },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { performSearch(query) }),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        )
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
        },
        bottomBar = {
            AppBottomSection(navController = navController, currentRoute = currentRoute, showNavBar = false)
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                showSuggestions && query.isNotBlank() && suggestions.isNotEmpty() -> {
                    SuggestionList(
                        suggestions = suggestions,
                        onSuggestionClick = { suggestion ->
                            viewModel.onQueryChange(suggestion)
                            performSearch(suggestion)
                        },
                        onSuggestionFill = { suggestion ->
                            showSuggestions = true
                            viewModel.onQueryChange(suggestion)
                        }
                    )
                }
                isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                error != null -> {
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
                            text = error.orEmpty(),
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { viewModel.search(query) }) {
                            Text("Reintentar")
                        }
                    }
                }
                hasSearched && results.isEmpty() -> {
                    FilterChips(filter, viewModel::setFilter)
                    MessageState(
                        icon = Icons.Default.SearchOff,
                        message = "No se encontraron resultados"
                    )
                }
                results.isNotEmpty() -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 8.dp)
                    ) {
                        item { FilterChips(filter, viewModel::setFilter) }
                        items(results) { result ->
                            when (result) {
                                is SearchResult.Video -> VideoCard(
                                    video = result.video,
                                    onClick = { PlayerSheet.open(result.video.url) }
                                )
                                is SearchResult.Channel -> ListItem(
                                    headlineContent = { Text(result.name) },
                                    supportingContent = { if (result.subtitle.isNotBlank()) Text(result.subtitle) },
                                    leadingContent = {
                                        AsyncImage(
                                            model = result.avatarUrl,
                                            contentDescription = null,
                                            modifier = Modifier.size(56.dp).clip(CircleShape)
                                        )
                                    },
                                    modifier = Modifier.clickable {
                                        navController.navigate(Screen.Channel.createRoute(result.url))
                                    }
                                )
                                is SearchResult.Playlist -> PlaylistRow(
                                    playlist = result.playlist,
                                    onClick = {
                                        navController.navigate(
                                            Screen.Playlist.createRoute(result.playlist.id, result.playlist.title)
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
                else -> {
                    MessageState(
                        icon = Icons.Default.Search,
                        message = "Busca videos en YouTube"
                    )
                }
            }
        }
    }
}

@Composable
private fun FilterChips(selected: SearchFilter, onSelect: (SearchFilter) -> Unit) {
    LazyRow(
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
    ) {
        items(SearchFilter.entries) { option ->
            FilterChip(
                selected = option == selected,
                onClick = { onSelect(option) },
                label = { Text(option.label) }
            )
        }
    }
}

@Composable
private fun SuggestionList(
    suggestions: List<String>,
    onSuggestionClick: (String) -> Unit,
    onSuggestionFill: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
    ) {
        items(suggestions) { suggestion ->
            ListItem(
                headlineContent = { Text(suggestion) },
                leadingContent = {
                    Icon(Icons.Default.Search, contentDescription = null)
                },
                trailingContent = {
                    IconButton(onClick = { onSuggestionFill(suggestion) }) {
                        Icon(
                            Icons.Default.NorthWest,
                            contentDescription = "Usar sugerencia"
                        )
                    }
                },
                modifier = Modifier.clickable { onSuggestionClick(suggestion) }
            )
        }
    }
}

@Composable
private fun BoxMessage(icon: ImageVector, message: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun MessageState(icon: ImageVector, message: String) {
    BoxMessage(icon = icon, message = message)
}
