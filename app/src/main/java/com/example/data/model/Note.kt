package com.example.data.model

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class Note(
    val id: String,
    val title: String,
    val body: String = "",
    val cardType: VisualCardType = VisualCardType.CREAM_LECTURE,
    val folder: String = "Personal",
    val updatedAtText: String = "Just now",
    val isFavorite: Boolean = false,
    val isTodo: Boolean = false,
    val isImportant: Boolean = false,
    val checklist: List<ChecklistItem> = emptyList(),
    val noteCountText: String? = null,
    val sharedWith: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val isDeleted: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val imageUri: String? = null,
    val audioUri: String? = null,
    val audioDurationMs: Long = 0L
)
