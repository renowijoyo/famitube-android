package com.famitube.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = PinkPrimary,
    onPrimary = Color.White,
    primaryContainer = PinkLight,
    onPrimaryContainer = Color.White,
    secondary = BlueSecondary,
    onSecondary = Color.White,
    secondaryContainer = SurfaceLightVariant,
    onSecondaryContainer = BlueSecondary,
    background = BackgroundLight,
    onBackground = TextPrimaryLight,
    surface = SurfaceLight,
    onSurface = TextPrimaryLight,
    surfaceVariant = SurfaceLightVariant,
    onSurfaceVariant = TextSecondaryLight,
    outline = BorderLight
)

private val DarkColorScheme = darkColorScheme(
    primary = PinkPrimary,
    onPrimary = Color.White,
    primaryContainer = PinkDark,
    onPrimaryContainer = Color.White,
    secondary = BlueSecondary,
    onSecondary = Color.White,
    secondaryContainer = SurfaceLightDark,
    onSecondaryContainer = CyanAccent,
    background = BackgroundDark,
    onBackground = TextPrimary,
    surface = SurfaceDark,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceLightDark,
    onSurfaceVariant = TextSecondary,
    outline = BorderDark
)

@Composable
fun FamiTubeTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = Typography,
        content = content
    )
}
