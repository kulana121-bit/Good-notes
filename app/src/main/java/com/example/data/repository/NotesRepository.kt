package com.example.data.repository

import android.content.Context
import androidx.compose.ui.graphics.Color
import com.example.data.local.NotesDatabase
import com.example.data.local.converters.NoteMappers
import com.example.data.local.dao.FolderDao
import com.example.data.local.dao.NoteDao
import com.example.data.local.dao.PendingSyncDao
import com.example.data.local.dao.SettingDao
import com.example.data.local.entity.FolderEntity
import com.example.data.local.entity.NoteEntity
import com.example.data.local.entity.SettingEntity
import com.example.data.model.Folder
import com.example.data.model.Note
import com.example.data.model.SampleFolders
import com.example.util.DeviceIdentityManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

class NotesRepository(
    private val database: NotesDatabase,
    private val context: Context? = null,
    private val noteDao: NoteDao = database.noteDao(),
    private val folderDao: FolderDao = database.folderDao(),
    private val settingDao: SettingDao = database.settingDao(),
    private val pendingSyncDao: PendingSyncDao = database.pendingSyncDao()
) {

    fun getActiveNotes(userId: String): Flow<List<Note>> =
        noteDao.getActiveNotes(userId).map { entities ->
            entities.map { NoteMappers.toDomain(it) }
        }

    fun getFavoriteNotes(userId: String): Flow<List<Note>> =
        noteDao.getFavoriteNotes(userId).map { entities ->
            entities.map { NoteMappers.toDomain(it) }
        }

    fun getTrashNotes(userId: String): Flow<List<Note>> =
        noteDao.getTrashNotes(userId).map { entities ->
            entities.map { NoteMappers.toDomain(it) }
        }

    fun getFolders(userId: String): Flow<List<Folder>> =
        folderDao.getAllFolders(userId).map { entities ->
            entities.map { NoteMappers.toDomainFolder(it, 0) }
        }

    fun getActiveNotesCount(userId: String): Flow<Int> = noteDao.getActiveNotesCount(userId)
    fun getFavoritesCount(userId: String): Flow<Int> = noteDao.getFavoritesCount(userId)
    fun getTrashCount(userId: String): Flow<Int> = noteDao.getTrashCount(userId)
    fun getPendingOperationsCount(userId: String): Flow<Int> = pendingSyncDao.getPendingCount(userId)

    fun getNotesByFolder(userId: String, folderName: String): Flow<List<Note>> {
        return noteDao.getNotesByFolder(userId, folderName).map { entities ->
            entities.map { NoteMappers.toDomain(it) }
        }
    }

    fun searchNotes(userId: String, query: String): Flow<List<Note>> {
        val trimmed = query.trim()
        return if (trimmed.isEmpty()) {
            getActiveNotes(userId)
        } else {
            noteDao.searchNotes(userId, trimmed).map { entities ->
                entities.map { NoteMappers.toDomain(it) }
            }
        }
    }

    fun getNoteById(id: String): Flow<Note?> {
        return noteDao.getNoteByIdFlow(id).map { entity ->
            entity?.let { NoteMappers.toDomain(it) }
        }
    }

    suspend fun getNoteByIdDirect(id: String): Note? {
        return noteDao.getNoteByIdDirect(id)?.let { NoteMappers.toDomain(it) }
    }

    suspend fun saveNote(note: Note, userId: String = "") {
        val existing = noteDao.getNoteByIdDirect(note.id)
        if (existing != null && existing.isDeleted && !note.isDeleted) {
            return
        }
        val updatedTimestamp = System.currentTimeMillis()
        val installationId = context?.let { DeviceIdentityManager.getInstallationId(it) } ?: ""
        val nextVersion = (existing?.version ?: 0L) + 1L

        val toSave = note.copy(
            updatedAt = updatedTimestamp,
            updatedAtText = NoteMappers.formatTimestamp(updatedTimestamp)
        )
        val entity = NoteMappers.toEntity(toSave, userId).copy(
            version = nextVersion,
            lastModifiedDeviceId = installationId,
            syncStatus = "PENDING_UPLOAD",
            deletedAt = 0L
        )
        noteDao.insertNote(entity)
        val opType = if (existing == null) "CREATE" else "UPDATE"
        pendingSyncDao.enqueueCoalesced(userId = userId, entityType = "NOTE", entityId = entity.id, operationType = opType)
    }

    suspend fun toggleFavorite(noteId: String, userId: String = "") {
        val current = noteDao.getNoteByIdDirect(noteId) ?: return
        val now = System.currentTimeMillis()
        noteDao.setFavorite(noteId, !current.isFavorite, now)
        pendingSyncDao.enqueueCoalesced(userId = userId, entityType = "NOTE", entityId = noteId, operationType = "UPDATE")
    }

    suspend fun softDeleteNote(noteId: String, userId: String = "") {
        val now = System.currentTimeMillis()
        noteDao.softDeleteNote(noteId, now)
        pendingSyncDao.enqueueCoalesced(userId = userId, entityType = "NOTE", entityId = noteId, operationType = "DELETE")
    }

    suspend fun restoreNote(noteId: String, userId: String = "") {
        val now = System.currentTimeMillis()
        noteDao.restoreNote(noteId, now)
        pendingSyncDao.enqueueCoalesced(userId = userId, entityType = "NOTE", entityId = noteId, operationType = "UPDATE")
    }

    suspend fun permanentlyDeleteNote(noteId: String, userId: String = "") {
        noteDao.permanentlyDeleteNote(noteId)
        pendingSyncDao.enqueueCoalesced(userId = userId, entityType = "NOTE", entityId = noteId, operationType = "DELETE")
    }

    suspend fun emptyTrash(userId: String) {
        val trashNotes = database.noteDao().getAllNotesDirect(userId).filter { it.isDeleted }
        noteDao.emptyTrash(userId)
        trashNotes.forEach { note ->
            pendingSyncDao.enqueueCoalesced(userId = userId, entityType = "NOTE", entityId = note.id, operationType = "DELETE")
        }
    }

    suspend fun moveNoteToFolder(noteId: String, targetFolderName: String, userId: String = "") {
        val now = System.currentTimeMillis()
        noteDao.moveNoteToFolder(noteId, targetFolderName, now)
        pendingSyncDao.enqueueCoalesced(userId = userId, entityType = "NOTE", entityId = noteId, operationType = "UPDATE")
    }

    suspend fun createFolder(name: String, color: Color, userId: String = ""): Boolean {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return false
        val existing = folderDao.getFolderByName(userId, trimmed)
        if (existing != null) return false
        val now = System.currentTimeMillis()
        val installationId = context?.let { DeviceIdentityManager.getInstallationId(it) } ?: ""
        val entity = FolderEntity(
            id = "folder_${UUID.randomUUID().toString().take(8)}",
            userId = userId,
            name = trimmed,
            colorHex = color.value.toLong(),
            createdAt = now,
            updatedAt = now,
            isDeleted = false,
            syncStatus = "PENDING_UPLOAD",
            version = 1L,
            lastModifiedDeviceId = installationId
        )
        folderDao.insertFolder(entity)
        pendingSyncDao.enqueueCoalesced(userId = userId, entityType = "FOLDER", entityId = entity.id, operationType = "CREATE")
        return true
    }

    suspend fun renameFolder(oldName: String, newName: String, userId: String = ""): Boolean {
        val trimmedNew = newName.trim()
        if (trimmedNew.isBlank() || oldName.equals(trimmedNew, ignoreCase = true)) {
            return false
        }
        val existing = folderDao.getFolderByName(userId, trimmedNew)
        if (existing != null) return false

        val now = System.currentTimeMillis()
        database.renameFolderWithNotes(userId = userId, oldName = oldName, newName = trimmedNew, timestamp = now)
        val renamedFolder = folderDao.getFolderByName(userId, trimmedNew)
        if (renamedFolder != null) {
            pendingSyncDao.enqueueCoalesced(userId = userId, entityType = "FOLDER", entityId = renamedFolder.id, operationType = "UPDATE")
        }
        return true
    }

    suspend fun deleteFolder(folderId: String, folderName: String, fallbackFolder: String = "Personal", userId: String = "") {
        val now = System.currentTimeMillis()
        database.deleteFolderSafely(userId = userId, folderId = folderId, folderName = folderName, fallbackFolder = fallbackFolder, timestamp = now)
        pendingSyncDao.enqueueCoalesced(userId = userId, entityType = "FOLDER", entityId = folderId, operationType = "DELETE")
    }

    // Settings
    fun getSetting(key: String, defaultValue: String): Flow<String> {
        return settingDao.getSettingFlow(key).map { it ?: defaultValue }
    }

    suspend fun setSetting(key: String, value: String) {
        settingDao.setSetting(SettingEntity(key, value))
    }

    fun isDarkMode(): Flow<Boolean> = getSetting("dark_mode", "true").map { it.toBoolean() }
    suspend fun setDarkMode(enabled: Boolean) = setSetting("dark_mode", enabled.toString())

    fun isAutoSave(): Flow<Boolean> = getSetting("auto_save", "true").map { it.toBoolean() }
    suspend fun setAutoSave(enabled: Boolean) = setSetting("auto_save", enabled.toString())

    fun getSelectedFont(): Flow<String> = getSetting("selected_font", "Outfit (Editorial)")
    suspend fun setSelectedFont(fontName: String) = setSetting("selected_font", fontName)

    fun getBaseTextSize(): Flow<Float> = getSetting("base_text_size", "16").map { it.toFloatOrNull() ?: 16f }
    suspend fun setBaseTextSize(size: Float) = setSetting("base_text_size", size.toString())

    fun getDefaultSortOrder(): Flow<String> = getSetting("default_sort_order", "Recently Modified")
    suspend fun setDefaultSortOrder(order: String) = setSetting("default_sort_order", order)

    // Cloud Backup Settings
    fun isAutoCloudBackup(): Flow<Boolean> = getSetting("auto_cloud_backup", "true").map { it.toBoolean() }
    suspend fun setAutoCloudBackup(enabled: Boolean) = setSetting("auto_cloud_backup", enabled.toString())

    fun isCloudBackupWifiOnly(): Flow<Boolean> = getSetting("cloud_backup_wifi_only", "false").map { it.toBoolean() }
    suspend fun setCloudBackupWifiOnly(enabled: Boolean) = setSetting("cloud_backup_wifi_only", enabled.toString())

    fun isCloudBackupIncludeDocs(): Flow<Boolean> = getSetting("cloud_backup_include_docs", "true").map { it.toBoolean() }
    suspend fun setCloudBackupIncludeDocs(enabled: Boolean) = setSetting("cloud_backup_include_docs", enabled.toString())

    fun getLastCloudBackupTimestamp(): Flow<Long> = getSetting("last_cloud_backup_timestamp", "0").map { it.toLongOrNull() ?: 0L }
    fun getLastCloudBackupStatus(): Flow<String> = getSetting("last_cloud_backup_status", "Never")

    fun isOnboardingCompleted(): Flow<Boolean> = getSetting("onboarding_completed", "false").map { it.toBoolean() }
    suspend fun setOnboardingCompleted() = setSetting("onboarding_completed", "true")

    /**
     * Seeds initial folders for this user if they don't have any folders.
     */
    suspend fun seedInitialDataIfNeeded(userId: String = "") {
        val folderCount = folderDao.getFolderCountDirect(userId)
        if (folderCount == 0) {
            val folderEntities = SampleFolders.map { NoteMappers.toFolderEntity(it, userId) }
            folderDao.insertFolders(folderEntities)
        }
        settingDao.setSetting(SettingEntity("database_initialized_$userId", "true"))
    }
}
