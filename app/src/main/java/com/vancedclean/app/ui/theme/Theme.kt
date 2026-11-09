package com.vancedclean.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.vancedclean.app.data.AppPreferences
import com.vancedclean.app.data.DARK_MODE_DARK
import com.vancedclean.app.data.DARK_MODE_LIGHT
import com.vancedclean.app.data.DARK_MODE_SYSTEM

// Negro neutro en todo (sin el tinte morado/rojizo por defecto de Material 3):
// barras superior e inferior del mismo negro que el contenido.
private val Black = Color(0xFF0A0A0A)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFFF0000),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF93000A),
    secondaryContainer = Color(0xFF2C2C2C),
    onSecondaryContainer = Color.White,
    surface = Black,
    onSurface = Color.White,
    background = Black,
    onBackground = Color.White,
    surfaceVariant = Color(0xFF1E1E1E),
    onSurfaceVariant = Color(0xFFCACACA),
    surfaceTint = Color.Transparent,
    surfaceContainerLowest = Black,
    surfaceContainerLow = Color(0xFF181818),
    surfaceContainer = Black,
    surfaceContainerHigh = Color(0xFF222222),
    surfaceContainerHighest = Color(0xFF2A2A2A),
    surfaceBright = Color(0xFF2A2A2A),
    surfaceDim = Black,
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFFCC0000),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDAD5),
    surface = Color(0xFFFFFBFF),
    onSurface = Color(0xFF1C1B1F),
    background = Color(0xFFFFFBFF),
    onBackground = Color(0xFF1C1B1F),
    surfaceVariant = Color(0xFFF3F3F3),
    onSurfaceVariant = Color(0xFF49454F),
    secondaryContainer = Color(0xFFE6E6E6),
    onSecondaryContainer = Color(0xFF1C1B1F),
    surfaceTint = Color.Transparent,
    surfaceContainerLowest = Color(0xFFFFFBFF),
    surfaceContainerLow = Color(0xFFF7F7F7),
    surfaceContainer = Color(0xFFFFFBFF),
    surfaceContainerHigh = Color(0xFFF0F0F0),
    surfaceContainerHighest = Color(0xFFE8E8E8),
)

@Composable
fun VancedCleanTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val preferences = remember { AppPreferences.getInstance(context) }
    val darkMode by preferences.darkMode.collectAsState(initial = DARK_MODE_SYSTEM)

    val darkTheme = when (darkMode) {
        DARK_MODE_LIGHT -> false
        DARK_MODE_DARK -> true
        else -> isSystemInDarkTheme()
    }

    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = Typography(),
        content = content
    )
}
