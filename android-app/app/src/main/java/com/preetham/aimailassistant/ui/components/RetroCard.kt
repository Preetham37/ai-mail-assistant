package com.preetham.aimailassistant.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.preetham.aimailassistant.ui.theme.RetroBlack
import com.preetham.aimailassistant.ui.theme.RetroShadowColor
import com.preetham.aimailassistant.ui.theme.RetroWhite

/**
 * 80s-90s Classic Cream / Night Charcoal container
 * with a 2dp solid border and hard offset drop-shadow.
 * Supports an optional subtle priority/level vertical accent stripe.
 */
@Composable
fun RetroBox(
    modifier: Modifier = Modifier,
    backgroundColor: Color = RetroWhite,
    borderColor: Color = RetroBlack,
    shadowColor: Color = RetroShadowColor,
    shadowOffset: Dp = 4.dp,
    cornerRadius: Dp = 4.dp,
    leftStripeColor: Color? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val shape = RoundedCornerShape(cornerRadius)

    Box(
        modifier = modifier.padding(end = shadowOffset, bottom = shadowOffset)
    ) {
        // Hard unblurred drop shadow (Classic 80s Macintosh depth)
        Box(
            modifier = Modifier
                .matchParentSize()
                .offset(x = shadowOffset, y = shadowOffset)
                .clip(shape)
                .background(shadowColor)
        )

        // Main Foreground Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(backgroundColor)
                .border(2.dp, borderColor, shape)
        ) {
            if (leftStripeColor != null) {
                Box(
                    modifier = Modifier.matchParentSize()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(4.dp)
                            .align(Alignment.CenterStart)
                            .background(leftStripeColor)
                    )
                }
            }
            content()
        }
    }
}
