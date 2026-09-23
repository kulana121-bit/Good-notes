package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.model.ChecklistItem
import com.example.data.model.Note
import com.example.data.model.VisualCardType
import com.example.ui.theme.MotionTokens
import com.example.ui.theme.OutfitFontFamily
import com.example.ui.theme.performTap
import com.example.ui.theme.rememberReducedMotion
import kotlinx.coroutines.launch

@Composable
fun NoteCard(
    note: Note,
    onClick: () -> Unit,
    onToggleFavorite: (String) -> Unit,
    onToggleChecklistItem: ((String, String) -> Unit)? = null,
    onDeleteNote: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val isReducedMotion = rememberReducedMotion()
    val shape = RoundedCornerShape(24.dp)
    val cardInteraction = remember { MutableInteractionSource() }
    val isPressed by cardInteraction.collectIsPressedAsState()

    val cardScale by animateFloatAsState(
        targetValue = if (isPressed && !isReducedMotion) 0.975f else 1.0f,
        animationSpec = MotionTokens.subtlePressSpring(),
        label = "note_card_scale"
    )

    val elevation by animateDpAsState(
        targetValue = if (isPressed && !isReducedMotion) 2.dp else 4.dp,
        animationSpec = MotionTokens.subtlePressSpring(),
        label = "note_card_elevation"
    )

    val (cardBgColor, contentColor) = when (note.cardType) {
        VisualCardType.CORAL_TASK -> Color(0xFFEB7A53) to Color(0xFF1E1E1E)
        VisualCardType.YELLOW_MEDIA -> Color(0xFFFEEA9F) to Color(0xFF1E1E1E)
        VisualCardType.CREAM_LECTURE -> Color(0xFFFBF7EE) to Color(0xFF1E1E1E)
        VisualCardType.LAVENDER_NOTE -> Color(0xFF9887DB) to Color(0xFF1E1E1E)
        VisualCardType.GREEN_NOTE -> Color(0xFFA8D672) to Color(0xFF1E1E1E)
        VisualCardType.BLUE_NOTE -> Color(0xFF7CC4FA) to Color(0xFF1E1E1E)
    }

    Box(
        modifier = modifier
            .scale(cardScale)
            .shadow(
                elevation = elevation,
                shape = shape,
                ambientColor = Color(0x22000000),
                spotColor = Color(0x22000000)
            )
            .clip(shape)
            .background(cardBgColor)
            .clickable(
                interactionSource = cardInteraction,
                indication = null,
                onClick = onClick
            )
            .testTag("note_card_${note.id}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            // Top row: Title + Actions (Favorite + More Menu)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = note.title.ifBlank { "Untitled Note" },
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontFamily = OutfitFontFamily,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = contentColor
                        ),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    Text(
                        text = note.updatedAtText,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = OutfitFontFamily,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = contentColor.copy(alpha = 0.6f)
                        )
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    FavoriteIcon(
                        isFavorite = note.isFavorite,
                        tint = contentColor,
                        onClick = { onToggleFavorite(note.id) }
                    )

                    if (onDeleteNote != null) {
                        var menuExpanded by remember { mutableStateOf(false) }
                        Box {
                            IconButton(
                                onClick = { menuExpanded = true },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.MoreVert,
                                    contentDescription = "Options",
                                    tint = contentColor.copy(alpha = 0.7f),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            DropdownMenu(
                                expanded = menuExpanded,
                                onDismissRequest = { menuExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Move to Trash") },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Outlined.DeleteOutline,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    },
                                    onClick = {
                                        menuExpanded = false
                                        onDeleteNote(note.id)
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Attached Image preview
            if (!note.imageUri.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                val context = LocalContext.current
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(note.imageUri)
                        .crossfade(true)
                        .build(),
                    contentDescription = "Attached image",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .clip(RoundedCornerShape(16.dp))
                )
            }

            // Voice Note Audio Badge
            if (!note.audioUri.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(contentColor.copy(alpha = 0.12f))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.GraphicEq,
                        contentDescription = "Voice note",
                        tint = contentColor,
                        modifier = Modifier.size(16.dp)
                    )
                    val durationSec = (note.audioDurationMs / 1000).coerceAtLeast(1)
                    Text(
                        text = "Voice Note (${durationSec}s)",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = OutfitFontFamily,
                            fontWeight = FontWeight.SemiBold,
                            color = contentColor
                        )
                    )
                }
            }

            // Checklist items if present
            if (note.checklist.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    note.checklist.take(3).forEach { item ->
                        ChecklistItemRow(
                            item = item,
                            onToggle = { onToggleChecklistItem?.invoke(note.id, item.id) }
                        )
                    }
                    if (note.checklist.size > 3) {
                        Text(
                            text = "+ ${note.checklist.size - 3} more tasks",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = OutfitFontFamily,
                                color = contentColor.copy(alpha = 0.6f),
                                fontWeight = FontWeight.Medium
                            )
                        )
                    }
                }
            } else if (note.body.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = note.body,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = OutfitFontFamily,
                        fontSize = 13.sp,
                        lineHeight = 17.sp,
                        color = contentColor.copy(alpha = 0.85f)
                    ),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun ChecklistItemRow(
    item: ChecklistItem,
    onToggle: () -> Unit
) {
    val haptics = LocalHapticFeedback.current
    val isReducedMotion = rememberReducedMotion()
    val checkAnim = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()

    val textColor by animateColorAsState(
        targetValue = if (item.isCompleted) Color(0x881E1E1E) else Color(0xFF1E1E1E),
        animationSpec = MotionTokens.fastTween(),
        label = "checklist_text_color"
    )

    val checkBgColor by animateColorAsState(
        targetValue = if (item.isCompleted) Color(0xFF1E1E1E) else Color.Transparent,
        animationSpec = MotionTokens.fastTween(),
        label = "checklist_bg_color"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                onClick = {
                    haptics.performTap()
                    if (!isReducedMotion) {
                        scope.launch {
                            checkAnim.animateTo(0.8f, MotionTokens.fastTween())
                            checkAnim.animateTo(1.0f, MotionTokens.favoritePopSpring())
                        }
                    }
                    onToggle()
                }
            )
            .padding(vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .scale(checkAnim.value)
                .size(18.dp)
                .clip(CircleShape)
                .background(checkBgColor)
                .border(
                    width = 1.5.dp,
                    color = if (item.isCompleted) Color(0xFF1E1E1E) else Color(0x661E1E1E),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            if (item.isCompleted) {
                Icon(
                    imageVector = Icons.Outlined.Check,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(11.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = item.text,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = OutfitFontFamily,
                fontSize = 13.sp,
                fontWeight = if (item.isCompleted) FontWeight.Normal else FontWeight.Medium,
                textDecoration = if (item.isCompleted) TextDecoration.LineThrough else TextDecoration.None,
                color = textColor
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun FavoriteIcon(
    isFavorite: Boolean,
    tint: Color,
    onClick: () -> Unit
) {
    val haptics = LocalHapticFeedback.current
    val isReducedMotion = rememberReducedMotion()
    val scope = rememberCoroutineScope()
    val scaleAnim = remember { Animatable(1f) }
    val rotateAnim = remember { Animatable(0f) }

    val heartColor by animateColorAsState(
        targetValue = if (isFavorite) Color(0xFFE11D48) else tint.copy(alpha = 0.6f),
        animationSpec = MotionTokens.fastTween(),
        label = "heart_color"
    )

    IconButton(
        onClick = {
            haptics.performTap()
            if (!isReducedMotion) {
                scope.launch {
                    scaleAnim.snapTo(0.75f)
                    rotateAnim.snapTo(if (isFavorite) -10f else 12f)
                    launch {
                        scaleAnim.animateTo(1.22f, MotionTokens.fastTween())
                        scaleAnim.animateTo(1.0f, MotionTokens.favoritePopSpring())
                    }
                    launch {
                        rotateAnim.animateTo(0f, MotionTokens.favoritePopSpring())
                    }
                }
            }
            onClick()
        },
        modifier = Modifier
            .size(28.dp)
            .scale(scaleAnim.value)
            .rotate(rotateAnim.value)
    ) {
        Icon(
            imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
            contentDescription = if (isFavorite) "Remove from favorites" else "Add to favorites",
            tint = heartColor,
            modifier = Modifier.size(18.dp)
        )
    }
}
