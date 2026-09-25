package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.local.entity.NoteEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {

    @Query("SELECT * FROM notes WHERE userId = :userId AND isDeleted = 0 ORDER BY updatedAt DESC")
    fun getActiveNotes(userId: String): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE userId = :userId AND isDeleted = 0 AND isFavorite = 1 ORDER BY updatedAt DESC")
    fun getFavoriteNotes(userId: String): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE userId = :userId AND isDeleted = 1 ORDER BY updatedAt DESC")
    fun getTrashNotes(userId: String): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE userId = :userId AND isDeleted = 0 AND folder = :folderName ORDER BY updatedAt DESC")
    fun getNotesByFolder(userId: String, folderName: String): Flow<List<NoteEntity>>

    @Query("""
        SELECT * FROM notes 
        WHERE userId = :userId AND isDeleted = 0 AND (
            title LIKE '%' || :query || '%' OR 
            content LIKE '%' || :query || '%' OR 
            checklistJson LIKE '%' || :query || '%' OR
            tagsJson LIKE '%' || :query || '%'
        ) 
        ORDER BY updatedAt DESC
    """)
    fun searchNotes(userId: String, query: String): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE id = :id LIMIT 1")
    fun getNoteByIdFlow(id: String): Flow<NoteEntity?>

    @Query("SELECT * FROM notes WHERE id = :id LIMIT 1")
    suspend fun getNoteByIdDirect(id: String): NoteEntity?

    @Query("SELECT * FROM notes WHERE userId = :userId AND id = :id LIMIT 1")
    suspend fun getNoteByIdAndUser(userId: String, id: String): NoteEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNote(note: NoteEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotes(notes: List<NoteEntity>)

    @Query("UPDATE notes SET isDeleted = 1, updatedAt = :timestamp, deletedAt = :timestamp, syncStatus = 'PENDING_DELETE' WHERE id = :id")
    suspend fun softDeleteNote(id: String, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE notes SET isDeleted = 0, updatedAt = :timestamp, deletedAt = 0, syncStatus = 'PENDING_UPLOAD' WHERE id = :id")
    suspend fun restoreNote(id: String, timestamp: Long = System.currentTimeMillis())

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun permanentlyDeleteNote(id: String)

    @Query("DELETE FROM notes WHERE userId = :userId AND isDeleted = 1")
    suspend fun emptyTrash(userId: String)

    @Query("UPDATE notes SET isFavorite = :isFavorite, updatedAt = :timestamp, syncStatus = 'PENDING_UPLOAD' WHERE id = :id")
    suspend fun setFavorite(id: String, isFavorite: Boolean, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE notes SET folder = :newFolderName, updatedAt = :timestamp, syncStatus = 'PENDING_UPLOAD' WHERE userId = :userId AND folder = :oldFolderName")
    suspend fun renameFolderInNotes(userId: String, oldFolderName: String, newFolderName: String, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE notes SET folder = :targetFolderName, updatedAt = :timestamp, syncStatus = 'PENDING_UPLOAD' WHERE id = :noteId")
    suspend fun moveNoteToFolder(noteId: String, targetFolderName: String, timestamp: Long = System.currentTimeMillis())

    @Query("SELECT COUNT(*) FROM notes WHERE userId = :userId AND isDeleted = 0")
    fun getActiveNotesCount(userId: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM notes WHERE userId = :userId AND isFavorite = 1 AND isDeleted = 0")
    fun getFavoritesCount(userId: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM notes WHERE userId = :userId AND isDeleted = 1")
    fun getTrashCount(userId: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM notes WHERE userId = :userId")
    suspend fun getTotalNotesCountDirect(userId: String): Int

    @Query("SELECT * FROM notes WHERE userId = :userId")
    suspend fun getAllNotesDirect(userId: String): List<NoteEntity>

    @Query("SELECT * FROM notes")
    suspend fun getAllNotesUnfilteredDirect(): List<NoteEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertNoteSync(note: NoteEntity)

    @Query("SELECT COUNT(*) FROM notes WHERE userId = :userId AND isDeleted = 0 AND folder = :folderName")
    fun getNotesCountForFolder(userId: String, folderName: String): Flow<Int>
}
