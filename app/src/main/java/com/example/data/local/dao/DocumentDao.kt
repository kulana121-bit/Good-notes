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
    @Query("SELECT * FROM documents WHERE userId = :userId AND isDeleted = 0 ORDER BY lastOpenedAt DESC")
    fun getActiveDocuments(userId: String): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE userId = :userId AND isDeleted = 0 AND isFavorite = 1 ORDER BY lastOpenedAt DESC")
    fun getFavoriteDocuments(userId: String): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE userId = :userId AND isDeleted = 0 ORDER BY lastOpenedAt DESC LIMIT :limit")
    fun getRecentDocuments(userId: String, limit: Int = 10): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE userId = :userId AND isDeleted = 1 ORDER BY updatedAt DESC")
    fun getTrashDocuments(userId: String): Flow<List<DocumentEntity>>

    @Query("""
        SELECT * FROM documents 
        WHERE userId = :userId AND isDeleted = 0 AND (fileName LIKE '%' || :query || '%' OR displayName LIKE '%' || :query || '%')
        ORDER BY lastOpenedAt DESC
    """)
    fun searchDocuments(userId: String, query: String): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE id = :id LIMIT 1")
    fun getDocumentByIdFlow(id: String): Flow<DocumentEntity?>

    @Query("SELECT * FROM documents WHERE id = :id LIMIT 1")
    suspend fun getDocumentByIdDirect(id: String): DocumentEntity?

    @Query("SELECT * FROM documents WHERE userId = :userId AND contentHash = :hash AND fileSize = :size AND isDeleted = 0 LIMIT 1")
    suspend fun findDocumentByHashAndSize(userId: String, hash: String, size: Long): DocumentEntity?

    @Query("SELECT * FROM documents WHERE driveFileId = :driveFileId LIMIT 1")
    suspend fun findDocumentByDriveId(driveFileId: String): DocumentEntity?

    @Query("SELECT * FROM documents WHERE userId = :userId AND uploadState = 'PENDING_UPLOAD' AND isDeleted = 0")
    suspend fun getPendingUploadDocuments(userId: String): List<DocumentEntity>

    @Query("SELECT * FROM documents WHERE userId = :userId AND downloadState = 'CLOUD_ONLY' AND isDeleted = 0")
    suspend fun getCloudOnlyDocuments(userId: String): List<DocumentEntity>

    @Query("SELECT * FROM documents WHERE userId = :userId AND remoteStorageRef IS NOT NULL AND driveFileId IS NULL AND isDeleted = 0")
    suspend fun getDocumentsNeedingFirebaseMigration(userId: String): List<DocumentEntity>

    @Query("SELECT * FROM documents WHERE userId = :userId")
    suspend fun getAllDocumentsDirect(userId: String): List<DocumentEntity>

    @Query("SELECT * FROM documents")
    suspend fun getAllDocumentsUnfilteredDirect(): List<DocumentEntity>

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

    @Query("UPDATE documents SET isDeleted = 1, deletedAt = :deletedAt, updatedAt = :updatedAt WHERE id = :id")
    suspend fun softDeleteDocument(id: String, deletedAt: Long = System.currentTimeMillis(), updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE documents SET isDeleted = 0, deletedAt = 0, updatedAt = :updatedAt WHERE id = :id")
    suspend fun restoreDocument(id: String, updatedAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM documents WHERE id = :id")
    suspend fun permanentlyDeleteDocument(id: String)

    @Query("""
        UPDATE documents 
        SET driveFileId = :driveFileId,
            syncStatus = :syncStatus,
            uploadState = :uploadState,
            lastSyncedAt = :lastSyncedAt,
            updatedAt = :updatedAt
        WHERE id = :id
    """)
    suspend fun updateDriveSyncStatus(
        id: String,
        driveFileId: String?,
        syncStatus: String,
        uploadState: String,
        lastSyncedAt: Long,
        updatedAt: Long = System.currentTimeMillis()
    )

    @Query("""
        UPDATE documents 
        SET downloadState = :downloadState,
            localPath = :localPath,
            thumbnailPath = :thumbnailPath,
            updatedAt = :updatedAt
        WHERE id = :id
    """)
    suspend fun updateDownloadState(
        id: String,
        downloadState: String,
        localPath: String,
        thumbnailPath: String?,
        updatedAt: Long = System.currentTimeMillis()
    )

    @Query("UPDATE documents SET uploadState = :uploadState, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateUploadState(id: String, uploadState: String, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE documents SET thumbnailPath = :thumbnailPath WHERE id = :id")
    suspend fun updateThumbnail(id: String, thumbnailPath: String?)

    @Query("SELECT COUNT(*) FROM documents WHERE userId = :userId AND isDeleted = 0")
    fun getActiveDocumentsCount(userId: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM documents WHERE userId = :userId AND isDeleted = 0")
    suspend fun getActiveDocumentsCountDirect(userId: String): Int

    @Query("SELECT SUM(fileSize) FROM documents WHERE userId = :userId AND isDeleted = 0")
    fun getTotalDocumentsSize(userId: String): Flow<Long?>

    @Query("SELECT SUM(fileSize) FROM documents WHERE userId = :userId AND isDeleted = 0")
    suspend fun getTotalDocumentsSizeDirect(userId: String): Long?
}
