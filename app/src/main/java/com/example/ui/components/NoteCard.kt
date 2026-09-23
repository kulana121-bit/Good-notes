package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    modifier: Modifier = Modifier
) {
    val isReducedMotion = rememberReducedMotion()
    val shape = RoundedCornerShape(28.dp)
    val cardInteraction = remember { MutableInteractionSource() }
    val isPressed by cardInteraction.collectIsPressedAsState()

    val cardScale by animateFloatAsState(
        targetValue = if (isPressed && !isReducedMotion) 0.975f else 1.0f,
        animationSpec = MotionTokens.subtlePressSpring(),
        label = "note_card_scale"
    )

    val elevation by animateDpAsState(
        targetValue = if (isPressed && !isReducedMotion) 2.dp else 6.dp,
        animationSpec = MotionTokens.subtlePressSpring(),
        label = "note_card_elevation"
    )

    Box(
        modifier = modifier
            .scale(cardScale)
            .shadow(
                elevation = elevation,
                shape = shape,
                ambientColor = Color(0x33000000),
                spotColor = Color(0x33000000)
            )
            .clip(shape)
            .background(note.cardType.backgroundColor)
            .clickable(
                interactionSource = cardInteraction,
                indication = null,
                onClick = onClick
            )
            .testTag("note_card_${note.id}")
    ) {
        when (note.cardType) {
            VisualCardType.CORAL_TASK -> {
                CoralTaskCardContent(
                    note = note,
                    onToggleFavorite = { onToggleFavorite(note.id) },
                    onToggleChecklistItem = onToggleChecklistItem
                )
            }
            VisualCardType.YELLOW_MEDIA -> {
                YellowMediaCardContent(
                    note = note,
                    onToggleFavorite = { onToggleFavorite(note.id) }
                )
            }
            VisualCardType.CREAM_LECTURE -> {
                CreamLectureCardContent(
                    note = note,
                    onToggleFavorite = { onToggleFavorite(note.id) }
                )
            }
            else -> {
                StandardNoteCardContent(
                    note = note,
                    onToggleFavorite = { onToggleFavorite(note.id) }
                )
            }
        }
    }
}

@Composable
private fun CoralTaskCardContent(
    note: Note,
    onToggleFavorite: () -> Unit,
    onToggleChecklistItem: ((String, String) -> Unit)?
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Title & Favorite row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Text(
                text = note.title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontFamily = OutfitFontFamily,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 22.sp,
                    color = Color(0xFF161616)
                ),
                modifier = Modifier.weight(1f)
            )

            FavoriteIcon(
                isFavorite = note.isFavorite,
                tint = Color(0xFF161616),
                onClick = onToggleFavorite
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Checklist items
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            note.checklist.take(4).forEach { item ->
                ChecklistRow(
                    item = item,
                    onToggle = { onToggleChecklistItem?.invoke(note.id, item.id) }
                )
            }
        }
    }
}

@Composable
private fun ChecklistRow(
    item: ChecklistItem,
    onToggle: () -> Unit
) {
    val haptics = LocalHapticFeedback.current
    val isReducedMotion = rememberReducedMotion()
    val checkAnim = remember { Animatable(1.0f) }
    val scope = rememberCoroutineScope()

    val textColor by animateColorAsState(
        targetValue = if (item.isCompleted) Color(0x991E1E1E) else Color(0xFF1E1E1E),
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
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Indicator circle
        Box(
            modifier = Modifier
                .scale(checkAnim.value)
                .size(20.dp)
                .clip(CircleShape)
                .background(checkBgColor)
                .border(
                    width = 2.dp,
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
                    modifier = Modifier.size(12.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(10.dp))

        Text(
            text = item.text,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = OutfitFontFamily,
                fontSize = 14.sp,
                fontWeight = if (item.isCompleted) FontWeight.Normal else FontWeight.Medium,
                textDecoration = if (item.isCompleted) TextDecoration.LineThrough else TextDecoration.None,
                color = textColor
            )
        )
    }
}

@Composable
private fun YellowMediaCardContent(
    note: Note,
    onToggleFavorite: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Title & Favorite row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = note.title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = OutfitFontFamily,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF161616)
                    )
                )

                Text(
                    text = note.updatedAtText,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = OutfitFontFamily,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0x99161616)
                    )
                )
            }

            FavoriteIcon(
                isFavorite = note.isFavorite,
                tint = Color(0xFF161616),
                onClick = onToggleFavorite
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Tactile artistic portrait silhouette illustration (matching reference screenshot)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(110.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFFE2BE38)),
            contentAlignment = Alignment.BottomCenter
        ) {
            Canvas(modifier = Modifier.size(120.dp, 105.dp)) {
                val width = size.width
                val height = size.height

                // Elegant editorial portrait profile silhouette
                val profilePath = Path().apply {
                    moveTo(width * 0.5f, height)
                    cubicTo(width * 0.45f, height * 0.7f, width * 0.35f, height * 0.6f, width * 0.35f, height * 0.45f)
                    cubicTo(width * 0.35f, height * 0.25f, width * 0.55f, height * 0.15f, width * 0.65f, height * 0.25f)
                    cubicTo(width * 0.75f, height * 0.35f, width * 0.72f, height * 0.5f, width * 0.68f, height * 0.6f)
                    cubicTo(width * 0.65f, height * 0.75f, width * 0.75f, height * 0.9f, width * 0.8f, height)
                    close()
                }

                drawPath(
                    path = profilePath,
                    brush = Brush.verticalGradient(
                        colors = listOf(Color(0xFF4A3423), Color(0xFF2A1C14))
                    )
                )

                // Warm sunset rim lighting highlight
                drawCircle(
                    color = Color(0x44FFFFFF),
                    radius = 24.dp.toPx(),
                    center = Offset(width * 0.6f, height * 0.35f)
                )
            }
        }
    }
}

@Composable
private fun CreamLectureCardContent(
    note: Note,
    onToggleFavorite: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 18.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.weight(1f)
        ) {
            // Lecture avatar / emoji circular badge (matching reference screenshot)
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFEBDFAE)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "📝",
                    fontSize = 20.sp
                )
            }

            Column {
                if (note.noteCountText != null) {
                    Text(
                        text = note.noteCountText,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = OutfitFontFamily,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0x99181818)
                        )
                    )
                }

                Text(
                    text = note.title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = OutfitFontFamily,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF181818)
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        FavoriteIcon(
            isFavorite = note.isFavorite,
            tint = Color(0xFF181818),
            onClick = onToggleFavorite
        )
    }
}

@Composable
private fun StandardNoteCardContent(
    note: Note,
    onToggleFavorite: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Text(
                text = note.title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontFamily = OutfitFontFamily,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 22.sp,
                    color = Color(0xFF181818)
                ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            FavoriteIcon(
                isFavorite = note.isFavorite,
                tint = Color(0xFF181818),
                onClick = onToggleFavorite
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (note.body.isNotEmpty()) {
            Text(
                text = note.body,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = OutfitFontFamily,
                    fontSize = 13.sp,
                    lineHeight = 17.sp,
                    color = Color(0xCC181818)
                ),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = note.updatedAtText,
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = OutfitFontFamily,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0x99181818)
            )
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
                    // Micro-interaction: quick pop and subtle rotation
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
