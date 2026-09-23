package com.example.data.local.converters

import androidx.compose.ui.graphics.Color
import com.example.data.local.entity.DocumentEntity
import com.example.data.model.Document
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DocumentMappers {
    fun toDomain(entity: DocumentEntity): Document {
        return Document(
            id = entity.id,
            fileName = entity.fileName,
            displayName = entity.displayName,
            localPath = entity.localPath,
            fileSize = entity.fileSize,
            fileSizeFormatted = formatFileSize(entity.fileSize),
            mimeType = entity.mimeType,
            pageCount = entity.pageCount,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt,
            lastOpenedAt = entity.lastOpenedAt,
            lastOpenedAtFormatted = formatTimestamp(entity.lastOpenedAt),
            lastOpenedPage = entity.lastOpenedPage,
            isFavorite = entity.isFavorite,
            isDeleted = entity.isDeleted,
            syncStatus = entity.syncStatus,
            remoteStorageRef = entity.remoteStorageRef,
            accentColor = Color(entity.accentColorHex.toULong())
        )
    }

    fun toEntity(domain: Document): DocumentEntity {
        return DocumentEntity(
            id = domain.id,
            fileName = domain.fileName,
            displayName = domain.displayName,
            localPath = domain.localPath,
            fileSize = domain.fileSize,
            mimeType = domain.mimeType,
            pageCount = domain.pageCount,
            createdAt = domain.createdAt,
            updatedAt = domain.updatedAt,
            lastOpenedAt = domain.lastOpenedAt,
            lastOpenedPage = domain.lastOpenedPage,
            isFavorite = domain.isFavorite,
            isDeleted = domain.isDeleted,
            syncStatus = domain.syncStatus,
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
