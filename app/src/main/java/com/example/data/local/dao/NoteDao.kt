package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.local.entity.NoteEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {

    @Query("SELECT * FROM notes WHERE isDeleted = 0 ORDER BY updatedAt DESC")
    fun getActiveNotes(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE isDeleted = 0 AND isFavorite = 1 ORDER BY updatedAt DESC")
    fun getFavoriteNotes(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE isDeleted = 1 ORDER BY updatedAt DESC")
    fun getTrashNotes(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE isDeleted = 0 AND folder = :folderName ORDER BY updatedAt DESC")
    fun getNotesByFolder(folderName: String): Flow<List<NoteEntity>>

    @Query("""
        SELECT * FROM notes 
        WHERE isDeleted = 0 AND (
            title LIKE '%' || :query || '%' OR 
            content LIKE '%' || :query || '%' OR 
            checklistJson LIKE '%' || :query || '%' OR
            tagsJson LIKE '%' || :query || '%'
        ) 
        ORDER BY updatedAt DESC
    """)
    fun searchNotes(query: String): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE id = :id LIMIT 1")
    fun getNoteByIdFlow(id: String): Flow<NoteEntity?>

    @Query("SELECT * FROM notes WHERE id = :id LIMIT 1")
    suspend fun getNoteByIdDirect(id: String): NoteEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNote(note: NoteEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotes(notes: List<NoteEntity>)

    @Query("UPDATE notes SET isDeleted = 1, updatedAt = :timestamp WHERE id = :id")
    suspend fun softDeleteNote(id: String, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE notes SET isDeleted = 0, updatedAt = :timestamp WHERE id = :id")
    suspend fun restoreNote(id: String, timestamp: Long = System.currentTimeMillis())

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun permanentlyDeleteNote(id: String)

    @Query("DELETE FROM notes WHERE isDeleted = 1")
    suspend fun emptyTrash()

    @Query("UPDATE notes SET isFavorite = :isFavorite, updatedAt = :timestamp WHERE id = :id")
    suspend fun setFavorite(id: String, isFavorite: Boolean, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE notes SET folder = :newFolderName, updatedAt = :timestamp WHERE folder = :oldFolderName")
    suspend fun renameFolderInNotes(oldFolderName: String, newFolderName: String, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE notes SET folder = :targetFolderName, updatedAt = :timestamp WHERE id = :noteId")
    suspend fun moveNoteToFolder(noteId: String, targetFolderName: String, timestamp: Long = System.currentTimeMillis())

    @Query("SELECT COUNT(*) FROM notes WHERE isDeleted = 0")
    fun getActiveNotesCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM notes WHERE isDeleted = 0 AND isFavorite = 1")
    fun getFavoritesCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM notes WHERE isDeleted = 1")
    fun getTrashCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM notes")
    suspend fun getTotalNotesCountDirect(): Int

    @Query("SELECT * FROM notes")
    suspend fun getAllNotesDirect(): List<NoteEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertNoteSync(note: NoteEntity)

    @Query("SELECT COUNT(*) FROM notes WHERE isDeleted = 0 AND folder = :folderName")
    fun getNotesCountForFolder(folderName: String): Flow<Int>
}
