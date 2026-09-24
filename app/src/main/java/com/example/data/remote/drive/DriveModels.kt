package com.example.data.remote.drive

/**
 * Storage quota information retrieved from Google Drive API v3.
 */
data class DriveStorageInfo(
    val usageBytes: Long,
    val limitBytes: Long?,
    val availableBytes: Long?,
    val userDisplayName: String? = null,
    val userEmail: String? = null
) {
    val usageFormatted: String get() = formatBytes(usageBytes)
    val limitFormatted: String get() = limitBytes?.let { formatBytes(it) } ?: "Unlimited"
    val availableFormatted: String get() = availableBytes?.let { formatBytes(it) } ?: "Available"

    val usagePercentage: Float? get() = if (limitBytes != null && limitBytes > 0L) {
        (usageBytes.toFloat() / limitBytes.toFloat()).coerceIn(0f, 1f)
    } else null

    companion object {
        fun formatBytes(bytes: Long): String {
            if (bytes < 1024L) return "$bytes B"
            val kb = bytes / 1024.0
            if (kb < 1024.0) return "%.1f KB".format(kb)
            val mb = kb / 1024.0
            if (mb < 1024.0) return "%.1f MB".format(mb)
            val gb = mb / 1024.0
            return "%.2f GB".format(gb)
        }
    }
}

/**
 * Identified or created folder structure in the user's personal Google Drive:
 * My Drive
 *   └── NOTES
 *         ├── Backup
 *         └── Documents
 */
data class DriveFolderStructure(
    val rootFolderId: String,
    val backupFolderId: String,
    val documentsFolderId: String
)

/**
 * Authorization state for Google Drive OAuth.
 */
sealed class DriveAuthState {
    object Disconnected : DriveAuthState()
    object Authorizing : DriveAuthState()
    data class Connected(
        val accountEmail: String,
        val accountName: String? = null
    ) : DriveAuthState()
    data class Error(val message: String) : DriveAuthState()
}

/**
 * Basic file metadata returned by Google Drive API.
 */
data class DriveFileInfo(
    val id: String,
    val name: String,
    val mimeType: String,
    val size: Long = 0L,
    val modifiedTime: String? = null,
    val md5Checksum: String? = null
)

/**
 * Result of Phase 7 connection and sanity integration test.
 */
data class DriveTestReport(
    val success: Boolean,
    val rootFolderId: String? = null,
    val backupFolderId: String? = null,
    val documentsFolderId: String? = null,
    val storageInfo: DriveStorageInfo? = null,
    val testFileUploaded: Boolean = false,
    val testFileDownloaded: Boolean = false,
    val testFileDeleted: Boolean = false,
    val message: String
)

/**
 * Result of uploading a document to Google Drive.
 */
data class DriveUploadResult(
    val driveFileId: String,
    val fileName: String,
    val md5Checksum: String? = null,
    val size: Long = 0L
)
