package com.example.data.migration

import android.content.Context
import android.util.Log
import com.example.data.local.dao.DocumentDao
import com.example.data.local.entity.DocumentEntity
import com.example.data.remote.drive.GoogleDriveDocumentService
import com.example.data.remote.storage.FirebaseStorageService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File

data class DocumentMigrationState(
    val inProgress: Boolean = false,
    val totalDocuments: Int = 0,
    val migratedDocuments: Int = 0,
    val failedDocuments: Int = 0,
    val currentDocumentName: String? = null,
    val lastError: String? = null
)

class DocumentDriveMigrationManager(
    private val context: Context,
    private val documentDao: DocumentDao,
    private val driveDocumentService: GoogleDriveDocumentService = GoogleDriveDocumentService(context),
    private val firebaseStorageService: FirebaseStorageService = FirebaseStorageService(context)
) {
    private val tag = "DocDriveMigration"

    private val _migrationState = MutableStateFlow(DocumentMigrationState())
    val migrationState: StateFlow<DocumentMigrationState> = _migrationState.asStateFlow()

    /**
     * Executes idempotent migration of legacy Firebase Storage documents to Google Drive.
     * Firebase files are preserved until Drive upload is verified.
     */
    suspend fun migratePendingDocuments(userId: String = ""): Result<DocumentMigrationState> = withContext(Dispatchers.IO) {
        val candidates = documentDao.getDocumentsNeedingFirebaseMigration(userId)
        if (candidates.isEmpty()) {
            Log.d(tag, "No legacy Firebase Storage documents require migration.")
            return@withContext Result.success(_migrationState.value)
        }

        _migrationState.value = DocumentMigrationState(
            inProgress = true,
            totalDocuments = candidates.size,
            migratedDocuments = 0,
            failedDocuments = 0
        )

        var migratedCount = 0
        var failedCount = 0

        for (doc in candidates) {
            val storageRef = doc.remoteStorageRef ?: continue
            _migrationState.value = _migrationState.value.copy(
                currentDocumentName = doc.displayName
            )

            try {
                val migrated = migrateSingleDocument(doc, storageRef)
                if (migrated) {
                    migratedCount++
                    _migrationState.value = _migrationState.value.copy(
                        migratedDocuments = migratedCount
                    )
                } else {
                    failedCount++
                    _migrationState.value = _migrationState.value.copy(
                        failedDocuments = failedCount
                    )
                }
            } catch (e: Exception) {
                Log.e(tag, "Failed migrating document ${doc.id}", e)
                failedCount++
                _migrationState.value = _migrationState.value.copy(
                    failedDocuments = failedCount,
                    lastError = e.localizedMessage
                )
            }
        }

        val finalState = DocumentMigrationState(
            inProgress = false,
            totalDocuments = candidates.size,
            migratedDocuments = migratedCount,
            failedDocuments = failedCount
        )
        _migrationState.value = finalState
        Log.i(tag, "Document migration cycle finished: $migratedCount migrated, $failedCount failed.")
        Result.success(finalState)
    }

    private suspend fun migrateSingleDocument(doc: DocumentEntity, storageRef: String): Boolean {
        // 1. Resolve local file or download from Firebase Storage
        val docsDir = File(context.filesDir, "documents").apply { if (!exists()) mkdirs() }
        val targetLocalFile = File(docsDir, "${doc.id}_${doc.fileName}")

        val sourceFile: File = if (doc.localPath.isNotBlank() && File(doc.localPath).exists() && File(doc.localPath).length() > 0) {
            File(doc.localPath)
        } else if (targetLocalFile.exists() && targetLocalFile.length() > 0) {
            targetLocalFile
        } else {
            // Download from Firebase Storage
            val downloadRes = firebaseStorageService.downloadPdfDocument(storageRef, targetLocalFile)
            if (downloadRes.isFailure) {
                Log.w(tag, "Could not download ${doc.id} from Firebase Storage for migration: ${downloadRes.exceptionOrNull()?.message}")
                return false
            }
            downloadRes.getOrThrow()
        }

        // 2. Upload to Google Drive NOTES/Documents
        val uploadRes = driveDocumentService.uploadDocument(doc, sourceFile)
        if (uploadRes.isFailure) {
            Log.w(tag, "Drive upload failed during migration of ${doc.id}: ${uploadRes.exceptionOrNull()?.message}")
            return false
        }

        val uploadResult = uploadRes.getOrThrow()

        // 3. Verify upload exists on Drive
        val verificationRes = driveDocumentService.verifyFileExistence(uploadResult.driveFileId)
        if (verificationRes.getOrDefault(false) != true) {
            Log.w(tag, "Drive verification failed for migrated document ${doc.id}")
            return false
        }

        // 4. Update Room with driveFileId, mark uploaded and synced
        val now = System.currentTimeMillis()
        documentDao.updateDriveSyncStatus(
            id = doc.id,
            driveFileId = uploadResult.driveFileId,
            syncStatus = "SYNCED",
            uploadState = "UPLOADED",
            lastSyncedAt = now,
            updatedAt = now
        )

        // Ensure localPath points to verified local file
        documentDao.updateDownloadState(
            id = doc.id,
            downloadState = "AVAILABLE_OFFLINE",
            localPath = sourceFile.absolutePath,
            thumbnailPath = doc.thumbnailPath,
            updatedAt = now
        )

        Log.i(tag, "Successfully migrated document ${doc.id} to Google Drive (ID: ${uploadResult.driveFileId})")
        return true
    }
}
