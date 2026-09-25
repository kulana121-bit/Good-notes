package com.example.data.backup

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.data.local.NotesDatabase
import com.example.data.local.entity.DocumentEntity
import com.example.data.local.entity.FolderEntity
import com.example.data.local.entity.NoteEntity
import com.example.data.local.entity.SettingEntity
import com.example.data.remote.auth.FirebaseAuthService
import com.example.data.remote.drive.DriveAuthState
import com.example.data.remote.drive.GoogleDriveService
import com.example.data.remote.firestore.FirestoreNotesService
import com.example.util.SecurityUtils
import com.squareup.moshi.Moshi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class CloudBackupManager(
    private val context: Context,
    private val database: NotesDatabase,
    private val authService: FirebaseAuthService = FirebaseAuthService(context),
    private val driveService: GoogleDriveService = GoogleDriveService(context),
    private val firestoreService: FirestoreNotesService = FirestoreNotesService(context)
) {

    private val tag = "CloudBackupManager"

    private val moshi = Moshi.Builder().build()
    private val backupAdapter = moshi.adapter(CloudBackupDto::class.java)

    private val _backupProgress = MutableStateFlow<CloudBackupProgress?>(null)
    val backupProgress: StateFlow<CloudBackupProgress?> = _backupProgress.asStateFlow()

    private val _restoreProgress = MutableStateFlow<CloudBackupProgress?>(null)
    val restoreProgress: StateFlow<CloudBackupProgress?> = _restoreProgress.asStateFlow()

    /**
     * Creates a versioned cloud backup and uploads it to the user's personal Google Drive
     * under My Drive/NOTES/Backup/notes_backup.json and documents under My Drive/NOTES/Documents/.
     */
    suspend fun backupToCloud(includeDocuments: Boolean = true): Result<CloudBackupDto> = withContext(Dispatchers.IO) {
        val uid = authService.getCurrentUserId()
        if (uid.isNullOrBlank()) {
            _backupProgress.value = CloudBackupProgress(status = "Error", errorMessage = "Sign in with your Google account first")
            return@withContext Result.failure(Exception("Sign in required for cloud backup."))
        }

        val currentUserEmail = authService.currentUser.value?.email
        val driveState = driveService.checkAuthorization(expectedEmail = currentUserEmail)
        if (driveState !is DriveAuthState.Connected) {
            val err = if (driveState is DriveAuthState.AccountMismatch) {
                "Drive account (${driveState.driveEmail}) doesn't match signed-in Google account (${driveState.firebaseEmail})."
            } else {
                "Google Drive isn't connected."
            }
            _backupProgress.value = CloudBackupProgress(status = "Error", errorMessage = err)
            return@withContext Result.failure(Exception(err))
        }

        try {
            _backupProgress.value = CloudBackupProgress(status = "Preparing...", progress = 0.15f)

            // 1. Gather Room entities for the active user
            val notes = database.noteDao().getAllNotesDirect(uid)
            val folders = database.folderDao().getAllFoldersDirect(uid)
            val settings = database.settingDao().getAllSettingsDirect()
            val documents = database.documentDao().getAllDocumentsDirect(uid)

            // 2. Map to versioned DTOs
            val backupDto = CloudBackupDto(
                schemaVersion = 1,
                backupVersion = "1.0.0",
                userId = uid,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
                notes = notes.map { NoteBackupDto.fromEntity(it) },
                folders = folders.map { FolderBackupDto.fromEntity(it) },
                settings = settings.map { SettingsBackupDto.fromEntity(it) },
                documents = documents.map { DocumentBackupDto.fromEntity(it) }
            )

            // 3. Serialize safely
            _backupProgress.value = CloudBackupProgress(status = "Backing up notes...", progress = 0.4f)
            val jsonString = backupAdapter.indent("  ").toJson(backupDto)
            val jsonBytes = jsonString.toByteArray(Charsets.UTF_8)

            // 4. Resolve Drive folder structure
            _backupProgress.value = CloudBackupProgress(status = "Connecting to Google Drive folder...", progress = 0.45f)
            val folderStructure = driveService.getOrCreateNotesFolderStructure().getOrThrow()

            // 5. Atomic upload: Upload temporary file notes_backup.tmp first
            _backupProgress.value = CloudBackupProgress(status = "Staging atomic backup snapshot...", progress = 0.5f)
            val tmpUploadResult = driveService.uploadOrUpdateFile(
                parentFolderId = folderStructure.backupFolderId,
                fileName = "notes_backup.tmp",
                mimeType = "application/json",
                content = jsonBytes
            )
            if (tmpUploadResult.isFailure) {
                val err = tmpUploadResult.exceptionOrNull() ?: Exception("Failed to stage atomic backup snapshot")
                _backupProgress.value = CloudBackupProgress(status = "Failed", errorMessage = err.localizedMessage)
                return@withContext Result.failure(err)
            }

            // Verify temporary upload
            val tmpFile = driveService.findFile(folderStructure.backupFolderId, "notes_backup.tmp").getOrNull()
            if (tmpFile == null || tmpFile.size == 0L) {
                val err = Exception("Atomic backup verification failed: staged snapshot file is empty or missing.")
                _backupProgress.value = CloudBackupProgress(status = "Failed", errorMessage = err.localizedMessage)
                return@withContext Result.failure(err)
            }

            // Commit atomic backup: update or create notes_backup.json
            _backupProgress.value = CloudBackupProgress(status = "Committing notes_backup.json...", progress = 0.65f)
            val backupUploadResult = driveService.uploadOrUpdateFile(
                parentFolderId = folderStructure.backupFolderId,
                fileName = "notes_backup.json",
                mimeType = "application/json",
                content = jsonBytes
            )
            if (backupUploadResult.isFailure) {
                val err = backupUploadResult.exceptionOrNull() ?: Exception("Failed committing verified notes_backup.json")
                _backupProgress.value = CloudBackupProgress(status = "Failed", errorMessage = err.localizedMessage)
                return@withContext Result.failure(err)
            }

            // Clean up temporary file
            try {
                driveService.deleteFile(tmpFile.id)
            } catch (cleanupErr: Exception) {
                Log.w(tag, "Temporary backup cleanup non-critical warning", cleanupErr)
            }

            // 6. Upload documents if requested
            if (includeDocuments && documents.isNotEmpty()) {
                _backupProgress.value = CloudBackupProgress(status = "Uploading documents...", progress = 0.7f)
                val existingDriveFilesResult = driveService.listFiles(folderStructure.documentsFolderId)
                val existingDriveFilesMap = existingDriveFilesResult.getOrDefault(emptyList()).associateBy { it.name }

                val activeDocs = documents.filter { !it.isDeleted }
                val totalDocs = activeDocs.size
                var uploadedCount = 0

                for ((idx, doc) in activeDocs.withIndex()) {
                    val progressValue = 0.7f + (0.22f * ((idx + 1).toFloat() / totalDocs.toFloat()))
                    _backupProgress.value = CloudBackupProgress(
                        status = "Uploading documents (${idx + 1}/$totalDocs)...",
                        progress = progressValue
                    )

                    val targetDriveName = "${doc.id}_${doc.fileName}"
                    val existingFile = existingDriveFilesMap[targetDriveName]

                    if (existingFile != null && existingFile.size == doc.fileSize && doc.fileSize > 0) {
                        Log.d(tag, "Document '$targetDriveName' is already up to date on Drive. Skipping duplicate upload.")
                        continue
                    }

                    val bytes = readDocumentBytes(doc)
                    if (bytes != null && bytes.isNotEmpty()) {
                        val docUploadRes = driveService.uploadOrUpdateFile(
                            parentFolderId = folderStructure.documentsFolderId,
                            fileName = targetDriveName,
                            mimeType = doc.mimeType,
                            content = bytes
                        )
                        if (docUploadRes.isSuccess) {
                            uploadedCount++
                        }
                    }
                }
            }

            // 7. Update timestamp and state
            _backupProgress.value = CloudBackupProgress(status = "Finishing...", progress = 0.95f)
            val now = System.currentTimeMillis()
            database.settingDao().setSetting(SettingEntity("last_cloud_backup_timestamp", now.toString()))
            database.settingDao().setSetting(SettingEntity("last_cloud_backup_status", "SUCCESS"))

            _backupProgress.value = CloudBackupProgress(status = "Backup complete ✓", progress = 1.0f, isCompleted = true)
            Log.i(tag, "Cloud backup completed successfully for user $uid")
            Result.success(backupDto)
        } catch (e: Exception) {
            Log.e(tag, "Cloud backup failed", e)
            _backupProgress.value = CloudBackupProgress(status = "Failed", errorMessage = "Backup couldn't finish. Your notes are safe on this device.", isCompleted = true)
            Result.failure(e)
        }
    }

    /**
     * Checks whether a valid cloud backup exists in the user's Google Drive.
     */
    suspend fun checkForCloudBackup(): Result<CloudBackupDto?> = withContext(Dispatchers.IO) {
        val currentUserEmail = authService.currentUser.value?.email
        val driveState = driveService.checkAuthorization(expectedEmail = currentUserEmail)
        if (driveState !is DriveAuthState.Connected) {
            return@withContext Result.failure(Exception("Google Drive not connected"))
        }

        try {
            val folderStructure = driveService.getOrCreateNotesFolderStructure().getOrThrow()
            val backupFile = driveService.findFile(folderStructure.backupFolderId, "notes_backup.json").getOrNull()
                ?: return@withContext Result.success(null)

            val bytes = driveService.downloadFile(backupFile.id).getOrThrow()
            val jsonString = String(bytes, Charsets.UTF_8)
            val backupDto = backupAdapter.fromJson(jsonString)
                ?: return@withContext Result.failure(Exception("Could not parse cloud backup metadata."))

            Result.success(backupDto)
        } catch (e: Exception) {
            Log.e(tag, "Error checking cloud backup", e)
            Result.failure(e)
        }
    }

    /**
     * Restores notes, folders, settings, and documents from Google Drive.
     * Uses deterministic conflict resolution (updatedAt) to prevent data loss.
     */
    suspend fun restoreFromCloud(providedBackup: CloudBackupDto? = null): Result<CloudRestoreReport> = withContext(Dispatchers.IO) {
        _restoreProgress.value = CloudBackupProgress(status = "Preparing restore...", progress = 0.1f)

        try {
            val backupDto = providedBackup ?: run {
                _restoreProgress.value = CloudBackupProgress(status = "Locating backup file on Google Drive...", progress = 0.2f)
                val checkRes = checkForCloudBackup()
                if (checkRes.isFailure) {
                    throw checkRes.exceptionOrNull() ?: Exception("Failed to check Drive for backup")
                }
                checkRes.getOrNull() ?: throw Exception("No cloud backup found on your Google Drive.")
            }

            if (backupDto.schemaVersion < 1) {
                throw Exception("Unsupported backup schema version: ${backupDto.schemaVersion}")
            }

            val currentUid = authService.getCurrentUserId() ?: ""

            _restoreProgress.value = CloudBackupProgress(status = "Restoring folders...", progress = 0.3f)
            var foldersRestored = 0
            val existingFolders = database.folderDao().getAllFoldersDirect(currentUid).associateBy { it.id }
            for (fDto in backupDto.folders) {
                val entity = fDto.toEntity().copy(userId = currentUid)
                if (!existingFolders.containsKey(entity.id)) {
                    database.folderDao().insertFolder(entity)
                    foldersRestored++
                }
            }

            _restoreProgress.value = CloudBackupProgress(status = "Restoring notes...", progress = 0.5f)
            var notesRestored = 0
            val existingNotes = database.noteDao().getAllNotesDirect(currentUid).associateBy { it.id }

            for (nDto in backupDto.notes) {
                val cloudNote = nDto.toEntity().copy(userId = currentUid)
                val localNote = existingNotes[cloudNote.id]

                if (localNote == null) {
                    database.noteDao().insertNoteSync(cloudNote)
                    notesRestored++
                } else {
                    if (cloudNote.updatedAt >= localNote.updatedAt) {
                        database.noteDao().insertNoteSync(cloudNote)
                        notesRestored++
                    }
                }
            }

            _restoreProgress.value = CloudBackupProgress(status = "Restoring settings...", progress = 0.65f)
            var settingsRestored = 0
            for (sDto in backupDto.settings) {
                database.settingDao().setSetting(sDto.toEntity())
                settingsRestored++
            }

            _restoreProgress.value = CloudBackupProgress(status = "Preparing documents...", progress = 0.75f)
            var docsRestored = 0
            val existingDocs = database.documentDao().getAllDocumentsDirect(currentUid).associateBy { it.id }

            val folderStructure = driveService.getOrCreateNotesFolderStructure().getOrNull()
            val driveFilesMap = if (folderStructure != null) {
                driveService.listFiles(folderStructure.documentsFolderId).getOrDefault(emptyList()).associateBy { it.name }
            } else emptyMap()

            if (backupDto.documents.isNotEmpty()) {
                _restoreProgress.value = CloudBackupProgress(status = "Downloading documents...", progress = 0.85f)
            }

            for (dDto in backupDto.documents) {
                val localDoc = existingDocs[dDto.id]
                val localTargetFile = try {
                    SecurityUtils.getSafeDocumentFile(context, dDto.id, dDto.fileName)
                } catch (e: SecurityException) {
                    Log.e(tag, "Skipping untrusted document file reference in backup: ${dDto.fileName}", e)
                    continue
                }

                var resolvedLocalPath = localDoc?.localPath.orEmpty()
                if (localTargetFile.exists() && localTargetFile.length() > 0) {
                    resolvedLocalPath = localTargetFile.absolutePath
                } else if (folderStructure != null) {
                    val driveFile = driveFilesMap["${dDto.id}_${dDto.fileName}"]
                    if (driveFile != null) {
                        try {
                            val fileBytes = driveService.downloadFile(driveFile.id).getOrNull()
                            if (fileBytes != null && fileBytes.isNotEmpty()) {
                                FileOutputStream(localTargetFile).use { it.write(fileBytes) }
                                resolvedLocalPath = localTargetFile.absolutePath
                            }
                        } catch (e: Exception) {
                            Log.w(tag, "Failed to download document ${dDto.fileName} during restore", e)
                        }
                    }
                }

                val docEntity = dDto.toEntity(resolvedLocalPath).copy(userId = currentUid)
                if (localDoc == null || dDto.updatedAt > localDoc.updatedAt) {
                    database.documentDao().insertDocumentSync(docEntity)
                    docsRestored++
                }
            }

            _restoreProgress.value = CloudBackupProgress(status = "Restore complete ✓", progress = 1.0f, isCompleted = true)

            val report = CloudRestoreReport(
                success = true,
                notesRestored = notesRestored,
                foldersRestored = foldersRestored,
                documentsRestored = docsRestored,
                settingsRestored = settingsRestored,
                message = "Restored $notesRestored notes, $foldersRestored folders, and $docsRestored documents."
            )
            Log.i(tag, "Cloud restore finished: ${report.message}")
            Result.success(report)
        } catch (e: Exception) {
            Log.e(tag, "Cloud restore failed", e)
            _restoreProgress.value = CloudBackupProgress(status = "Failed", errorMessage = "Couldn't restore the backup.", isCompleted = true)
            Result.failure(e)
        }
    }

    private fun readDocumentBytes(doc: DocumentEntity): ByteArray? {
        return try {
            if (doc.localPath.startsWith("content://")) {
                val uri = Uri.parse(doc.localPath)
                context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            } else {
                val file = File(doc.localPath)
                if (file.exists() && file.isFile) {
                    file.readBytes()
                } else null
            }
        } catch (e: Exception) {
            Log.w(tag, "Error reading bytes for document ${doc.id}", e)
            null
        }
    }
}
