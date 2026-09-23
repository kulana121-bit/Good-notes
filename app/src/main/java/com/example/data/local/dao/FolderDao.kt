package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.local.entity.FolderEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FolderDao {

    @Query("SELECT * FROM folders ORDER BY createdAt ASC")
    fun getAllFolders(): Flow<List<FolderEntity>>

    @Query("SELECT * FROM folders WHERE id = :id LIMIT 1")
    suspend fun getFolderById(id: String): FolderEntity?

    @Query("SELECT * FROM folders WHERE name = :name LIMIT 1")
    suspend fun getFolderByName(name: String): FolderEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFolder(folder: FolderEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFolders(folders: List<FolderEntity>)

    @Query("UPDATE folders SET name = :newName WHERE name = :oldName")
    suspend fun renameFolder(oldName: String, newName: String)

    @Query("DELETE FROM folders WHERE id = :id")
    suspend fun deleteFolder(id: String)

    @Query("DELETE FROM folders WHERE name = :name")
    suspend fun deleteFolderByName(name: String)

    @Query("SELECT COUNT(*) FROM folders")
    suspend fun getFolderCountDirect(): Int

    @Query("SELECT * FROM folders")
    suspend fun getAllFoldersDirect(): List<FolderEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertFolderSync(folder: FolderEntity)
}
