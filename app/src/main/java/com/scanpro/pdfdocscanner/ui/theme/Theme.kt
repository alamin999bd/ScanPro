package com.scanpro.pdfdocscanner.ui.theme

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

private val LightColors = lightColorScheme(
    primary = Indigo600,
    onPrimary = Color.White,
    primaryContainer = Indigo50,
    onPrimaryContainer = Indigo900,
    secondary = AccentPink,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFE2F1),
    onSecondaryContainer = Color(0xFF65143E),
    background = Color(0xFFF6F2FF),
    onBackground = Slate900,
    surface = Color(0xFFFFFFFF),
    onSurface = Slate900,
    surfaceVariant = Color(0xFFECE6F7),
    onSurfaceVariant = Slate500,
    outline = Slate300,
    outlineVariant = Slate200,
    error = ErrorRed,
)

private val DarkColors = darkColorScheme(
    primary = Indigo300,
    onPrimary = Slate950,
    primaryContainer = Color(0xFF4B2B88),
    onPrimaryContainer = Color(0xFFF0E8FF),
    secondary = AccentPink,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF61264B),
    onSecondaryContainer = Color(0xFFFFD9EC),
    tertiary = AccentCyan,
    onTertiary = Slate950,
    background = Slate950,
    onBackground = Slate50,
    surface = GlassSurfaceStrong,
    onSurface = Slate50,
    surfaceVariant = GlassSurface,
    onSurfaceVariant = Color(0xFFC1B6D8),
    outline = GlassOutline,
    outlineVariant = Color(0xFF3D3158),
    error = ErrorRedDark,
)

/** mode: 0 = follow system, 1 = light, 2 = dark */
@Composable
fun ScanProTheme(
    themeMode: Int = 0,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        1 -> false
        2 -> true
        else -> isSystemInDarkTheme()
    }
    val colors = if (dark) DarkColors else LightColors

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            window.navigationBarColor = android.graphics.Color.TRANSPARENT
        }
    }

    MaterialTheme(
        colorScheme = colors,
        typography = ScanProTypography,
        shapes = ScanProShapes,
        content = content,
    )
}
