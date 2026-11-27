package com.scanpro.pdfdocscanner.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColors = lightColorScheme(
    primary = Indigo600,
    onPrimary = androidx.compose.ui.graphics.Color.White,
    primaryContainer = Indigo50,
    onPrimaryContainer = Indigo900,
    secondary = Amber600,
    onSecondary = androidx.compose.ui.graphics.Color.White,
    secondaryContainer = ColorTokens.CoralContainer,
    onSecondaryContainer = ColorTokens.CoralInk,
    background = Slate50,
    onBackground = Slate900,
    surface = androidx.compose.ui.graphics.Color.White,
    onSurface = Slate900,
    surfaceVariant = Slate100,
    onSurfaceVariant = Slate500,
    outline = Slate300,
    outlineVariant = Slate200,
    error = ErrorRed,
)

private val DarkColors = darkColorScheme(
    primary = Indigo300,
    onPrimary = Slate950,
    primaryContainer = Slate800,
    onPrimaryContainer = Indigo100,
    secondary = Amber400,
    onSecondary = Slate950,
    secondaryContainer = ColorTokens.DarkCoralContainer,
    onSecondaryContainer = Amber400,
    background = Slate950,
    onBackground = Slate100,
    surface = Slate900,
    onSurface = Slate100,
    surfaceVariant = Slate800,
    onSurfaceVariant = Slate400,
    outline = Slate700,
    outlineVariant = Slate800,
    error = ErrorRedDark,
)

private object ColorTokens {
    val CoralContainer = androidx.compose.ui.graphics.Color(0xFFFFE9DC)
    val CoralInk = androidx.compose.ui.graphics.Color(0xFF713719)
    val DarkCoralContainer = androidx.compose.ui.graphics.Color(0xFF53301F)
}

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
