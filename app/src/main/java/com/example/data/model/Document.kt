package com.example.data.model

import androidx.compose.ui.graphics.Color

data class Document(
    val id: String,
    val fileName: String,
    val displayName: String,
    val localPath: String,
    val fileSize: Long,
    val fileSizeFormatted: String,
    val mimeType: String = "application/pdf",
    val pageCount: Int = 1,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val lastOpenedAt: Long = System.currentTimeMillis(),
    val lastOpenedAtFormatted: String = "",
    val lastOpenedPage: Int = 0,
    val isFavorite: Boolean = false,
    val isDeleted: Boolean = false,
    val syncStatus: String = "PENDING",
    val remoteStorageRef: String? = null,
    val accentColor: Color = Color(0xFFFEEA9F)
)
