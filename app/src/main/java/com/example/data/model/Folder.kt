package com.example.data.model

import androidx.compose.ui.graphics.Color
import com.example.ui.theme.NoteBlue
import com.example.ui.theme.NoteCoral
import com.example.ui.theme.NoteGreen
import com.example.ui.theme.NoteLavender
import com.example.ui.theme.NoteYellow

data class Folder(
    val id: String,
    val name: String,
    val noteCount: Int,
    val color: Color
)

val SampleFolders = listOf(
    Folder("personal", "Personal", 7, NoteCoral),
    Folder("study", "Study", 12, NoteYellow),
    Folder("work", "Work", 5, NoteBlue),
    Folder("ideas", "Ideas", 9, NoteGreen),
    Folder("projects", "Projects", 4, NoteLavender)
)
