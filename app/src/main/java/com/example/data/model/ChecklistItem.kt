package com.example.data.model

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class ChecklistItem(
    val id: String,
    val text: String,
    val isCompleted: Boolean = false
)
