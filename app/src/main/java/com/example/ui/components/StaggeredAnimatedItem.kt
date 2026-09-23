package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.ui.theme.rememberReducedMotion
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * Premium staggered fade-in + slide-up motion wrapper for masonry note cards and list items.
 * Provides a tactile, editorial entrance animation when items are rendered on screen.
 */
@Composable
fun StaggeredAnimatedItem(
    index: Int,
    modifier: Modifier = Modifier,
    baseDelayMs: Int = 40,
    maxDelayMs: Int = 360,
    content: @Composable () -> Unit
) {
    val isReducedMotion = rememberReducedMotion()

    if (isReducedMotion) {
        Box(modifier = modifier) {
            content()
        }
        return
    }

    val alphaAnim = remember { Animatable(0f) }
    val offsetYAnim = remember { Animatable(28f) }

    LaunchedEffect(Unit) {
        val calculatedDelay = (index * baseDelayMs).coerceAtMost(maxDelayMs).toLong()
        if (calculatedDelay > 0) {
            delay(calculatedDelay)
        }

        // Parallel alpha fade-in and smooth spring slide-up
        alphaAnim.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing)
        )
    }

    LaunchedEffect(Unit) {
        val calculatedDelay = (index * baseDelayMs).coerceAtMost(maxDelayMs).toLong()
        if (calculatedDelay > 0) {
            delay(calculatedDelay)
        }

        offsetYAnim.animateTo(
            targetValue = 0f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioLowBouncy,
                stiffness = Spring.StiffnessMediumLow
            )
        )
    }

    Box(
        modifier = modifier
            .offset { IntOffset(0, offsetYAnim.value.dp.roundToPx()) }
            .alpha(alphaAnim.value)
    ) {
        content()
    }
}
