package com.example.data.model

import androidx.compose.ui.graphics.Color

val InitialNotes: List<Note> = emptyList()

val SampleFolders = listOf(
    Folder(id = "folder_personal", name = "Personal", noteCount = 0, color = Color(0xFFF3C08D)),
    Folder(id = "folder_work", name = "Work", noteCount = 0, color = Color(0xFF9887DB)),
    Folder(id = "folder_ideas", name = "Ideas", noteCount = 0, color = Color(0xFFA8D672)),
    Folder(id = "folder_study", name = "Study", noteCount = 0, color = Color(0xFFFEEA9F))
)
