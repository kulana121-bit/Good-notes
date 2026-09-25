package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.local.dao.DocumentDao
import com.example.data.local.dao.FolderDao
import com.example.data.local.dao.NoteDao
import com.example.data.local.dao.PendingSyncDao
import com.example.data.local.dao.SettingDao
import com.example.data.local.entity.DocumentEntity
import com.example.data.local.entity.FolderEntity
import com.example.data.local.entity.NoteEntity
import com.example.data.local.entity.PendingSyncOperation
import com.example.data.local.entity.SettingEntity

@Database(
    entities = [
        NoteEntity::class,
        FolderEntity::class,
        SettingEntity::class,
        DocumentEntity::class,
        PendingSyncOperation::class
    ],
    version = 5,
    exportSchema = false
)
abstract class NotesDatabase : RoomDatabase() {

    abstract fun noteDao(): NoteDao
    abstract fun folderDao(): FolderDao
    abstract fun settingDao(): SettingDao
    abstract fun documentDao(): DocumentDao
    abstract fun pendingSyncDao(): PendingSyncDao

    @Transaction
    open suspend fun renameFolderWithNotes(userId: String = "", oldName: String, newName: String, timestamp: Long = System.currentTimeMillis()) {
        folderDao().renameFolder(userId = userId, oldName = oldName, newName = newName, timestamp = timestamp)
        noteDao().renameFolderInNotes(userId = userId, oldFolderName = oldName, newFolderName = newName, timestamp = timestamp)
    }

    @Transaction
    open suspend fun deleteFolderSafely(userId: String = "", folderId: String, folderName: String, fallbackFolder: String = "Personal", timestamp: Long = System.currentTimeMillis()) {
        folderDao().softDeleteFolder(userId = userId, id = folderId, timestamp = timestamp)
        noteDao().renameFolderInNotes(userId = userId, oldFolderName = folderName, newFolderName = fallbackFolder, timestamp = timestamp)
    }

