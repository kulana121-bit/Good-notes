package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.local.entity.FolderEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FolderDao {

    @Query("SELECT * FROM folders WHERE userId = :userId AND isDeleted = 0 ORDER BY createdAt ASC")
    fun getAllFolders(userId: String): Flow<List<FolderEntity>>

    @Query("SELECT * FROM folders WHERE id = :id LIMIT 1")
    suspend fun getFolderById(id: String): FolderEntity?

    @Query("SELECT * FROM folders WHERE userId = :userId AND id = :id LIMIT 1")
    suspend fun getFolderByIdAndUser(userId: String, id: String): FolderEntity?

    @Query("SELECT * FROM folders WHERE userId = :userId AND name = :name AND isDeleted = 0 LIMIT 1")
    suspend fun getFolderByName(userId: String, name: String): FolderEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFolder(folder: FolderEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFolders(folders: List<FolderEntity>)

    @Query("UPDATE folders SET name = :newName, updatedAt = :timestamp, syncStatus = 'PENDING_UPLOAD' WHERE userId = :userId AND name = :oldName")
    suspend fun renameFolder(userId: String, oldName: String, newName: String, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE folders SET isDeleted = 1, updatedAt = :timestamp, syncStatus = 'PENDING_DELETE' WHERE userId = :userId AND id = :id")
    suspend fun softDeleteFolder(userId: String, id: String, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE folders SET isDeleted = 0, updatedAt = :timestamp, syncStatus = 'PENDING_UPLOAD' WHERE userId = :userId AND id = :id")
    suspend fun restoreFolder(userId: String, id: String, timestamp: Long = System.currentTimeMillis())

    @Query("DELETE FROM folders WHERE id = :id")
    suspend fun deleteFolder(id: String)

    @Query("DELETE FROM folders WHERE userId = :userId AND name = :name")
    suspend fun deleteFolderByName(userId: String, name: String)

    @Query("SELECT COUNT(*) FROM folders WHERE userId = :userId AND isDeleted = 0")
    suspend fun getFolderCountDirect(userId: String): Int

    @Query("SELECT * FROM folders WHERE userId = :userId AND isDeleted = 0")
    suspend fun getAllFoldersDirect(userId: String): List<FolderEntity>

    @Query("SELECT * FROM folders WHERE userId = :userId")
    suspend fun getAllFoldersIncludingDeletedDirect(userId: String): List<FolderEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertFolderSync(folder: FolderEntity)
}
