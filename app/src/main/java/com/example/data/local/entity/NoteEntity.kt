package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
@Entity(
    tableName = "notes",
    indices = [
        Index(value = ["userId"]),
        Index(value = ["userId", "isDeleted"]),
        Index(value = ["userId", "isFavorite"]),
        Index(value = ["userId", "folder"]),
        Index(value = ["userId", "updatedAt"])
    ]
)
data class NoteEntity(
    @PrimaryKey val id: String,
    val userId: String = "",
    val title: String,
    val content: String = "",
    val cardType: String = "CREAM_LECTURE",
    val folder: String = "Personal",
    val isFavorite: Boolean = false,
    val isTodo: Boolean = false,
    val isImportant: Boolean = false,
    val checklistJson: String = "[]",
    val noteCountText: String? = null,
    val sharedWithJson: String = "[]",
    val tagsJson: String = "[]",
    val isDeleted: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val syncStatus: String = "SYNCED",
    val syncedAt: Long = 0L,
    val remoteId: String? = null,
    val imageUri: String? = null,
    val audioUri: String? = null,
    val audioDurationMs: Long = 0L,
    val version: Long = 1L,
    val lastModifiedDeviceId: String = "",
    val deletedAt: Long = 0L
)
