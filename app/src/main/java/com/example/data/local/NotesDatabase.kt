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
import com.example.data.local.dao.SettingDao
import com.example.data.local.entity.DocumentEntity
import com.example.data.local.entity.FolderEntity
import com.example.data.local.entity.NoteEntity
import com.example.data.local.entity.SettingEntity

@Database(
    entities = [
        NoteEntity::class,
        FolderEntity::class,
        SettingEntity::class,
        DocumentEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class NotesDatabase : RoomDatabase() {

    abstract fun noteDao(): NoteDao
    abstract fun folderDao(): FolderDao
    abstract fun settingDao(): SettingDao
    abstract fun documentDao(): DocumentDao

    @Transaction
    open suspend fun renameFolderWithNotes(oldName: String, newName: String) {
        folderDao().renameFolder(oldName = oldName, newName = newName)
        noteDao().renameFolderInNotes(oldFolderName = oldName, newFolderName = newName)
    }

    @Transaction
    open suspend fun deleteFolderSafely(folderId: String, folderName: String, fallbackFolder: String = "Personal") {
        folderDao().deleteFolder(folderId)
        noteDao().renameFolderInNotes(oldFolderName = folderName, newFolderName = fallbackFolder)
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

        fun getInstance(context: Context): NotesDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    NotesDatabase::class.java,
                    "notes_database"
                )
                .addMigrations(MIGRATION_1_2)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
