package com.example.data.local.converters

import androidx.compose.ui.graphics.Color
import com.example.data.local.entity.DocumentEntity
import com.example.data.model.Document
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DocumentMappers {
    fun toDomain(entity: DocumentEntity): Document {
        val hex = entity.accentColorHex
        val color = if (hex ushr 32 == 0L) {
            Color(hex)
        } else {
            Color(hex.toULong())
        }

        return Document(
            id = entity.id,
            fileName = entity.fileName,
            displayName = entity.displayName,
            localPath = entity.localPath,
            driveFileId = entity.driveFileId,
            fileSize = entity.fileSize,
            fileSizeFormatted = formatFileSize(entity.fileSize),
            mimeType = entity.mimeType,
            contentHash = entity.contentHash,
            pageCount = entity.pageCount,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt,
            lastOpenedAt = entity.lastOpenedAt,
            lastOpenedAtFormatted = formatTimestamp(entity.lastOpenedAt),
            lastOpenedPage = entity.lastOpenedPage,
            lastSyncedAt = entity.lastSyncedAt,
            isFavorite = entity.isFavorite,
            isDeleted = entity.isDeleted,
            deletedAt = entity.deletedAt,
            syncStatus = entity.syncStatus,
            downloadState = entity.downloadState,
            uploadState = entity.uploadState,
            thumbnailPath = entity.thumbnailPath,
            remoteStorageRef = entity.remoteStorageRef,
            accentColor = color
        )
    }

    fun toEntity(domain: Document): DocumentEntity {
        return DocumentEntity(
            id = domain.id,
            fileName = domain.fileName,
            displayName = domain.displayName,
            localPath = domain.localPath,
            driveFileId = domain.driveFileId,
            fileSize = domain.fileSize,
            mimeType = domain.mimeType,
            contentHash = domain.contentHash,
            pageCount = domain.pageCount,
            createdAt = domain.createdAt,
            updatedAt = domain.updatedAt,
            lastOpenedAt = domain.lastOpenedAt,
            lastOpenedPage = domain.lastOpenedPage,
            lastSyncedAt = domain.lastSyncedAt,
            isFavorite = domain.isFavorite,
            isDeleted = domain.isDeleted,
            deletedAt = domain.deletedAt,
            syncStatus = domain.syncStatus,
            downloadState = domain.downloadState,
            uploadState = domain.uploadState,
            thumbnailPath = domain.thumbnailPath,
            remoteStorageRef = domain.remoteStorageRef,
            accentColorHex = domain.accentColor.value.toLong()
        )
    }

    fun formatFileSize(bytes: Long): String {
        return when {
            bytes >= 1024 * 1024 -> String.format(Locale.US, "%.1f MB", bytes.toDouble() / (1024 * 1024))
            bytes >= 1024 -> String.format(Locale.US, "%.1f KB", bytes.toDouble() / 1024)
            else -> "$bytes B"
        }
    }

    fun formatTimestamp(timestamp: Long): String {
        val diff = System.currentTimeMillis() - timestamp
        val oneMinute = 60 * 1000L
        val oneHour = 60 * oneMinute
        val oneDay = 24 * oneHour

        return when {
            diff < oneMinute -> "Just now"
            diff < oneHour -> "${diff / oneMinute}m ago"
            diff < oneDay -> "${diff / oneHour}h ago"
            diff < 2 * oneDay -> "Yesterday"
            diff < 7 * oneDay -> "${diff / oneDay} days ago"
            else -> SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(timestamp))
        }
    }
}
