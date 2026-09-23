package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
@Entity(
    tableName = "notes",
    indices = [
        Index(value = ["isDeleted"]),
        Index(value = ["isFavorite"]),
        Index(value = ["folder"]),
        Index(value = ["updatedAt"])
    ]
)
data class NoteEntity(
    @PrimaryKey val id: String,
    val title: String,
    val content: String = "",
    val folder: String = "Personal",
    val isFavorite: Boolean = false,
    val isDeleted: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val color: String = "CREAM_LECTURE",
    val noteType: String = "TEXT",
    val isTodo: Boolean = false,
    val isImportant: Boolean = false,
    val checklistJson: String = "[]",
    val sharedWithJson: String = "[]",
    val tagsJson: String = "[]",
    val noteCountText: String? = null,
    val syncStatus: String = "PENDING",
    val syncedAt: Long = 0L,
    val remoteId: String? = null
)
