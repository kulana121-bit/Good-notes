package com.example.ui.theme

import android.content.Context
import android.provider.Settings
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext

/**
 * Centralized Motion System for NOTES
 * Provides natural, physical, tactile animations with consistent timing,
 * spring dynamics, and reduced-motion accessibility support.
 */
object MotionTokens {
    // Timing constants (ms)
    const val DurationMicro = 140
    const val DurationSmall = 220
    const val DurationStandard = 300
    const val DurationEmphasized = 400
    const val DurationLong = 480

    // Easing curves
    val EaseOutQuart = CubicBezierEasing(0.25f, 1f, 0.5f, 1f)
    val EaseOutCubic = CubicBezierEasing(0.33f, 1f, 0.68f, 1f)
    val EaseInOutCubic = CubicBezierEasing(0.65f, 0f, 0.35f, 1f)
    val StandardDecelerate = LinearOutSlowInEasing
    val StandardAccelerate = FastOutLinearInEasing
    val EmphasizedEasing = FastOutSlowInEasing

    // Spring Specifications for Physical Tactile Feel
    fun <T> snappySpring(
        dampingRatio: Float = Spring.DampingRatioMediumBouncy,
        stiffness: Float = Spring.StiffnessMedium
    ): androidx.compose.animation.core.SpringSpec<T> = spring(
        dampingRatio = dampingRatio,
        stiffness = stiffness
    )

    fun <T> subtlePressSpring(): androidx.compose.animation.core.SpringSpec<T> = spring(
        dampingRatio = 0.78f,
        stiffness = Spring.StiffnessMediumLow
    )

    fun <T> gentleSpring(): androidx.compose.animation.core.SpringSpec<T> = spring(
        dampingRatio = 0.88f,
        stiffness = Spring.StiffnessLow
    )

    fun <T> favoritePopSpring(): androidx.compose.animation.core.SpringSpec<T> = spring(
        dampingRatio = 0.65f,
        stiffness = Spring.StiffnessMediumLow
    )

    fun <T> bouncySpring(): androidx.compose.animation.core.SpringSpec<T> = spring(
        dampingRatio = 0.65f,
        stiffness = Spring.StiffnessMediumLow
    )

    // Standard Tweens
    fun <T> fastTween(): androidx.compose.animation.core.TweenSpec<T> = tween(
        durationMillis = DurationMicro,
        easing = EaseOutQuart
    )

    fun <T> standardTween(): androidx.compose.animation.core.TweenSpec<T> = tween(
        durationMillis = DurationStandard,
        easing = EaseOutCubic
    )

    fun <T> emphasizedTween(): androidx.compose.animation.core.TweenSpec<T> = tween(
        durationMillis = DurationEmphasized,
        easing = EmphasizedEasing
    )
}

/**
 * Checks whether user has enabled reduced motion in device accessibility settings.
 */
@Composable
fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        isReducedMotionEnabled(context)
    }
}

private fun isReducedMotionEnabled(context: Context): Boolean {
    return try {
        val animationScale = Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1.0f
        )
        val transitionScale = Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.TRANSITION_ANIMATION_SCALE,
            1.0f
        )
        animationScale == 0f || transitionScale == 0f
    } catch (_: Exception) {
        false
    }
}

/**
 * Tactile haptic feedback triggers
 */
fun HapticFeedback.performTap() {
    try {
        performHapticFeedback(HapticFeedbackType.TextHandleMove)
    } catch (_: Exception) {}
}

fun HapticFeedback.performConfirm() {
    try {
        performHapticFeedback(HapticFeedbackType.LongPress)
    } catch (_: Exception) {}
}
