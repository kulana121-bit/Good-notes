package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.MotionTokens
import com.example.ui.theme.OutfitFontFamily
import com.example.ui.theme.performTap
import com.example.ui.theme.rememberReducedMotion

@Composable
fun NotesHeader(
    title: String = "My\nNotes",
    onMenuClick: () -> Unit,
    onSearchClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val haptics = LocalHapticFeedback.current
    val isReducedMotion = rememberReducedMotion()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        // Large expressive editorial title
        Text(
            text = title,
            style = MaterialTheme.typography.displayLarge.copy(
                fontFamily = OutfitFontFamily,
                fontSize = 38.sp,
                lineHeight = 40.sp,
                color = MaterialTheme.colorScheme.onBackground
            ),
            modifier = Modifier.testTag("notes_header_title")
        )

        // Action buttons (Search + 4-dot Menu)
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (onSearchClick != null) {
                val searchInteraction = remember { MutableInteractionSource() }
                val isSearchPressed by searchInteraction.collectIsPressedAsState()
                val searchScale by animateFloatAsState(
                    targetValue = if (isSearchPressed && !isReducedMotion) 0.88f else 1.0f,
                    animationSpec = MotionTokens.subtlePressSpring(),
                    label = "search_btn_scale"
                )

                Box(
                    modifier = Modifier
                        .scale(searchScale)
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f))
                        .clickable(
                            interactionSource = searchInteraction,
                            indication = null,
                            onClick = {
                                haptics.performTap()
                                onSearchClick()
                            }
                        )
                        .testTag("search_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Search,
                        contentDescription = "Search notes",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Compact 4-dot Menu Button (RonDesignLab style)
            val menuInteraction = remember { MutableInteractionSource() }
            val isMenuPressed by menuInteraction.collectIsPressedAsState()
            val menuScale by animateFloatAsState(
                targetValue = if (isMenuPressed && !isReducedMotion) 0.88f else 1.0f,
                animationSpec = MotionTokens.subtlePressSpring(),
                label = "menu_btn_scale"
            )

            Box(
                modifier = Modifier
                    .scale(menuScale)
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f))
                    .clickable(
                        interactionSource = menuInteraction,
                        indication = null,
                        onClick = {
                            haptics.performTap()
                            onMenuClick()
                        }
                    )
                    .testTag("menu_button"),
                contentAlignment = Alignment.Center
            ) {
                // 2x2 grid of dots
                Column(
                    verticalArrangement = Arrangement.spacedBy(3.5.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(3.5.dp)) {
                        Dot(color = MaterialTheme.colorScheme.onSurface)
                        Dot(color = MaterialTheme.colorScheme.onSurface)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(3.5.dp)) {
                        Dot(color = MaterialTheme.colorScheme.onSurface)
                        Dot(color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }
    }
}

@Composable
private fun Dot(color: Color) {
    Box(
        modifier = Modifier
            .size(3.5.dp)
            .clip(CircleShape)
            .background(color)
    )
}
