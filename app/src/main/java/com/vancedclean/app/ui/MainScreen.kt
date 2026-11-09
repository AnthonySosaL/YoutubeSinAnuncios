package com.vancedclean.app.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import coil.compose.AsyncImage
import com.vancedclean.app.player.PlayerSheet
import com.vancedclean.app.data.CookieStorage
import com.vancedclean.app.navigation.Screen
import com.vancedclean.app.ui.components.AppBottomSection
import com.vancedclean.app.ui.components.VideoCard
import com.vancedclean.app.viewmodel.DownloaderImpl
import com.vancedclean.app.viewmodel.LoginViewModel
import com.vancedclean.app.viewmodel.MainViewModel
import com.vancedclean.app.viewmodel.hasAuthCookies
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    navController: NavController,
    viewModel: MainViewModel = viewModel(),
    loginViewModel: LoginViewModel = viewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val isLoggedIn by loginViewModel.isLoggedIn.collectAsState()
    val userAvatar by loginViewModel.userAvatar.collectAsState()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    LaunchedEffect(Unit) {
        // Lee las cookies cifradas fuera del hilo principal y las inyecta en el
        // DownloaderImpl antes de que NewPipe haga peticiones autenticadas.
        val savedCookies = withContext(Dispatchers.IO) {
            CookieStorage.getCookies(context.applicationContext)
        }
        if (hasAuthCookies(savedCookies)) {
            DownloaderImpl.getInstance().setCookies(savedCookies)
        }
        loginViewModel.checkLoginStatus(context)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Zaza",
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    if (isLoggedIn) {
                        IconButton(onClick = { navController.navigate(Screen.Settings.route) }) {
                            if (userAvatar.isNotBlank()) {
                                AsyncImage(
                                    model = userAvatar,
                                    contentDescription = "Mi cuenta",
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.surfaceVariant),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Icon(
                                    Icons.Default.AccountCircle,
                                    contentDescription = "Mi cuenta",
                                    modifier = Modifier.size(32.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    } else {
                        IconButton(onClick = { navController.navigate(Screen.Login.route) }) {
                            Icon(
                                Icons.Default.AccountCircle,
                                contentDescription = "Iniciar sesión"
                            )
                        }
                    }
                    IconButton(onClick = { navController.navigate(Screen.Search.route) }) {
                        Icon(Icons.Default.Search, contentDescription = "Buscar")
                    }
                    IconButton(onClick = { navController.navigate(Screen.Settings.route) }) {
                        Icon(Icons.Default.Settings, contentDescription = "Ajustes")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            AppBottomSection(
                navController = navController,
                currentRoute = currentRoute,
                onHomeReselected = { viewModel.loadTrending() }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
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
                            uiState.error ?: "Error desconocido",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { viewModel.loadTrending() }) {
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
                            Icons.Default.PlayCircleOutline,
                            contentDescription = null,
                            modifier = Modifier.size(80.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "Zaza",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "YouTube limpio, sin anuncios",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(onClick = { viewModel.loadTrending() }) {
                            Text("Cargar Tendencias")
                        }
                    }
                }
                else -> {
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
