package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.entity.DocumentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DocumentDao {
    @Query("SELECT * FROM documents WHERE isDeleted = 0 ORDER BY lastOpenedAt DESC")
    fun getActiveDocuments(): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE isDeleted = 0 AND isFavorite = 1 ORDER BY lastOpenedAt DESC")
    fun getFavoriteDocuments(): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE isDeleted = 0 ORDER BY lastOpenedAt DESC LIMIT :limit")
    fun getRecentDocuments(limit: Int = 10): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE isDeleted = 1 ORDER BY updatedAt DESC")
    fun getTrashDocuments(): Flow<List<DocumentEntity>>

    @Query("""
        SELECT * FROM documents 
        WHERE isDeleted = 0 AND (fileName LIKE '%' || :query || '%' OR displayName LIKE '%' || :query || '%')
        ORDER BY lastOpenedAt DESC
    """)
    fun searchDocuments(query: String): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE id = :id LIMIT 1")
    fun getDocumentByIdFlow(id: String): Flow<DocumentEntity?>

    @Query("SELECT * FROM documents WHERE id = :id LIMIT 1")
    suspend fun getDocumentByIdDirect(id: String): DocumentEntity?

    @Query("SELECT * FROM documents")
    suspend fun getAllDocumentsDirect(): List<DocumentEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDocument(document: DocumentEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertDocumentSync(document: DocumentEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDocuments(documents: List<DocumentEntity>)

    @Update
    suspend fun updateDocument(document: DocumentEntity)

    @Query("UPDATE documents SET lastOpenedAt = :timestamp, lastOpenedPage = :page WHERE id = :id")
    suspend fun updateLastOpened(id: String, timestamp: Long, page: Int)

    @Query("UPDATE documents SET isFavorite = :isFavorite, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setFavorite(id: String, isFavorite: Boolean, updatedAt: Long)

    @Query("UPDATE documents SET isDeleted = 1, updatedAt = :updatedAt WHERE id = :id")
    suspend fun softDeleteDocument(id: String, updatedAt: Long)

    @Query("UPDATE documents SET isDeleted = 0, updatedAt = :updatedAt WHERE id = :id")
    suspend fun restoreDocument(id: String, updatedAt: Long)

    @Query("DELETE FROM documents WHERE id = :id")
    suspend fun permanentlyDeleteDocument(id: String)

    @Query("SELECT COUNT(*) FROM documents WHERE isDeleted = 0")
    fun getActiveDocumentsCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM documents WHERE isDeleted = 0")
    suspend fun getActiveDocumentsCountDirect(): Int

    @Query("SELECT SUM(fileSize) FROM documents WHERE isDeleted = 0")
    fun getTotalDocumentsSize(): Flow<Long?>

    @Query("SELECT SUM(fileSize) FROM documents WHERE isDeleted = 0")
    suspend fun getTotalDocumentsSizeDirect(): Long?
}