    companion object {
        @Volatile
        private var INSTANCE: NotesDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1. Add sync columns to notes table with safe DEFAULT values
                db.execSQL("ALTER TABLE notes ADD COLUMN syncStatus TEXT NOT NULL DEFAULT 'PENDING'")
                db.execSQL("ALTER TABLE notes ADD COLUMN syncedAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE notes ADD COLUMN remoteId TEXT DEFAULT NULL")

                // 2. Create documents table
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `documents` (
                        `id` TEXT NOT NULL,
                        `fileName` TEXT NOT NULL,
                        `displayName` TEXT NOT NULL,
                        `localPath` TEXT NOT NULL,
                        `fileSize` INTEGER NOT NULL,
                        `mimeType` TEXT NOT NULL DEFAULT 'application/pdf',
                        `pageCount` INTEGER NOT NULL DEFAULT 1,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        `lastOpenedAt` INTEGER NOT NULL,
                        `lastOpenedPage` INTEGER NOT NULL DEFAULT 0,
                        `isFavorite` INTEGER NOT NULL DEFAULT 0,
                        `isDeleted` INTEGER NOT NULL DEFAULT 0,
                        `syncStatus` TEXT NOT NULL DEFAULT 'PENDING',
                        `remoteStorageRef` TEXT DEFAULT NULL,
                        `accentColorHex` INTEGER NOT NULL DEFAULT 18446744073686884127,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )

                // 3. Create indices for documents table
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_documents_isDeleted` ON `documents` (`isDeleted`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_documents_isFavorite` ON `documents` (`isFavorite`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_documents_lastOpenedAt` ON `documents` (`lastOpenedAt`)")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1. Add version, lastModifiedDeviceId, deletedAt to notes
                db.execSQL("ALTER TABLE notes ADD COLUMN version INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE notes ADD COLUMN lastModifiedDeviceId TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE notes ADD COLUMN deletedAt INTEGER NOT NULL DEFAULT 0")

                // 2. Add sync and version fields to folders
                db.execSQL("ALTER TABLE folders ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE folders ADD COLUMN isDeleted INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE folders ADD COLUMN syncStatus TEXT NOT NULL DEFAULT 'SYNCED'")
                db.execSQL("ALTER TABLE folders ADD COLUMN version INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE folders ADD COLUMN lastModifiedDeviceId TEXT NOT NULL DEFAULT ''")

                // 3. Create pending_sync_operations table
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `pending_sync_operations` (
                        `operationId` TEXT NOT NULL,
                        `entityType` TEXT NOT NULL,
                        `entityId` TEXT NOT NULL,
                        `operationType` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `retryCount` INTEGER NOT NULL DEFAULT 0,
                        `lastError` TEXT DEFAULT NULL,
                        `payloadJson` TEXT DEFAULT NULL,
                        PRIMARY KEY(`operationId`)
                    )
                    """.trimIndent()
                )

                // 4. Create indices for pending_sync_operations
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_pending_sync_operations_entityId` ON `pending_sync_operations` (`entityId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_pending_sync_operations_entityType_entityId` ON `pending_sync_operations` (`entityType`, `entityId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_pending_sync_operations_createdAt` ON `pending_sync_operations` (`createdAt`)")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE documents ADD COLUMN driveFileId TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE documents ADD COLUMN contentHash TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE documents ADD COLUMN lastSyncedAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE documents ADD COLUMN deletedAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE documents ADD COLUMN downloadState TEXT NOT NULL DEFAULT 'AVAILABLE_OFFLINE'")
                db.execSQL("ALTER TABLE documents ADD COLUMN uploadState TEXT NOT NULL DEFAULT 'IDLE'")
                db.execSQL("ALTER TABLE documents ADD COLUMN thumbnailPath TEXT DEFAULT NULL")

                db.execSQL("CREATE INDEX IF NOT EXISTS `index_documents_driveFileId` ON `documents` (`driveFileId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_documents_contentHash` ON `documents` (`contentHash`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_documents_downloadState` ON `documents` (`downloadState`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_documents_uploadState` ON `documents` (`uploadState`)")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add userId columns for account isolation
                db.execSQL("ALTER TABLE notes ADD COLUMN userId TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE folders ADD COLUMN userId TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE documents ADD COLUMN userId TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE pending_sync_operations ADD COLUMN userId TEXT NOT NULL DEFAULT ''")

                // Create account-isolated indices
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_notes_userId` ON `notes` (`userId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_notes_userId_isDeleted` ON `notes` (`userId`, `isDeleted`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_notes_userId_isFavorite` ON `notes` (`userId`, `isFavorite`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_notes_userId_folder` ON `notes` (`userId`, `folder`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_notes_userId_updatedAt` ON `notes` (`userId`, `updatedAt`)")

                db.execSQL("DROP INDEX IF EXISTS `index_folders_name`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_folders_userId` ON `folders` (`userId`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_folders_userId_name` ON `folders` (`userId`, `name`)")

                db.execSQL("CREATE INDEX IF NOT EXISTS `index_documents_userId` ON `documents` (`userId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_documents_userId_isDeleted` ON `documents` (`userId`, `isDeleted`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_documents_userId_isFavorite` ON `documents` (`userId`, `isFavorite`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_documents_userId_lastOpenedAt` ON `documents` (`userId`, `lastOpenedAt`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_documents_userId_driveFileId` ON `documents` (`userId`, `driveFileId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_documents_userId_contentHash` ON `documents` (`userId`, `contentHash`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_documents_userId_downloadState` ON `documents` (`userId`, `downloadState`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_documents_userId_uploadState` ON `documents` (`userId`, `uploadState`)")

                db.execSQL("CREATE INDEX IF NOT EXISTS `index_pending_sync_operations_userId` ON `pending_sync_operations` (`userId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_pending_sync_operations_userId_entityId` ON `pending_sync_operations` (`userId`, `entityId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_pending_sync_operations_userId_entityType_entityId` ON `pending_sync_operations` (`userId`, `entityType`, `entityId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_pending_sync_operations_userId_createdAt` ON `pending_sync_operations` (`userId`, `createdAt`)")
            }
        }

        fun getInstance(context: Context): NotesDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    NotesDatabase::class.java,
                    "notes_database"
                )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
