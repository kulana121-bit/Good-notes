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

    val allNotes: Flow<List<Note>> = noteDao.getActiveNotes().map { entities ->
        entities.map { NoteMappers.toDomain(it) }
    }

    val favoriteNotes: Flow<List<Note>> = noteDao.getFavoriteNotes().map { entities ->
        entities.map { NoteMappers.toDomain(it) }
    }

    val trashNotes: Flow<List<Note>> = noteDao.getTrashNotes().map { entities ->
        entities.map { NoteMappers.toDomain(it) }
    }

    val allFolders: Flow<List<Folder>> = folderDao.getAllFolders().map { entities ->
        entities.map { NoteMappers.toDomainFolder(it, 0) }
    }

    val folders: Flow<List<Folder>> = allFolders

    val activeNotesCount: Flow<Int> = noteDao.getActiveNotesCount()
    val favoritesCount: Flow<Int> = noteDao.getFavoritesCount()
    val trashCount: Flow<Int> = noteDao.getTrashCount()

    fun getNotesByFolder(folderName: String): Flow<List<Note>> {
        return noteDao.getNotesByFolder(folderName).map { entities ->
            entities.map { NoteMappers.toDomain(it) }
        }
    }

    fun searchNotes(query: String): Flow<List<Note>> {
        val trimmed = query.trim()
        return if (trimmed.isEmpty()) {
            allNotes
        } else {
            noteDao.searchNotes(trimmed).map { entities ->
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

    suspend fun saveNote(note: Note) {
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
        val entity = NoteMappers.toEntity(toSave).copy(
            version = nextVersion,
            lastModifiedDeviceId = installationId,
            syncStatus = "PENDING_UPLOAD",
            deletedAt = 0L
        )
        noteDao.insertNote(entity)
        val opType = if (existing == null) "CREATE" else "UPDATE"
        pendingSyncDao.enqueueCoalesced("NOTE", entity.id, opType)
    }

    suspend fun toggleFavorite(noteId: String) {
        val current = noteDao.getNoteByIdDirect(noteId) ?: return
        val now = System.currentTimeMillis()
        noteDao.setFavorite(noteId, !current.isFavorite, now)
        pendingSyncDao.enqueueCoalesced("NOTE", noteId, "UPDATE")
    }

    suspend fun softDeleteNote(noteId: String) {
        val now = System.currentTimeMillis()
        noteDao.softDeleteNote(noteId, now)
        pendingSyncDao.enqueueCoalesced("NOTE", noteId, "DELETE")
    }

    suspend fun restoreNote(noteId: String) {
        val now = System.currentTimeMillis()
        noteDao.restoreNote(noteId, now)
        pendingSyncDao.enqueueCoalesced("NOTE", noteId, "UPDATE")
    }

    suspend fun permanentlyDeleteNote(noteId: String) {
        noteDao.permanentlyDeleteNote(noteId)
        pendingSyncDao.enqueueCoalesced("NOTE", noteId, "DELETE")
    }

    suspend fun emptyTrash() {
        val trashNotes = database.noteDao().getAllNotesDirect().filter { it.isDeleted }
        noteDao.emptyTrash()
        trashNotes.forEach { note ->
            pendingSyncDao.enqueueCoalesced("NOTE", note.id, "DELETE")
        }
    }

    suspend fun moveNoteToFolder(noteId: String, targetFolderName: String) {
        val now = System.currentTimeMillis()
        noteDao.moveNoteToFolder(noteId, targetFolderName, now)
        pendingSyncDao.enqueueCoalesced("NOTE", noteId, "UPDATE")
    }

    suspend fun createFolder(name: String, color: Color): Boolean {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return false
        val existing = folderDao.getFolderByName(trimmed)
        if (existing != null) return false
        val now = System.currentTimeMillis()
        val installationId = context?.let { DeviceIdentityManager.getInstallationId(it) } ?: ""
        val entity = FolderEntity(
            id = "folder_${UUID.randomUUID().toString().take(8)}",
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
        pendingSyncDao.enqueueCoalesced("FOLDER", entity.id, "CREATE")
        return true
    }

    suspend fun renameFolder(oldName: String, newName: String): Boolean {
        val trimmedNew = newName.trim()
        if (trimmedNew.isBlank() || oldName.equals(trimmedNew, ignoreCase = true)) {
            return false
        }
        val existing = folderDao.getFolderByName(trimmedNew)
        if (existing != null) return false

        val now = System.currentTimeMillis()
        database.renameFolderWithNotes(oldName, trimmedNew, now)
        val renamedFolder = folderDao.getFolderByName(trimmedNew)
        if (renamedFolder != null) {
            pendingSyncDao.enqueueCoalesced("FOLDER", renamedFolder.id, "UPDATE")
        }
        return true
    }

    suspend fun deleteFolder(folderId: String, folderName: String, fallbackFolder: String = "Personal") {
        val now = System.currentTimeMillis()
        database.deleteFolderSafely(folderId, folderName, fallbackFolder, now)
        pendingSyncDao.enqueueCoalesced("FOLDER", folderId, "DELETE")
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
     * Seeds initial folders if database is brand new.
     */
    suspend fun seedInitialDataIfNeeded() {
        val dummyIds = listOf("note_1", "note_2", "note_3", "note_4", "note_5", "note_6", "note_7", "note_8")
        dummyIds.forEach { dummyId ->
            noteDao.permanentlyDeleteNote(dummyId)
        }

        val folderCount = folderDao.getFolderCountDirect()
        if (folderCount == 0) {
            val folderEntities = SampleFolders.map { NoteMappers.toFolderEntity(it) }
            folderDao.insertFolders(folderEntities)
        }
        settingDao.setSetting(SettingEntity("database_initialized", "true"))
    }
}
