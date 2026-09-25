package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
@Entity(
    tableName = "folders",
    indices = [
        Index(value = ["userId"]),
        Index(value = ["userId", "name"], unique = true)
    ]
)
data class FolderEntity(
    @PrimaryKey val id: String,
    val userId: String = "",
    val name: String,
    val colorHex: Long = 0xFFEB7A53,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false,
    val syncStatus: String = "SYNCED",
    val version: Long = 1L,
    val lastModifiedDeviceId: String = ""
)
