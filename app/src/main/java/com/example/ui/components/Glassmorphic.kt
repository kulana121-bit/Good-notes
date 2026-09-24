package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * High-performance Glassmorphism modifier that provides a frosted translucent surface
 * with specular gradient borders. Ensures all contained buttons, icons, and text remain
 * crisp, razor-sharp, and fully visible without content blur.
 */
fun Modifier.glassmorphism(
    shape: Shape = RoundedCornerShape(20.dp),
    blurRadius: Dp = 16.dp,
    isDark: Boolean = false,
    alpha: Float = if (isDark) 0.88f else 0.92f,
    borderWidth: Dp = 1.dp
): Modifier = this
    .clip(shape)
    .background(
        color = if (isDark) {
            Color(0xFF222227).copy(alpha = alpha)
        } else {
            Color(0xFFFFFFFF).copy(alpha = alpha)
        },
        shape = shape
    )
    .border(
        width = borderWidth,
        brush = Brush.linearGradient(
            colors = if (isDark) {
                listOf(
                    Color(0x48FFFFFF),
                    Color(0x18FFFFFF),
                    Color(0x08FFFFFF)
                )
            } else {
                listOf(
                    Color(0x99FFFFFF),
                    Color(0x44FFFFFF),
                    Color(0x15000000)
                )
            }
        ),
        shape = shape
    )

