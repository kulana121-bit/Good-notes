package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
@Entity(
    tableName = "documents",
    indices = [
        Index(value = ["userId"]),
        Index(value = ["userId", "isDeleted"]),
        Index(value = ["userId", "isFavorite"]),
        Index(value = ["userId", "lastOpenedAt"]),
        Index(value = ["userId", "driveFileId"]),
        Index(value = ["userId", "contentHash"]),
        Index(value = ["userId", "downloadState"]),
        Index(value = ["userId", "uploadState"])
    ]
)
data class DocumentEntity(
    @PrimaryKey val id: String,
    val userId: String = "",
    val fileName: String,
    val displayName: String,
    val localPath: String,
    val driveFileId: String? = null,
    val fileSize: Long,
    val mimeType: String = "application/pdf",
    val contentHash: String? = null,
    val pageCount: Int = 1,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val lastOpenedAt: Long = System.currentTimeMillis(),
    val lastOpenedPage: Int = 0,
    val lastSyncedAt: Long = 0L,
    val isFavorite: Boolean = false,
    val isDeleted: Boolean = false,
    val deletedAt: Long = 0L,
    val syncStatus: String = "PENDING",
    val downloadState: String = "AVAILABLE_OFFLINE", // AVAILABLE_OFFLINE, DOWNLOADING, CLOUD_ONLY, FAILED
    val uploadState: String = "IDLE", // IDLE, PENDING_UPLOAD, UPLOADING, UPLOADED, FAILED
    val thumbnailPath: String? = null,
    val remoteStorageRef: String? = null,
    val accentColorHex: Long = 0xFFFEEA9F
)
