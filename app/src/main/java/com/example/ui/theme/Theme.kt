package com.example.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

// Default: Dark Mode (recommended for off-grid resilient night & outdoors battery saving)
val MonrDarkColorScheme = darkColorScheme(
    primary = MonrCyanAccent,
    onPrimary = MonrBgDark,
    primaryContainer = MonrPrimary,
    onPrimaryContainer = MonrCyanLight,
    secondary = MonrCyanLight,
    onSecondary = MonrBgDark,
    secondaryContainer = MonrSurfaceVariantDark,
    onSecondaryContainer = MonrTextPrimary,
    tertiary = MonrAmberWarning,
    onTertiary = MonrBgDark,
    background = MonrBgDark,
    onBackground = MonrTextPrimary,
    surface = MonrSurfaceDark,
    onSurface = MonrTextPrimary,
    surfaceVariant = MonrSurfaceVariantDark,
    onSurfaceVariant = MonrTextSecondary,
    outline = MonrBorderDark,
    outlineVariant = Color(0xFF1E2D40),
    error = Color(0xFFEF4444),
    onError = Color.White
)

val MonrLightColorScheme = lightColorScheme(
    primary = MonrCyanAccent,
    onPrimary = Color(0xFF07121E),
    primaryContainer = Color(0xFFCFFAFE),
    onPrimaryContainer = Color(0xFF155E75),
    secondary = MonrCyanDark,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCFFAFE),
    onSecondaryContainer = Color(0xFF155E75),
    tertiary = MonrAmberWarning,
    onTertiary = Color.White,
    background = MonrBgLight,
    onBackground = MonrTextPrimaryLight,
    surface = MonrSurfaceLight,
    onSurface = MonrTextPrimaryLight,
    surfaceVariant = MonrSurfaceVariantLight,
    onSurfaceVariant = MonrTextSecondaryLight,
    outline = MonrBorderLight,
    outlineVariant = Color(0xFFE2E8F0),
    error = Color(0xFFDC2626),
    onError = Color.White
)

fun ColorScheme.isMonrDark(): Boolean = background.luminance() < 0.5f

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true, // Default to dark mode for off-grid Monr experience
    dynamicColor: Boolean = false, // Keep Monr branded deep slate & cyan consistent
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) MonrDarkColorScheme else MonrLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
