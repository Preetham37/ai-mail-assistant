package com.preetham.aimailassistant.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

val LocalIsDarkTheme = compositionLocalOf { false }

// =====================================================================
// RAW PALETTE CONSTANTS
// =====================================================================

// --- DAY MODE (80s-90s Classic Macintosh: Cream Background, Black Borders) ---
val LightCreamBg = Color(0xFFF5F1EB)        // Warm Paper Cream
val LightCreamSurface = Color(0xFFEBE6DC)   // Frame & Nav Cream
val LightWhite = Color(0xFFFFFFFF)          // Crisp Card White
val LightCardElevated = Color(0xFFE2DDD3)   // Inset / Pressed Surface
val LightBlack = Color(0xFF0F0F0F)          // Pitch ink / borders
val LightDarkGray = Color(0xFF2B2B2B)
val LightMediumGray = Color(0xFF5A5A5A)
val LightTextPrimary = Color(0xFF141414)    // Ink black
val LightTextSecondary = Color(0xFF383838)
val LightTextMuted = Color(0xFF6E6E6E)
val LightLinkBlue = Color(0xFF1E88E5)       // Friendly Gmail light blue
val LightShadowColor = Color(0xFF0F0F0F)    // Hard 80s black shadow

// --- NIGHT MODE (Dark Black Background, Warm Cream Borders) ---
// Requested: Dark black background, borders with cream, zero glare for night reading:
val DarkRetroBg = Color(0xFF0A0A0C)         // Deep dark black
val DarkRetroSurface = Color(0xFF111114)    // Console frame & nav
val DarkRetroCard = Color(0xFF141418)       // Dark black card surface
val DarkRetroElevated = Color(0xFF1C1C22)   // Inset surface
val DarkRetroBorder = Color(0xFFF5F1EB)     // Warm Paper Cream Border!
val DarkRetroDarkGray = Color(0xFFB0ACA2)
val DarkRetroMediumGray = Color(0xFFD4CEC3) // Cream gray
val DarkRetroLightGray = Color(0xFFF5F1EB)
val DarkRetroTextPrimary = Color(0xFFF5F1EB)// Warm Cream Text
val DarkRetroTextSecondary = Color(0xFFD4CEC3)
val DarkRetroTextMuted = Color(0xFFA09C94)
val DarkRetroLinkBlue = Color(0xFF64B5F6)   // Soft sky blue for dark mode
val DarkRetroShadowColor = Color(0xFF26262B)// Subtle dark depth shadow

// =====================================================================
// DYNAMIC THEME GETTERS
// Every composable automatically receives day or night colors based on theme!
// =====================================================================

val RetroCreamBg: Color
    @Composable
    @ReadOnlyComposable
    get() = if (LocalIsDarkTheme.current) DarkRetroBg else LightCreamBg

val RetroCreamSurface: Color
    @Composable
    @ReadOnlyComposable
    get() = if (LocalIsDarkTheme.current) DarkRetroSurface else LightCreamSurface

val RetroWhite: Color
    @Composable
    @ReadOnlyComposable
    get() = if (LocalIsDarkTheme.current) DarkRetroCard else LightWhite

val RetroCardElevated: Color
    @Composable
    @ReadOnlyComposable
    get() = if (LocalIsDarkTheme.current) DarkRetroElevated else LightCardElevated

val RetroBlack: Color
    @Composable
    @ReadOnlyComposable
    get() = if (LocalIsDarkTheme.current) DarkRetroBorder else LightBlack

val RetroDarkGray: Color
    @Composable
    @ReadOnlyComposable
    get() = if (LocalIsDarkTheme.current) DarkRetroDarkGray else LightDarkGray

val RetroMediumGray: Color
    @Composable
    @ReadOnlyComposable
    get() = if (LocalIsDarkTheme.current) DarkRetroMediumGray else LightMediumGray

val RetroLightGray: Color
    @Composable
    @ReadOnlyComposable
    get() = if (LocalIsDarkTheme.current) DarkRetroLightGray else Color(0xFFD4CEC3)

val RetroTextPrimary: Color
    @Composable
    @ReadOnlyComposable
    get() = if (LocalIsDarkTheme.current) DarkRetroTextPrimary else LightTextPrimary

val RetroTextSecondary: Color
    @Composable
    @ReadOnlyComposable
    get() = if (LocalIsDarkTheme.current) DarkRetroTextSecondary else LightTextSecondary

val RetroTextMuted: Color
    @Composable
    @ReadOnlyComposable
    get() = if (LocalIsDarkTheme.current) DarkRetroTextMuted else LightTextMuted

val RetroHighlight: Color
    @Composable
    @ReadOnlyComposable
    get() = if (LocalIsDarkTheme.current) DarkRetroTextPrimary else LightBlack

val RetroInverseText: Color
    @Composable
    @ReadOnlyComposable
    get() = if (LocalIsDarkTheme.current) DarkRetroBg else LightWhite

val RetroInverseBg: Color
    @Composable
    @ReadOnlyComposable
    get() = if (LocalIsDarkTheme.current) DarkRetroTextPrimary else LightBlack

val RetroAccentWarm: Color
    @Composable
    @ReadOnlyComposable
    get() = if (LocalIsDarkTheme.current) DarkRetroCard else Color(0xFFDDD7CB)

val RetroLinkLightBlue: Color
    @Composable
    @ReadOnlyComposable
    get() = if (LocalIsDarkTheme.current) DarkRetroLinkBlue else LightLinkBlue

val RetroShadowColor: Color
    @Composable
    @ReadOnlyComposable
    get() = if (LocalIsDarkTheme.current) DarkRetroShadowColor else LightShadowColor
