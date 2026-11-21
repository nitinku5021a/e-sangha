package com.digital.sanghaworld.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColorScheme = lightColorScheme(
    primary = Indigo,
    secondary = IndigoSoft,
    tertiary = Indigo,
    background = Paper,
    surface = White,
    surfaceVariant = Sage,
    secondaryContainer = Sand,
    onPrimary = White,
    onSecondary = Ink,
    onTertiary = Paper,
    onBackground = Ink,
    onSurface = Ink,
    onSurfaceVariant = InkMuted,
    outline = StoneLine,
    error = ErrorWarm,
    onError = White
)

private val DarkColorScheme = darkColorScheme(
    primary = Indigo,
    secondary = IndigoSoft,
    tertiary = Sage,
    background = Color(0xFF1C241E),
    surface = Color(0xFF27312A),
    surfaceVariant = Color(0xFF3A4A3E),
    secondaryContainer = Color(0xFF2A342C),
    onPrimary = White,
    onSecondary = Paper,
    onTertiary = Ink,
    onBackground = Paper,
    onSurface = Paper,
    onSurfaceVariant = Color(0xFFC5CBB8),
    outline = Color(0xFF4A5A4C),
    error = Color(0xFFE7B2AE),
    onError = Ink
)

@Composable
fun VipassanaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = Typography,
        content = content
    )
}
