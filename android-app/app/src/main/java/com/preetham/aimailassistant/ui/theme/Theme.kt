package com.preetham.aimailassistant.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightRetroColorScheme = lightColorScheme(
    primary = LightBlack,
    onPrimary = LightWhite,
    primaryContainer = LightWhite,
    onPrimaryContainer = LightBlack,
    secondary = LightDarkGray,
    onSecondary = LightWhite,
    background = LightCreamBg,
    onBackground = LightTextPrimary,
    surface = LightWhite,
    onSurface = LightTextPrimary,
    surfaceVariant = LightCreamSurface,
    onSurfaceVariant = LightTextSecondary,
    outline = LightBlack
)

private val DarkRetroColorScheme = darkColorScheme(
    primary = DarkRetroTextPrimary,
    onPrimary = DarkRetroBg,
    primaryContainer = DarkRetroCard,
    onPrimaryContainer = DarkRetroTextPrimary,
    secondary = DarkRetroMediumGray,
    onSecondary = DarkRetroBg,
    background = DarkRetroBg,
    onBackground = DarkRetroTextPrimary,
    surface = DarkRetroCard,
    onSurface = DarkRetroTextPrimary,
    surfaceVariant = DarkRetroSurface,
    onSurfaceVariant = DarkRetroTextSecondary,
    outline = DarkRetroBorder
)

@Composable
fun BriefingOSTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                val insetsController = WindowCompat.getInsetsController(window, view)
                // In dark mode: status/nav bar text & icons are white (isAppearanceLight = false)
                // In light mode: status/nav bar text & icons are black (isAppearanceLight = true)
                insetsController.isAppearanceLightStatusBars = !darkTheme
                insetsController.isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    val colorScheme = if (darkTheme) DarkRetroColorScheme else LightRetroColorScheme

    CompositionLocalProvider(
        LocalIsDarkTheme provides darkTheme
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            content = content
        )
    }
}
