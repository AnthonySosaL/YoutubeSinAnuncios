package com.vancedclean.app.ui

import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.MusicNote
import com.vancedclean.app.navigation.Screen
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.vancedclean.app.data.DARK_MODE_DARK
import com.vancedclean.app.data.DARK_MODE_LIGHT
import com.vancedclean.app.data.DARK_MODE_SYSTEM
import com.vancedclean.app.ui.components.AppBottomSection
import com.vancedclean.app.viewmodel.SettingsViewModel

private const val APP_CREATOR = "Anthony Sosa"
private const val APP_DATE = "8 de noviembre de 2026"
private const val GITHUB_URL = "https://github.com/AnthonySosaL/YoutubeSinAnuncios"
private const val NEWPIPE_EXTRACTOR_URL = "https://github.com/TeamNewPipe/NewPipeExtractor"

private val qualityOptions = listOf(
    "auto" to "Auto",
    "1080p" to "1080p",
    "720p" to "720p",
    "480p" to "480p",
    "360p" to "360p"
)

private val countryOptions = listOf(
    "US" to "Estados Unidos",
    "ES" to "España",
    "MX" to "México",
    "AR" to "Argentina",
    "CO" to "Colombia",
    "CL" to "Chile",
    "PE" to "Perú",
    "VE" to "Venezuela",
    "EC" to "Ecuador",
    "UY" to "Uruguay",
    "BR" to "Brasil",
    "CA" to "Canadá",
    "GB" to "Reino Unido",
    "FR" to "Francia",
    "DE" to "Alemania",
    "IT" to "Italia",
    "PT" to "Portugal",
    "JP" to "Japón",
    "KR" to "Corea del Sur",
    "IN" to "India"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavController,
    viewModel: SettingsViewModel = viewModel()
) {
    val settings by viewModel.settings.collectAsState()
    val systemDark = isSystemInDarkTheme()
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    var showQualityDialog by remember { mutableStateOf(false) }
    var showCountryDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }

    val versionName = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "1.0"
    }

    val openLink: (String) -> Unit = { url ->
        runCatching { uriHandler.openUri(url) }
    }

    val qualityLabel = qualityOptions.firstOrNull { it.first == settings.defaultQuality }?.second ?: "Auto"
    val countryLabel = countryOptions.firstOrNull { it.first == settings.country }?.second ?: settings.country

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ajustes") },
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            SectionHeader("Descargas y música")

            ClickItem(
                icon = Icons.Default.Download,
                title = "Descargar de YouTube o Instagram",
                subtitle = "Pega un enlace y elige MP4 o MP3 con su calidad",
                onClick = { navController.navigate(Screen.Download.createRoute()) }
            )

            ClickItem(
                icon = Icons.Default.VideoLibrary,
                title = "Mis descargas",
                subtitle = "Videos y audios guardados para ver sin internet",
                onClick = { navController.navigate(Screen.MyDownloads.route) }
            )

            ClickItem(
                icon = Icons.Default.MusicNote,
                title = "Reproductor de música",
                subtitle = "Tus MP3 descargados de YouTube, sin internet",
                onClick = { navController.navigate(Screen.Music.route) }
            )

            SectionHeader("Apariencia")

            SwitchItem(
                icon = Icons.Default.BrightnessAuto,
                title = "Usar tema del sistema",
                subtitle = "Sigue la configuración de tu dispositivo",
                checked = settings.darkMode == DARK_MODE_SYSTEM,
                onCheckedChange = { useSystem ->
                    if (useSystem) {
                        viewModel.setDarkMode(DARK_MODE_SYSTEM)
                    } else {
                        viewModel.setDarkMode(if (systemDark) DARK_MODE_DARK else DARK_MODE_LIGHT)
                    }
                }
            )

            SwitchItem(
                icon = Icons.Default.DarkMode,
                title = "Tema oscuro",
                subtitle = if (settings.darkMode == DARK_MODE_SYSTEM) {
                    "Desactiva el tema del sistema para elegir"
                } else {
                    "Usa colores oscuros en toda la app"
                },
                checked = when (settings.darkMode) {
                    DARK_MODE_DARK -> true
                    DARK_MODE_LIGHT -> false
                    else -> systemDark
                },
                enabled = settings.darkMode != DARK_MODE_SYSTEM,
                onCheckedChange = { dark ->
                    viewModel.setDarkMode(if (dark) DARK_MODE_DARK else DARK_MODE_LIGHT)
                }
            )

            SectionHeader("Reproducción")

            ClickItem(
                icon = Icons.Default.HighQuality,
                title = "Calidad de video por defecto",
                subtitle = qualityLabel,
                onClick = { showQualityDialog = true }
            )

            SwitchItem(
                icon = Icons.Default.Headphones,
                title = "Reproducción en segundo plano",
                subtitle = "Sigue reproduciendo al salir de la app",
                checked = settings.backgroundPlayback,
                onCheckedChange = viewModel::setBackgroundPlayback
            )

            SwitchItem(
                icon = Icons.Default.SkipNext,
                title = "Reproducción automática",
                subtitle = "Al terminar un video, pasa al siguiente relacionado",
                checked = settings.autoplay,
                onCheckedChange = viewModel::setAutoplay
            )

            SectionHeader("Contenido")

            ClickItem(
                icon = Icons.Default.Public,
                title = "País / región",
                subtitle = countryLabel,
                onClick = { showCountryDialog = true }
            )

            SectionHeader("Privacidad")

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                PrivacyBadge(
                    icon = Icons.Default.Block,
                    label = "Sin anuncios",
                    modifier = Modifier.weight(1f)
                )
                PrivacyBadge(
                    icon = Icons.Default.VisibilityOff,
                    label = "Sin tracking",
                    modifier = Modifier.weight(1f)
                )
            }

            Text(
                text = "Tu sesión de Google solo se usa para hablar con YouTube: sin analíticas ni rastreadores",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            )

            SectionHeader("Acerca de")

            ClickItem(
                icon = Icons.Default.Person,
                title = "Más información",
                subtitle = "Creado por $APP_CREATOR",
                onClick = { showAboutDialog = true }
            )

            ListItem(
                headlineContent = { Text("Versión") },
                supportingContent = { Text(versionName) },
                leadingContent = { Icon(Icons.Default.Info, contentDescription = null) }
            )

            ClickItem(
                icon = Icons.Default.Favorite,
                title = "Hecho con NewPipe Extractor",
                subtitle = "Extracción de datos de YouTube sin la API de Google",
                onClick = { openLink(NEWPIPE_EXTRACTOR_URL) }
            )

            ClickItem(
                icon = Icons.Default.Code,
                title = "GitHub",
                subtitle = GITHUB_URL,
                onClick = { openLink(GITHUB_URL) }
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            title = { Text("Zaza", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("YouTube sin anuncios, con tu cuenta de Google.")
                    Text("Creador: $APP_CREATOR", fontWeight = FontWeight.Medium)
                    Text("Fecha: $APP_DATE")
                    Text("Versión: $versionName")
                    Text(
                        GITHUB_URL,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable { openLink(GITHUB_URL) }
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showAboutDialog = false }) { Text("Cerrar") }
            }
        )
    }

    if (showQualityDialog) {
        OptionsDialog(
            title = "Calidad de video por defecto",
            options = qualityOptions,
            selected = settings.defaultQuality,
            onSelect = { value ->
                viewModel.setDefaultQuality(value)
                showQualityDialog = false
            },
            onDismiss = { showQualityDialog = false }
        )
    }

    if (showCountryDialog) {
        OptionsDialog(
            title = "País / región",
            options = countryOptions,
            selected = settings.country,
            onSelect = { value ->
                viewModel.setCountry(value)
                showCountryDialog = false
            },
            onDismiss = { showCountryDialog = false }
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 8.dp)
    )
}

@Composable
private fun SwitchItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        leadingContent = { Icon(icon, contentDescription = null) },
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled
            )
        },
        modifier = Modifier.clickable(enabled = enabled) { onCheckedChange(!checked) }
    )
}

@Composable
private fun ClickItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        leadingContent = { Icon(icon, contentDescription = null) },
        modifier = Modifier.clickable(onClick = onClick)
    )
}

@Composable
private fun PrivacyBadge(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun OptionsDialog(
    title: String,
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            LazyColumn {
                items(options, key = { it.first }) { (value, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(value) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = value == selected,
                            onClick = null
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}
