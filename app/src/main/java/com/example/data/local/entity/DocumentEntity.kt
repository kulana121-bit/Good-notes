package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "documents",
    indices = [
        Index(value = ["isDeleted"]),
        Index(value = ["isFavorite"]),
        Index(value = ["lastOpenedAt"])
    ]
)
data class DocumentEntity(
    @PrimaryKey val id: String,
    val fileName: String,
    val displayName: String,
    val localPath: String,
    val fileSize: Long,
    val mimeType: String = "application/pdf",
    val pageCount: Int = 1,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val lastOpenedAt: Long = System.currentTimeMillis(),
    val lastOpenedPage: Int = 0,
    val isFavorite: Boolean = false,
    val isDeleted: Boolean = false,
    val syncStatus: String = "PENDING",
    val remoteStorageRef: String? = null,
    val accentColorHex: Long = 0xFFFEEA9F
)
