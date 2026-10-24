package com.preetham.aimailassistant.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.preetham.aimailassistant.ui.theme.LocalIsDarkTheme

/**
 * Tactile Retro-Brutalist Account Pill Badge.
 * Formats account tags like [PERSONAL], [WORK], [DEV] with authentic 80s hardware borders.
 */
@Composable
fun AccountBadge(
    label: String,
    colorHex: String,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 9.sp,
    onClick: (() -> Unit)? = null
) {
    val isDark = LocalIsDarkTheme.current
    val parsedColor = remember(colorHex) {
        try {
            Color(android.graphics.Color.parseColor(colorHex))
        } catch (_: Exception) {
            Color(0xFF3B82F6) // Work Blue default fallback
        }
    }

    val bgTint = if (isDark) {
        parsedColor.copy(alpha = 0.22f)
    } else {
        parsedColor.copy(alpha = 0.12f)
    }

    val displayLabel = remember(label) {
        val clean = label.trim().uppercase()
        if (clean.startsWith("[") && clean.endsWith("]")) clean else "[$clean]"
    }

    val clickableModifier = if (onClick != null) {
        modifier.clickable { onClick() }
    } else {
        modifier
    }

    Box(
        modifier = clickableModifier
            .background(bgTint)
            .border(1.2.dp, parsedColor)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = displayLabel,
            fontFamily = FontFamily.Monospace,
            fontSize = fontSize,
            fontWeight = FontWeight.Black,
            color = parsedColor,
            letterSpacing = 0.5.sp,
            maxLines = 1
        )
    }
}
