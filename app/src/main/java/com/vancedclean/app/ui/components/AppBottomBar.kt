package com.vancedclean.app.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import com.vancedclean.app.player.PlayerSheet
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import com.vancedclean.app.navigation.Screen
import com.vancedclean.app.player.VideoPlayerManager

data class BottomNavItem(
    val route: String,
    val label: String,
    val icon: ImageVector
)

val bottomNavItems = listOf(
    BottomNavItem(Screen.Home.route, "Inicio", Icons.Default.Home),
    BottomNavItem(Screen.Search.route, "Buscar", Icons.Default.Search),
    BottomNavItem(Screen.Subscriptions.route, "Subs", Icons.Default.Subscriptions),
    BottomNavItem(Screen.Library.route, "Biblioteca", Icons.Default.VideoLibrary),
    BottomNavItem(Screen.Settings.route, "Ajustes", Icons.Default.Settings)
)

@Composable
fun AppBottomBar(
    navController: NavController,
    currentRoute: String?,
    onHomeReselected: (() -> Unit)? = null
) {
    NavigationBar {
        bottomNavItems.forEach { item ->
            val selected = currentRoute == item.route
            NavigationBarItem(
                icon = { Icon(item.icon, contentDescription = null) },
                label = { Text(item.label) },
                selected = selected,
                onClick = {
                    if (selected) {
                        if (item.route == Screen.Home.route) onHomeReselected?.invoke()
                    } else {
                        navController.navigate(item.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                }
            )
        }
    }
}

@Composable
fun AppBottomSection(
    navController: NavController,
    currentRoute: String?,
    showNavBar: Boolean = true,
    onHomeReselected: (() -> Unit)? = null
) {
    val videoUrl by PlayerSheet.videoUrl.collectAsState()

    Column {
        // El mini reproductor lo dibuja VideoPlayerSheet encima; aquí solo reservamos
        // su espacio para que el contenido no quede tapado.
        if (videoUrl != null) {
            Spacer(
                Modifier
                    .fillMaxWidth()
                    .height(64.dp)
            )
        }
        if (showNavBar) {
            Box(Modifier.onSizeChanged { PlayerSheet.navBarHeightPx.value = it.height }) {
                AppBottomBar(
                    navController = navController,
                    currentRoute = currentRoute,
                    onHomeReselected = onHomeReselected
                )
            }
        } else if (videoUrl != null) {
            Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
        }
    }
}
