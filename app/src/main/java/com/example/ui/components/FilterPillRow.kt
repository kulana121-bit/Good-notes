package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.MotionTokens
import com.example.ui.theme.OutfitFontFamily
import com.example.ui.theme.performTap
import com.example.ui.theme.rememberReducedMotion
import com.example.ui.viewmodel.NotesFilter

@Composable
fun FilterPillRow(
    selectedFilter: NotesFilter,
    onFilterSelected: (NotesFilter) -> Unit,
    totalNotesCount: Int = 23,
    modifier: Modifier = Modifier
) {
    val haptics = LocalHapticFeedback.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        NotesFilter.values().forEach { filter ->
            val isSelected = filter == selectedFilter
            FilterPill(
                filter = filter,
                isSelected = isSelected,
                count = if (filter == NotesFilter.ALL) totalNotesCount else null,
                onClick = {
                    if (filter != selectedFilter) {
                        haptics.performTap()
                        onFilterSelected(filter)
                    }
                }
            )
        }
    }
}

@Composable
fun FilterPill(
    filter: NotesFilter,
    isSelected: Boolean,
    count: Int? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val isReducedMotion = rememberReducedMotion()
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed && !isReducedMotion) 0.95f else 1.0f,
        animationSpec = MotionTokens.subtlePressSpring(),
        label = "pill_press_scale"
    )

    val backgroundColor by animateColorAsState(
        targetValue = when {
            isSelected && isDark -> Color(0xFF222226)
            isSelected && !isDark -> Color(0xFF141416)
            else -> Color.Transparent
        },
        animationSpec = MotionTokens.standardTween(),
        label = "pill_bg"
    )

    val contentColor by animateColorAsState(
        targetValue = when {
            isSelected -> Color.White
            isDark -> Color(0xFF9E9E9E)
            else -> Color(0xFF555555)
        },
        animationSpec = MotionTokens.standardTween(),
        label = "pill_text"
    )

    val borderColor by animateColorAsState(
        targetValue = if (isSelected) {
            if (isDark) Color(0xFF3E3E44) else Color(0xFF141416)
        } else {
            if (isDark) Color(0xFF2A2A2E) else Color(0xFFE2DBC8)
        },
        animationSpec = MotionTokens.standardTween(),
        label = "pill_border"
    )

    Box(
        modifier = modifier
            .scale(scale)
            .clip(RoundedCornerShape(32.dp))
            .background(backgroundColor)
            .border(1.dp, borderColor, RoundedCornerShape(32.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 18.dp, vertical = 10.dp)
            .testTag("filter_pill_${filter.name.lowercase()}"),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = filter.label,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontFamily = OutfitFontFamily,
                    fontSize = 14.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = contentColor
                )
            )

            // Optional note count badge inside the pill
            if (count != null) {
                val badgeBgColor by animateColorAsState(
                    targetValue = if (isSelected) Color(0x33FFFFFF) else Color(0x18000000),
                    animationSpec = MotionTokens.standardTween(),
                    label = "badge_bg"
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(badgeBgColor)
                        .padding(horizontal = 7.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "$count",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = OutfitFontFamily,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = contentColor
                        )
                    )
                }
            }
        }
    }
}
