package com.example.data.backup

import android.content.Context
import android.net.Uri
import com.example.data.local.NotesDatabase
import com.example.data.local.entity.DocumentEntity
import com.example.data.local.entity.FolderEntity
import com.example.data.local.entity.NoteEntity
import com.example.data.local.entity.SettingEntity
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

@JsonClass(generateAdapter = true)
data class NotesBackupDto(
    val formatVersion: Int = 1,
    val exportedAt: Long = System.currentTimeMillis(),
    val appIdentifier: String = "NOTES_BACKUP",
    val notes: List<NoteEntity> = emptyList(),
    val folders: List<FolderEntity> = emptyList(),
    val settings: List<SettingEntity> = emptyList(),
    val documents: List<DocumentEntity> = emptyList()
)

data class RestoreResultSummary(
    val notesCount: Int,
    val foldersCount: Int,
    val documentsCount: Int,
    val settingsCount: Int
)

class BackupManager(private val database: NotesDatabase) {

    private val moshi = Moshi.Builder().build()

    private val adapter = moshi.adapter(NotesBackupDto::class.java)

    suspend fun exportBackup(context: Context, destinationUri: Uri): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val notes = database.noteDao().getAllNotesDirect()
            val folders = database.folderDao().getAllFoldersDirect()
            val settings = database.settingDao().getAllSettingsDirect()
            val documents = database.documentDao().getAllDocumentsDirect()

            val backupDto = NotesBackupDto(
                formatVersion = 1,
                exportedAt = System.currentTimeMillis(),
                appIdentifier = "NOTES_BACKUP",
                notes = notes,
                folders = folders,
                settings = settings,
                documents = documents
            )

            val jsonString = adapter.indent("  ").toJson(backupDto)

            val outputStream = context.contentResolver.openOutputStream(destinationUri)
                ?: return@withContext Result.failure(Exception("Cannot open destination stream for export"))

            ZipOutputStream(BufferedOutputStream(outputStream)).use { zipOut ->
                // 1. Write backup.json
                val jsonEntry = ZipEntry("backup.json")
                zipOut.putNextEntry(jsonEntry)
                zipOut.write(jsonString.toByteArray(Charsets.UTF_8))
                zipOut.closeEntry()

                // 2. Write document files
                for (doc in documents) {
                    val file = File(doc.localPath)
                    if (file.exists() && file.isFile) {
                        val fileEntry = ZipEntry("documents/${file.name}")
                        zipOut.putNextEntry(fileEntry)
                        FileInputStream(file).use { fileIn ->
                            fileIn.copyTo(zipOut)
                        }
                        zipOut.closeEntry()
                    }
                }
            }

            Result.success(notes.size + documents.size)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun restoreBackup(context: Context, sourceUri: Uri): Result<RestoreResultSummary> = withContext(Dispatchers.IO) {
        try {
            val inputStream = context.contentResolver.openInputStream(sourceUri)
                ?: return@withContext Result.failure(Exception("Cannot open backup archive"))

            var jsonContent: String? = null
            val docsDir = File(context.filesDir, "documents").apply { mkdirs() }
            val docsDirCanonicalPath = docsDir.canonicalPath + File.separator
            val extractedFiles = mutableMapOf<String, String>() // original filename -> localPath

            ZipInputStream(BufferedInputStream(inputStream)).use { zipIn ->
                var entry: ZipEntry? = zipIn.nextEntry
                while (entry != null) {
                    val entryName = entry.name
                    if (entryName == "backup.json") {
                        val byteBuffer = ByteArrayOutputStream()
                        zipIn.copyTo(byteBuffer)
                        jsonContent = byteBuffer.toString(Charsets.UTF_8.name())
                    } else if (entryName.startsWith("documents/") && !entry.isDirectory) {
                        val fileName = entryName.substringAfter("documents/")
                        val targetFile = File(docsDir, fileName)

                        if (!targetFile.canonicalPath.startsWith(docsDirCanonicalPath)) {
                            throw SecurityException("Zip Slip vulnerability detected: Invalid file path $entryName")
                        }

                        FileOutputStream(targetFile).use { out ->
                            zipIn.copyTo(out)
                        }
                        extractedFiles[fileName] = targetFile.absolutePath
                    }
                    zipIn.closeEntry()
                    entry = zipIn.nextEntry
                }
            }

            if (jsonContent.isNullOrBlank()) {
                return@withContext Result.failure(Exception("Invalid backup archive: missing backup.json descriptor"))
            }

            val backupDto = adapter.fromJson(jsonContent)
                ?: return@withContext Result.failure(Exception("Failed to parse backup metadata. Corrupted or invalid format."))

            if (backupDto.appIdentifier != "NOTES_BACKUP") {
                return@withContext Result.failure(Exception("Unrecognized backup file format"))
            }

            // Remap documents local paths
            val remappedDocuments = backupDto.documents.map { doc ->
                val fileName = File(doc.localPath).name
                val restoredPath = extractedFiles[fileName] ?: doc.localPath
                doc.copy(localPath = restoredPath)
            }

            // Transactional restore into Room
            database.runInTransaction {
                // Restore folders
                for (folder in backupDto.folders) {
                    database.folderDao().insertFolderSync(folder)
                }
                // Restore notes
                for (note in backupDto.notes) {
                    database.noteDao().insertNoteSync(note)
                }
                // Restore settings
                for (setting in backupDto.settings) {
                    database.settingDao().setSettingSync(setting)
                }
                // Restore documents
                for (doc in remappedDocuments) {
                    database.documentDao().insertDocumentSync(doc)
                }
            }

            Result.success(
                RestoreResultSummary(
                    notesCount = backupDto.notes.size,
                    foldersCount = backupDto.folders.size,
                    documentsCount = remappedDocuments.size,
                    settingsCount = backupDto.settings.size
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
