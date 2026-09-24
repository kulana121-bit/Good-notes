package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
@Entity(
    tableName = "pending_sync_operations",
    indices = [
        Index(value = ["entityId"]),
        Index(value = ["entityType", "entityId"]),
        Index(value = ["createdAt"])
    ]
)
data class PendingSyncOperation(
    @PrimaryKey val operationId: String,
    val entityType: String,      // "NOTE", "FOLDER", "DOCUMENT"
    val entityId: String,
    val operationType: String,    // "CREATE", "UPDATE", "DELETE"
    val createdAt: Long = System.currentTimeMillis(),
    val retryCount: Int = 0,
    val lastError: String? = null,
    val payloadJson: String? = null
)
