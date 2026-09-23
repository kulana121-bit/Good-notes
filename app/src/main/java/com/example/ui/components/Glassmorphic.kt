package com.example.ui.components

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * High-performance Glassmorphism modifier that provides a frosted translucent look
 * with specular gradient borders. Gracefully falls back on low-end / older devices
 * using lightweight alpha blending without CPU overhead.
 */
fun Modifier.glassmorphism(
    shape: Shape = RoundedCornerShape(20.dp),
    blurRadius: Dp = 16.dp,
    isDark: Boolean = false,
    alpha: Float = if (isDark) 0.72f else 0.82f,
    borderWidth: Dp = 1.dp
): Modifier = this
    .clip(shape)
    .then(
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && blurRadius > 0.dp) {
            Modifier.graphicsLayer {
                renderEffect = android.graphics.RenderEffect
                    .createBlurEffect(
                        blurRadius.toPx(),
                        blurRadius.toPx(),
                        android.graphics.Shader.TileMode.CLAMP
                    )
                    .asComposeRenderEffect()
            }
        } else {
            Modifier
        }
    )
    .background(
        color = if (isDark) {
            Color(0xFF16161A).copy(alpha = alpha)
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
                    Color(0x44FFFFFF),
                    Color(0x11FFFFFF),
                    Color(0x05000000)
                )
            } else {
                listOf(
                    Color(0x88FFFFFF),
                    Color(0x33FFFFFF),
                    Color(0x10000000)
                )
            }
        ),
        shape = shape
    )
