package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Nightlight
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.MotionTokens
import com.example.ui.theme.NoteCoral
import com.example.ui.theme.NoteYellow
import com.example.ui.theme.OutfitFontFamily
import com.example.ui.theme.performTap
import com.example.ui.theme.rememberReducedMotion
import com.example.ui.viewmodel.NavDestination

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NavigationSheet(
    currentDestination: NavDestination,
    notesCount: Int,
    favoritesCount: Int,
    foldersCount: Int,
    documentsCount: Int = 0,
    trashCount: Int = 0,
    isDarkMode: Boolean,
    onSelectDestination: (NavDestination) -> Unit,
    onToggleDarkMode: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val haptics = LocalHapticFeedback.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 8.dp)
        ) {
            // Editorial Notebook Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "NOTES",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontFamily = OutfitFontFamily,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    )
                    Text(
                        text = "Digital Paper Workspace",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = OutfitFontFamily,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    )
                }

                // Dark mode toggle pill
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = if (isDarkMode) Icons.Outlined.Nightlight else Icons.Outlined.WbSunny,
                        contentDescription = "Theme",
                        tint = if (isDarkMode) NoteYellow else NoteCoral,
                        modifier = Modifier.size(20.dp)
                    )
                    Switch(
                        checked = isDarkMode,
                        onCheckedChange = {
                            haptics.performTap()
                            onToggleDarkMode(it)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = NoteYellow,
                            checkedTrackColor = Color(0xFF222226),
                            uncheckedThumbColor = Color(0xFF141414),
                            uncheckedTrackColor = Color(0xFFE5DECE)
                        ),
                        modifier = Modifier.testTag("dark_mode_switch")
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Navigation Destinations
            NavigationItem(
                title = "Home",
                icon = Icons.Outlined.Home,
                badge = null,
                isSelected = currentDestination == NavDestination.HOME,
                onClick = { onSelectDestination(NavDestination.HOME) }
            )

            NavigationItem(
                title = "All Notes",
                icon = Icons.Outlined.MenuBook,
                badge = "$notesCount",
                isSelected = currentDestination == NavDestination.ALL_NOTES,
                onClick = { onSelectDestination(NavDestination.ALL_NOTES) }
            )

            NavigationItem(
                title = "Folders",
                icon = Icons.Outlined.Folder,
                badge = "$foldersCount",
                isSelected = currentDestination == NavDestination.FOLDERS,
                onClick = { onSelectDestination(NavDestination.FOLDERS) }
            )

            NavigationItem(
                title = "Favorites",
                icon = Icons.Outlined.FavoriteBorder,
                badge = "$favoritesCount",
                isSelected = currentDestination == NavDestination.FAVORITES,
                onClick = { onSelectDestination(NavDestination.FAVORITES) }
            )

            NavigationItem(
                title = "Documents",
                icon = Icons.Outlined.Description,
                badge = if (documentsCount > 0) "$documentsCount" else "PDF",
                isSelected = currentDestination == NavDestination.DOCUMENTS,
                onClick = { onSelectDestination(NavDestination.DOCUMENTS) }
            )

            NavigationItem(
                title = "Trash",
                icon = Icons.Outlined.Delete,
                badge = if (trashCount > 0) "$trashCount" else null,
                isSelected = currentDestination == NavDestination.TRASH,
                onClick = { onSelectDestination(NavDestination.TRASH) }
            )

            NavigationItem(
                title = "Settings",
                icon = Icons.Outlined.Settings,
                badge = null,
                isSelected = currentDestination == NavDestination.SETTINGS,
                onClick = { onSelectDestination(NavDestination.SETTINGS) }
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun NavigationItem(
    title: String,
    icon: ImageVector,
    badge: String?,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val haptics = LocalHapticFeedback.current
    val isReducedMotion = rememberReducedMotion()
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed && !isReducedMotion) 0.97f else 1.0f,
        animationSpec = MotionTokens.subtlePressSpring(),
        label = "nav_item_scale"
    )

    val backgroundColor by animateColorAsState(
        targetValue = if (isSelected) {
            MaterialTheme.colorScheme.surfaceVariant
        } else {
            Color.Transparent
        },
        animationSpec = MotionTokens.standardTween(),
        label = "nav_item_bg"
    )

    val iconColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
        animationSpec = MotionTokens.standardTween(),
        label = "nav_item_icon_tint"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .clip(RoundedCornerShape(18.dp))
            .background(backgroundColor)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {
                    haptics.performTap()
                    onClick()
                }
            )
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .testTag("nav_item_${title.lowercase().replace(" ", "_")}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(22.dp)
            )

            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontFamily = OutfitFontFamily,
                    fontSize = 16.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            )
        }

        if (badge != null) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                    .padding(horizontal = 9.dp, vertical = 3.dp)
            ) {
                Text(
                    text = badge,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = OutfitFontFamily,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                )
            }
        }
    }
}
