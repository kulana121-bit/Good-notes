package com.example.data.model

import androidx.compose.ui.graphics.Color

data class Folder(
    val id: String,
    val name: String,
    val noteCount: Int = 0,
    val color: Color = Color(0xFFF3C08D)
)
