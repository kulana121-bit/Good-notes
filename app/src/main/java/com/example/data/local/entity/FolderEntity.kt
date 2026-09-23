package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
@Entity(
    tableName = "folders",
    indices = [
        Index(value = ["name"], unique = true)
    ]
)
data class FolderEntity(
    @PrimaryKey val id: String,
    val name: String,
    val colorHex: Long = 0xFFEB7A53,
    val createdAt: Long = System.currentTimeMillis()
)
