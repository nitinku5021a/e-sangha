package com.digital.sanghaworld.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFF0C36A),
    secondary = WarmGold,
    tertiary = Color(0xFFE07A3D),
    background = DarkBackground,
    surface = DarkSurface,
    surfaceVariant = Color(0xFF3A2A1C),
    onPrimary = Color(0xFF2C2116),
    onSecondary = Ink,
    onTertiary = DarkBackground,
    onBackground = TextLight,
    onSurface = TextLight,
    onSurfaceVariant = TextLightMuted,
    outline = Color(0xFF5C4630),
    error = ErrorWarm,
    onError = TextLight
)

private val LightColorScheme = lightColorScheme(
    primary = WarmGoldDeep,
    secondary = WarmGold,
    tertiary = Color(0xFFC45C12),
    background = Parchment,
    surface = CreamSurface,
    surfaceVariant = Color(0xFFFFE8C2),
    onPrimary = Color(0xFFFFFBF3),
    onSecondary = Ink,
    onTertiary = Color.White,
    onBackground = Ink,
    onSurface = Ink,
    onSurfaceVariant = InkMuted,
    outline = Color(0xFFE4CBA3),
    error = ErrorWarm,
    onError = Color.White
)

@Composable
fun VipassanaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
