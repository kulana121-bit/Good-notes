package com.example.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.ui.theme.MotionTokens
import com.example.ui.theme.performTap
import com.example.ui.theme.rememberReducedMotion

@Composable
fun FloatingActionCapsule(
    onAddClick: () -> Unit,
    onMicClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalHapticFeedback.current
    val isReducedMotion = rememberReducedMotion()

    val addInteraction = remember { MutableInteractionSource() }
    val isAddPressed by addInteraction.collectIsPressedAsState()
    val addScale by animateFloatAsState(
        targetValue = if (isAddPressed && !isReducedMotion) 0.90f else 1.0f,
        animationSpec = MotionTokens.subtlePressSpring(),
        label = "add_scale"
    )

    val micInteraction = remember { MutableInteractionSource() }
    val isMicPressed by micInteraction.collectIsPressedAsState()
    val micScale by animateFloatAsState(
        targetValue = if (isMicPressed && !isReducedMotion) 0.88f else 1.0f,
        animationSpec = MotionTokens.subtlePressSpring(),
        label = "mic_scale"
    )

    val elevation by animateDpAsState(
        targetValue = if ((isAddPressed || isMicPressed) && !isReducedMotion) 6.dp else 12.dp,
        animationSpec = MotionTokens.subtlePressSpring(),
        label = "capsule_elevation"
    )

    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(bottom = 20.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .shadow(
                    elevation = elevation,
                    shape = RoundedCornerShape(36.dp),
                    ambientColor = Color(0x66000000),
                    spotColor = Color(0x66000000)
                )
                .glassmorphism(
                    shape = RoundedCornerShape(36.dp),
                    blurRadius = 20.dp,
                    isDark = isDark,
                    alpha = if (isDark) 0.85f else 0.88f
                )
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Primary Dominant circular "+" Button
            Box(
                modifier = Modifier
                    .scale(addScale)
                    .size(54.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF0F0F10))
                    .clickable(
                        interactionSource = addInteraction,
                        indication = null,
                        onClick = {
                            haptics.performTap()
                            onAddClick()
                        }
                    )
                    .testTag("floating_add_button"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "New Note",
                    tint = Color.White,
                    modifier = Modifier.size(26.dp)
                )
            }

            // Secondary Translucent Microphone Capsule Control
            Box(
                modifier = Modifier
                    .scale(micScale)
                    .size(width = 48.dp, height = 48.dp)
                    .clip(CircleShape)
                    .background(if (isDark) Color(0x33FFFFFF) else Color(0x44FFFFFF))
                    .clickable(
                        interactionSource = micInteraction,
                        indication = null,
                        onClick = {
                            haptics.performTap()
                            onMicClick()
                        }
                    )
                    .testTag("floating_mic_button"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = "Voice Note",
                    tint = if (isDark) Color(0xFFDDDDDD) else Color(0xFF222222),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
