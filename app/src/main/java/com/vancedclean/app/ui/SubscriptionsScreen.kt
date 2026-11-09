package com.vancedclean.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material3.HorizontalDivider
import com.vancedclean.app.ui.components.ChannelBubble
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.vancedclean.app.viewmodel.LoginViewModel
import com.vancedclean.app.viewmodel.SubscriptionsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubscriptionsScreen(
    navController: NavController,
    viewModel: SubscriptionsViewModel = viewModel(),
    loginViewModel: LoginViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val channels by viewModel.channels.collectAsState()
    val isLoggedIn by loginViewModel.isLoggedIn.collectAsState()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    LaunchedEffect(isLoggedIn) {
        if (isLoggedIn) {
            viewModel.loadFeed()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Suscripciones", fontWeight = FontWeight.Bold) },
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
                            Icons.Default.Subscriptions,
                            contentDescription = null,
                            modifier = Modifier.size(80.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "Inicia sesión para ver tus suscripciones",
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { navController.navigate(Screen.Login.route) }) {
                            Text("Iniciar sesión")
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
                        Button(onClick = { viewModel.loadFeed() }) {
                            Text("Reintentar")
                        }
                    }
                }
                uiState.videos.isEmpty() -> {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.Subscriptions,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "No hay videos recientes de tus suscripciones",
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center
                        )
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 8.dp)
                    ) {
                        if (channels.isNotEmpty()) {
                            item {
                                LazyRow(contentPadding = PaddingValues(horizontal = 8.dp)) {
                                    items(channels, key = { it.id }) { channel ->
                                        ChannelBubble(channel) {
                                            navController.navigate(Screen.Channel.createRoute(channel.url))
                                        }
                                    }
                                }
                                HorizontalDivider()
                            }
                        }
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
