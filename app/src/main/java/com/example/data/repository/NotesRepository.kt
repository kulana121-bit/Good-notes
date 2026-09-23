package com.example.data.repository

import androidx.compose.ui.graphics.Color
import com.example.data.local.NotesDatabase
import com.example.data.local.converters.NoteMappers
import com.example.data.local.dao.FolderDao
import com.example.data.local.dao.NoteDao
import com.example.data.local.dao.SettingDao
import com.example.data.local.entity.FolderEntity
import com.example.data.local.entity.NoteEntity
import com.example.data.local.entity.SettingEntity
import com.example.data.model.Folder
import com.example.data.model.Note
import com.example.data.model.SampleFolders
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

class NotesRepository(
    private val database: NotesDatabase,
    private val noteDao: NoteDao = database.noteDao(),
    private val folderDao: FolderDao = database.folderDao(),
    private val settingDao: SettingDao = database.settingDao()
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
        val toSave = note.copy(
            updatedAt = updatedTimestamp,
            updatedAtText = NoteMappers.formatTimestamp(updatedTimestamp)
        )
        noteDao.insertNote(NoteMappers.toEntity(toSave))
    }

    suspend fun toggleFavorite(noteId: String) {
        val current = noteDao.getNoteByIdDirect(noteId) ?: return
        noteDao.setFavorite(noteId, !current.isFavorite, System.currentTimeMillis())
    }

    suspend fun softDeleteNote(noteId: String) {
        noteDao.softDeleteNote(noteId, System.currentTimeMillis())
    }

    suspend fun restoreNote(noteId: String) {
        noteDao.restoreNote(noteId, System.currentTimeMillis())
    }

    suspend fun permanentlyDeleteNote(noteId: String) {
        noteDao.permanentlyDeleteNote(noteId)
    }

    suspend fun emptyTrash() {
        noteDao.emptyTrash()
    }

    suspend fun moveNoteToFolder(noteId: String, targetFolderName: String) {
        noteDao.moveNoteToFolder(noteId, targetFolderName, System.currentTimeMillis())
    }

    suspend fun createFolder(name: String, color: Color): Boolean {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return false
        val existing = folderDao.getFolderByName(trimmed)
        if (existing != null) return false
        val entity = FolderEntity(
            id = "folder_${UUID.randomUUID().toString().take(8)}",
            name = trimmed,
            colorHex = color.value.toLong(),
            createdAt = System.currentTimeMillis()
        )
        folderDao.insertFolder(entity)
        return true
    }

    suspend fun renameFolder(oldName: String, newName: String): Boolean {
        val trimmedNew = newName.trim()
        if (trimmedNew.isBlank() || oldName.equals(trimmedNew, ignoreCase = true)) {
            return false
        }
        val existing = folderDao.getFolderByName(trimmedNew)
        if (existing != null) return false

        database.renameFolderWithNotes(oldName, trimmedNew)
        return true
    }

    suspend fun deleteFolder(folderId: String, folderName: String, fallbackFolder: String = "Personal") {
        database.deleteFolderSafely(folderId, folderName, fallbackFolder)
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
