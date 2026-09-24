package com.example.data.backup

import com.example.data.local.entity.DocumentEntity
import com.example.data.local.entity.FolderEntity
import com.example.data.local.entity.NoteEntity
import com.example.data.local.entity.SettingEntity
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class NoteBackupDto(
    val id: String,
    val title: String,
    val content: String,
    val folder: String,
    val isFavorite: Boolean = false,
    val isDeleted: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
    val cardType: String = "CREAM_LECTURE",
    val isTodo: Boolean = false,
    val isImportant: Boolean = false,
    val checklistJson: String = "[]",
    val sharedWithJson: String = "[]",
    val tagsJson: String = "[]",
    val noteCountText: String? = null,
    val imageUri: String? = null,
    val audioUri: String? = null,
    val audioDurationMs: Long = 0L
) {
    fun toEntity(): NoteEntity = NoteEntity(
        id = id,
        title = title,
        content = content,
        folder = folder,
        isFavorite = isFavorite,
        isDeleted = isDeleted,
        createdAt = createdAt,
        updatedAt = updatedAt,
        cardType = cardType,
        isTodo = isTodo,
        isImportant = isImportant,
        checklistJson = checklistJson,
        sharedWithJson = sharedWithJson,
        tagsJson = tagsJson,
        noteCountText = noteCountText,
        imageUri = imageUri,
        audioUri = audioUri,
        audioDurationMs = audioDurationMs,
        syncStatus = "SYNCED",
        syncedAt = System.currentTimeMillis()
    )

    companion object {
        fun fromEntity(entity: NoteEntity): NoteBackupDto = NoteBackupDto(
            id = entity.id,
            title = entity.title,
            content = entity.content,
            folder = entity.folder,
            isFavorite = entity.isFavorite,
            isDeleted = entity.isDeleted,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt,
            cardType = entity.cardType,
            isTodo = entity.isTodo,
            isImportant = entity.isImportant,
            checklistJson = entity.checklistJson,
            sharedWithJson = entity.sharedWithJson,
            tagsJson = entity.tagsJson,
            noteCountText = entity.noteCountText,
            imageUri = entity.imageUri,
            audioUri = entity.audioUri,
            audioDurationMs = entity.audioDurationMs
        )
    }
}

@JsonClass(generateAdapter = true)
data class FolderBackupDto(
    val id: String,
    val name: String,
    val colorHex: Long,
    val createdAt: Long
) {
    fun toEntity(): FolderEntity = FolderEntity(
        id = id,
        name = name,
        colorHex = colorHex,
        createdAt = createdAt
    )

    companion object {
        fun fromEntity(entity: FolderEntity): FolderBackupDto = FolderBackupDto(
            id = entity.id,
            name = entity.name,
            colorHex = entity.colorHex,
            createdAt = entity.createdAt
        )
    }
}

@JsonClass(generateAdapter = true)
data class SettingsBackupDto(
    val key: String,
    val value: String
) {
    fun toEntity(): SettingEntity = SettingEntity(
        key = key,
        value = value
    )

    companion object {
        fun fromEntity(entity: SettingEntity): SettingsBackupDto = SettingsBackupDto(
            key = entity.key,
            value = entity.value
        )
    }
}

@JsonClass(generateAdapter = true)
data class DocumentBackupDto(
    val id: String,
    val fileName: String,
    val displayName: String,
    val fileSize: Long,
    val mimeType: String,
    val pageCount: Int,
    val createdAt: Long,
    val updatedAt: Long,
    val lastOpenedAt: Long,
    val lastOpenedPage: Int = 0,
    val isFavorite: Boolean = false,
    val isDeleted: Boolean = false,
    val remoteStorageRef: String? = null,
    val accentColorHex: Long = 0xFFFEEA9F
) {
    fun toEntity(localPath: String = ""): DocumentEntity = DocumentEntity(
        id = id,
        fileName = fileName,
        displayName = displayName,
        localPath = localPath,
        fileSize = fileSize,
        mimeType = mimeType,
        pageCount = pageCount,
        createdAt = createdAt,
        updatedAt = updatedAt,
        lastOpenedAt = lastOpenedAt,
        lastOpenedPage = lastOpenedPage,
        isFavorite = isFavorite,
        isDeleted = isDeleted,
        syncStatus = "SYNCED",
        remoteStorageRef = remoteStorageRef,
        accentColorHex = accentColorHex
    )

    companion object {
        fun fromEntity(entity: DocumentEntity): DocumentBackupDto = DocumentBackupDto(
            id = entity.id,
            fileName = entity.fileName,
            displayName = entity.displayName,
            fileSize = entity.fileSize,
            mimeType = entity.mimeType,
            pageCount = entity.pageCount,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt,
            lastOpenedAt = entity.lastOpenedAt,
            lastOpenedPage = entity.lastOpenedPage,
            isFavorite = entity.isFavorite,
            isDeleted = entity.isDeleted,
            remoteStorageRef = entity.remoteStorageRef,
            accentColorHex = entity.accentColorHex
        )
    }
}

@JsonClass(generateAdapter = true)
data class CloudBackupDto(
    val schemaVersion: Int = 1,
    val backupVersion: String = "1.0.0",
    val userId: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val notes: List<NoteBackupDto> = emptyList(),
    val folders: List<FolderBackupDto> = emptyList(),
    val settings: List<SettingsBackupDto> = emptyList(),
    val documents: List<DocumentBackupDto> = emptyList()
)

data class CloudBackupProgress(
    val status: String,
    val progress: Float = 0f,
    val isCompleted: Boolean = false,
    val errorMessage: String? = null
)

data class CloudRestoreReport(
    val success: Boolean,
    val notesRestored: Int = 0,
    val foldersRestored: Int = 0,
    val documentsRestored: Int = 0,
    val settingsRestored: Int = 0,
    val message: String
)
