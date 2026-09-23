package com.example.data.model

import androidx.compose.ui.graphics.Color
import com.example.ui.theme.NoteBlue
import com.example.ui.theme.NoteCoral
import com.example.ui.theme.NoteGreen
import com.example.ui.theme.NoteLavender
import com.example.ui.theme.NoteWarmCream
import com.example.ui.theme.NoteYellow

enum class VisualCardType(
    val backgroundColor: Color,
    val contentColor: Color = Color(0xFF151515)
) {
    CORAL_TASK(backgroundColor = NoteCoral, contentColor = Color(0xFF1A1A1A)),
    YELLOW_MEDIA(backgroundColor = NoteYellow, contentColor = Color(0xFF1A1A1A)),
    CREAM_LECTURE(backgroundColor = NoteWarmCream, contentColor = Color(0xFF1A1A1A)),
    GREEN_NOTE(backgroundColor = NoteGreen, contentColor = Color(0xFF1A1A1A)),
    BLUE_NOTE(backgroundColor = NoteBlue, contentColor = Color(0xFF1A1A1A)),
    LAVENDER_NOTE(backgroundColor = NoteLavender, contentColor = Color(0xFF1A1A1A))
}
