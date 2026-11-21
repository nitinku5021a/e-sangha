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
    surfaceVariant = Color(0xFFEFEDE8),
    onPrimary = Paper,
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
    primary = Paper,
    secondary = TextLightMuted,
    tertiary = Color(0xFFC5CBD6),
    background = Ink,
    surface = DarkSurface,
    surfaceVariant = Color(0xFF2C2C2C),
    onPrimary = Ink,
    onSecondary = Ink,
    onTertiary = Ink,
    onBackground = Paper,
    onSurface = Paper,
    onSurfaceVariant = TextLightMuted,
    outline = Color(0xFF3A3A3A),
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
