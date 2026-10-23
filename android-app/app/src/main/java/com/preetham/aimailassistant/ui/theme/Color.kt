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

// --- INFO LEVEL PALETTE (Subtle vintage accents for visual hierarchy) ---
// Level 1: Critical / Security Alert (Terracotta / Crimson)
val LightLevelSecurityAccent = Color(0xFFC62828)
val LightLevelSecurityBg = Color(0xFFFCE8E6)
val LightLevelSecurityText = Color(0xFFB71C1C)

val DarkLevelSecurityAccent = Color(0xFFEF5350)
val DarkLevelSecurityBg = Color(0xFF2B1418)
val DarkLevelSecurityText = Color(0xFFFF8A80)

// Level 2: Action Required / Tasks (Warm Amber Gold)
val LightLevelActionAccent = Color(0xFFD97706)
val LightLevelActionBg = Color(0xFFFEF3C7)
val LightLevelActionText = Color(0xFF92400E)

val DarkLevelActionAccent = Color(0xFFF59E0B)
val DarkLevelActionBg = Color(0xFF281B0E)
val DarkLevelActionText = Color(0xFFFDE68A)

// Level 3: Important / Time-Sensitive (Steel Navy / Sky Slate)
val LightLevelImportantAccent = Color(0xFF2563EB)
val LightLevelImportantBg = Color(0xFFEFF6FF)
val LightLevelImportantText = Color(0xFF1E40AF)

val DarkLevelImportantAccent = Color(0xFF60A5FA)
val DarkLevelImportantBg = Color(0xFF101E33)
val DarkLevelImportantText = Color(0xFFBFDBFE)

// Level 4: Topic Digest / Informational (Vintage Sage / Mint)
val LightLevelInfoAccent = Color(0xFF16A34A)
val LightLevelInfoBg = Color(0xFFF0FDF4)
val LightLevelInfoText = Color(0xFF166534)

val DarkLevelInfoAccent = Color(0xFF4ADE80)
val DarkLevelInfoBg = Color(0xFF0F2618)
val DarkLevelInfoText = Color(0xFFBBF7D0)

// Level 5: Idle / Resolved
val LightLevelResolvedAccent = Color(0xFF94A3B8)
val LightLevelResolvedBg = Color(0xFFF1F5F9)
val LightLevelResolvedText = Color(0xFF64748B)

val DarkLevelResolvedAccent = Color(0xFF64748B)
val DarkLevelResolvedBg = Color(0xFF1E222A)
val DarkLevelResolvedText = Color(0xFF94A3B8)

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

// --- DYNAMIC LEVEL COLORS ---
val LevelSecurityAccent: Color
    @Composable @ReadOnlyComposable get() = if (LocalIsDarkTheme.current) DarkLevelSecurityAccent else LightLevelSecurityAccent

val LevelSecurityBg: Color
    @Composable @ReadOnlyComposable get() = if (LocalIsDarkTheme.current) DarkLevelSecurityBg else LightLevelSecurityBg

val LevelSecurityText: Color
    @Composable @ReadOnlyComposable get() = if (LocalIsDarkTheme.current) DarkLevelSecurityText else LightLevelSecurityText

val LevelActionAccent: Color
    @Composable @ReadOnlyComposable get() = if (LocalIsDarkTheme.current) DarkLevelActionAccent else LightLevelActionAccent

val LevelActionBg: Color
    @Composable @ReadOnlyComposable get() = if (LocalIsDarkTheme.current) DarkLevelActionBg else LightLevelActionBg

val LevelActionText: Color
    @Composable @ReadOnlyComposable get() = if (LocalIsDarkTheme.current) DarkLevelActionText else LightLevelActionText

val LevelImportantAccent: Color
    @Composable @ReadOnlyComposable get() = if (LocalIsDarkTheme.current) DarkLevelImportantAccent else LightLevelImportantAccent

val LevelImportantBg: Color
    @Composable @ReadOnlyComposable get() = if (LocalIsDarkTheme.current) DarkLevelImportantBg else LightLevelImportantBg

val LevelImportantText: Color
    @Composable @ReadOnlyComposable get() = if (LocalIsDarkTheme.current) DarkLevelImportantText else LightLevelImportantText

val LevelInfoAccent: Color
    @Composable @ReadOnlyComposable get() = if (LocalIsDarkTheme.current) DarkLevelInfoAccent else LightLevelInfoAccent

val LevelInfoBg: Color
    @Composable @ReadOnlyComposable get() = if (LocalIsDarkTheme.current) DarkLevelInfoBg else LightLevelInfoBg

val LevelInfoText: Color
    @Composable @ReadOnlyComposable get() = if (LocalIsDarkTheme.current) DarkLevelInfoText else LightLevelInfoText

val LevelResolvedAccent: Color
    @Composable @ReadOnlyComposable get() = if (LocalIsDarkTheme.current) DarkLevelResolvedAccent else LightLevelResolvedAccent

val LevelResolvedBg: Color
    @Composable @ReadOnlyComposable get() = if (LocalIsDarkTheme.current) DarkLevelResolvedBg else LightLevelResolvedBg

val LevelResolvedText: Color
    @Composable @ReadOnlyComposable get() = if (LocalIsDarkTheme.current) DarkLevelResolvedText else LightLevelResolvedText
