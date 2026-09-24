package com.example.data.model

import androidx.compose.ui.graphics.Color

data class Document(
    val id: String,
    val fileName: String,
    val displayName: String,
    val localPath: String,
    val driveFileId: String? = null,
    val fileSize: Long,
    val fileSizeFormatted: String,
    val mimeType: String = "application/pdf",
    val contentHash: String? = null,
    val pageCount: Int = 1,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val lastOpenedAt: Long = System.currentTimeMillis(),
    val lastOpenedAtFormatted: String = "",
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
    val accentColor: Color = Color(0xFFFEEA9F)
) {
    val isAvailableOffline: Boolean
        get() = downloadState == "AVAILABLE_OFFLINE" && localPath.isNotBlank()

    val isCloudOnly: Boolean
        get() = downloadState == "CLOUD_ONLY" || localPath.isBlank()
}
