package com.example.data.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.util.Log
import com.example.data.local.NotesDatabase
import com.example.data.local.entity.DocumentEntity
import com.example.data.local.entity.FolderEntity
import com.example.data.local.entity.NoteEntity
import com.example.data.local.entity.PendingSyncOperation
import com.example.data.migration.DocumentDriveMigrationManager
import com.example.data.remote.auth.FirebaseAuthService
import com.example.data.remote.drive.DriveAuthState
import com.example.data.remote.drive.GoogleDriveDocumentService
import com.example.data.remote.drive.GoogleDriveService
import com.example.data.remote.firestore.FirestoreNotesService
import com.example.data.remote.storage.FirebaseStorageService
import com.example.util.DeviceIdentityManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

enum class SyncState(val label: String) {
    SYNCED("Synced"),
    SYNCING("Syncing..."),
    OFFLINE("Offline"),
    SIGN_IN_REQUIRED("Sign in required"),
    ERROR("Sync error")
}

data class SyncReport(
    val uploadedNotes: Int = 0,
    val downloadedNotes: Int = 0,
    val uploadedDocuments: Int = 0,
    val downloadedDocuments: Int = 0,
    val conflictsResolved: Int = 0,
    val pendingOperationsProcessed: Int = 0,
    val errorMessage: String? = null
)

class SyncManager(
    private val context: Context,
    private val database: NotesDatabase,
    private val authService: FirebaseAuthService = FirebaseAuthService(context),
    private val firestoreService: FirestoreNotesService = FirestoreNotesService(context),
    private val storageService: FirebaseStorageService = FirebaseStorageService(context),
    private val driveService: GoogleDriveService = GoogleDriveService(context),
    private val driveDocumentService: GoogleDriveDocumentService = GoogleDriveDocumentService(context),
    val migrationManager: DocumentDriveMigrationManager = DocumentDriveMigrationManager(context, database.documentDao(), driveDocumentService, storageService)
) {

    private val tag = "SyncManager"

    private val _syncState = MutableStateFlow(SyncState.SYNCED)
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    private val _lastSyncTimestamp = MutableStateFlow(0L)
    val lastSyncTimestamp: StateFlow<Long> = _lastSyncTimestamp.asStateFlow()

    private val _lastSyncReport = MutableStateFlow<SyncReport?>(null)
    val lastSyncReport: StateFlow<SyncReport?> = _lastSyncReport.asStateFlow()

    val pendingOperationsCount = database.pendingSyncDao().getPendingCount()

    fun isOnline(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    suspend fun syncNow(): Result<SyncReport> = withContext(Dispatchers.IO) {
        val uid = authService.getCurrentUserId()
        if (uid.isNullOrBlank()) {
            _syncState.value = SyncState.SIGN_IN_REQUIRED
            return@withContext Result.failure(Exception("Sign in to synchronize your notes."))
        }

        // Account switching safety: ensure pending ops from a different user are never uploaded to this user
        val lastSyncedUid = database.settingDao().getSettingDirect("last_synced_user_id")
        if (lastSyncedUid != null && lastSyncedUid != uid) {
            Log.w(tag, "Account switch detected: previous user '$lastSyncedUid', current user '$uid'. Purging pending queue to prevent cross-account contamination.")
            database.pendingSyncDao().clearAllOperations()
        }
        database.settingDao().setSetting(com.example.data.local.entity.SettingEntity("last_synced_user_id", uid))

        if (!isOnline()) {
            _syncState.value = SyncState.OFFLINE
            return@withContext Result.failure(Exception("No internet connection available. Changes will sync when online."))
        }

        _syncState.value = SyncState.SYNCING

        val localDeviceId = DeviceIdentityManager.getInstallationId(context)
        var uploadedNotesCount = 0
        var downloadedNotesCount = 0
        var uploadedDocsCount = 0
        var conflictsCount = 0
        var pendingProcessed = 0

        try {
            // STEP 1: DRAIN PENDING OPERATIONS QUEUE (OFFLINE-FIRST SYNC ENGINE)
            val pendingOps = database.pendingSyncDao().getAllPendingOperationsDirect()
            for (op in pendingOps) {
                try {
                    when (op.entityType) {
                        "NOTE" -> {
                            when (op.operationType) {
                                "CREATE", "UPDATE" -> {
                                    val note = database.noteDao().getNoteByIdDirect(op.entityId)
                                    if (note != null) {
                                        val uploadRes = firestoreService.uploadNote(uid, note)
                                        if (uploadRes.isSuccess) {
                                            database.noteDao().insertNoteSync(
                                                note.copy(syncStatus = "SYNCED", syncedAt = System.currentTimeMillis())
                                            )
                                            database.pendingSyncDao().deleteOperation(op.operationId)
                                            uploadedNotesCount++
                                            pendingProcessed++
                                        } else {
                                            database.pendingSyncDao().updateRetry(
                                                op.operationId,
                                                op.retryCount + 1,
                                                uploadRes.exceptionOrNull()?.localizedMessage
                                            )
                                        }
                                    } else {
                                        database.pendingSyncDao().deleteOperation(op.operationId)
                                    }
                                }
                                "DELETE" -> {
                                    val note = database.noteDao().getNoteByIdDirect(op.entityId)
                                    if (note != null && note.isDeleted) {
                                        // Upload tombstone so other devices learn of the deletion
                                        val uploadRes = firestoreService.uploadNote(uid, note)
                                        if (uploadRes.isSuccess) {
                                            database.pendingSyncDao().deleteOperation(op.operationId)
                                            pendingProcessed++
                                        }
                                    } else {
                                        val deleteRes = firestoreService.deleteNote(uid, op.entityId)
                                        if (deleteRes.isSuccess) {
                                            database.pendingSyncDao().deleteOperation(op.operationId)
                                            pendingProcessed++
                                        }
                                    }
                                }
                            }
                        }
                        "FOLDER" -> {
                            when (op.operationType) {
                                "CREATE", "UPDATE" -> {
                                    val folder = database.folderDao().getFolderById(op.entityId)
                                    if (folder != null) {
                                        val uploadRes = firestoreService.uploadFolder(uid, folder)
                                        if (uploadRes.isSuccess) {
                                            database.folderDao().insertFolderSync(
                                                folder.copy(syncStatus = "SYNCED")
                                            )
                                            database.pendingSyncDao().deleteOperation(op.operationId)
                                            pendingProcessed++
                                        } else {
                                            database.pendingSyncDao().updateRetry(
                                                op.operationId,
                                                op.retryCount + 1,
                                                uploadRes.exceptionOrNull()?.localizedMessage
                                            )
                                        }
                                    } else {
                                        database.pendingSyncDao().deleteOperation(op.operationId)
                                    }
                                }
                                "DELETE" -> {
                                    val folder = database.folderDao().getFolderById(op.entityId)
                                    if (folder != null && folder.isDeleted) {
                                        val uploadRes = firestoreService.uploadFolder(uid, folder)
                                        if (uploadRes.isSuccess) {
                                            database.pendingSyncDao().deleteOperation(op.operationId)
                                            pendingProcessed++
                                        }
                                    } else {
                                        val deleteRes = firestoreService.deleteFolder(uid, op.entityId)
                                        if (deleteRes.isSuccess) {
                                            database.pendingSyncDao().deleteOperation(op.operationId)
                                            pendingProcessed++
                                        }
                                    }
                                }
                            }
                        }
                    }
                } catch (singleOpErr: Exception) {
                    Log.w(tag, "Failed processing pending operation ${op.operationId}", singleOpErr)
                }
            }

            // STEP 2: SYNC FOLDERS TWO-WAY WITH RECONCILIATION
            try {
                val localFolders = database.folderDao().getAllFoldersIncludingDeletedDirect()
                val localFolderMap = localFolders.associateBy { it.id }
                val remoteFoldersResult = firestoreService.fetchAllFolders(uid)

                if (remoteFoldersResult.isSuccess) {
                    val remoteFolders = remoteFoldersResult.getOrThrow()
                    val remoteFolderMap = remoteFolders.associateBy { it.id }

                    // Check remote folders vs local
                    for (remoteFolder in remoteFolders) {
                        val localFolder = localFolderMap[remoteFolder.id]
                        if (localFolder == null) {
                            // Folder exists remotely, insert locally
                            database.folderDao().insertFolderSync(remoteFolder)
                        } else {
                            if (remoteFolder.updatedAt > localFolder.updatedAt) {
                                // If remote folder was renamed, update folder name and all notes in Room atomically!
                                if (remoteFolder.name != localFolder.name) {
                                    database.renameFolderWithNotes(
                                        oldName = localFolder.name,
                                        newName = remoteFolder.name,
                                        timestamp = remoteFolder.updatedAt
                                    )
                                }
                                database.folderDao().insertFolderSync(remoteFolder)
                            }
                        }
                    }

                    // Upload local folders not yet in remote
                    for (localFolder in localFolders) {
                        if (!remoteFolderMap.containsKey(localFolder.id) && !localFolder.isDeleted) {
                            firestoreService.uploadFolder(uid, localFolder)
                        }
                    }
                }
            } catch (folderErr: Exception) {
                Log.w(tag, "Folder sync reconciliation warning", folderErr)
            }

            // STEP 3: SYNC NOTES TWO-WAY WITH DETERMINISTIC CONFLICT RESOLUTION
            try {
                val localNotes = database.noteDao().getAllNotesDirect()
                val localNotesMap = localNotes.associateBy { it.id }
                val remoteNotesResult = firestoreService.fetchAllNotes(uid)

                if (remoteNotesResult.isSuccess) {
                    val remoteNotes = remoteNotesResult.getOrThrow()
                    val remoteNotesMap = remoteNotes.associateBy { it.id }

                    for (localNote in localNotes) {
                        val remoteNote = remoteNotesMap[localNote.id]
                        if (remoteNote == null) {
                            // Note only exists locally
                            if (!localNote.isDeleted) {
                                firestoreService.uploadNote(uid, localNote)
                                database.noteDao().insertNoteSync(
                                    localNote.copy(syncStatus = "SYNCED", syncedAt = System.currentTimeMillis())
                                )
                                uploadedNotesCount++
                            }
                        } else {
                            // Note exists both locally and remotely
                            val localHasPendingEdits = localNote.syncStatus == "PENDING_UPLOAD" ||
                                    localNote.syncStatus == "PENDING_DELETE" ||
                                    database.pendingSyncDao().getPendingOperationsForEntity(localNote.id).isNotEmpty()

                            val remoteModifiedByOther = remoteNote.lastModifiedDeviceId.isNotEmpty() &&
                                    remoteNote.lastModifiedDeviceId != localDeviceId

                            if (localHasPendingEdits && remoteModifiedByOther && remoteNote.updatedAt != localNote.updatedAt) {
                                // CONFLICT DETECTED!
                                Log.i(tag, "Conflict detected on note: ${localNote.id} ('${localNote.title}')")

                                val isContentIdentical = localNote.title == remoteNote.title &&
                                        localNote.content == remoteNote.content &&
                                        localNote.checklistJson == remoteNote.checklistJson

                                if (isContentIdentical) {
                                    // Non-conflicting: merge metadata
                                    val mergedNote = localNote.copy(
                                        version = maxOf(localNote.version, remoteNote.version) + 1,
                                        updatedAt = maxOf(localNote.updatedAt, remoteNote.updatedAt),
                                        syncStatus = "SYNCED",
                                        syncedAt = System.currentTimeMillis()
                                    )
                                    database.noteDao().insertNoteSync(mergedNote)
                                    firestoreService.uploadNote(uid, mergedNote)
                                } else {
                                    // Content conflict: Zero data loss strategy!
                                    // 1. Keep newest version as primary
                                    // 2. Preserve other version as conflict copy note: "${title} (conflict copy)"
                                    conflictsCount++
                                    val (primaryNote, conflictSource) = if (localNote.updatedAt >= remoteNote.updatedAt) {
                                        localNote to remoteNote
                                    } else {
                                        remoteNote to localNote
                                    }

                                    // Save primary
                                    database.noteDao().insertNoteSync(
                                        primaryNote.copy(
                                            syncStatus = "SYNCED",
                                            syncedAt = System.currentTimeMillis()
                                        )
                                    )
                                    firestoreService.uploadNote(uid, primaryNote)

                                    // Create conflict copy
                                    val conflictCopyId = "note_conflict_${UUID.randomUUID().toString().take(8)}"
                                    val conflictNote = conflictSource.copy(
                                        id = conflictCopyId,
                                        title = "${conflictSource.title.ifBlank { "Untitled Note" }} (conflict copy)",
                                        updatedAt = System.currentTimeMillis(),
                                        syncStatus = "PENDING_UPLOAD",
                                        syncedAt = 0L,
                                        remoteId = null,
                                        version = 1L,
                                        lastModifiedDeviceId = localDeviceId
                                    )
                                    database.noteDao().insertNoteSync(conflictNote)
                                    database.pendingSyncDao().enqueueCoalesced("NOTE", conflictCopyId, "CREATE")
                                    firestoreService.uploadNote(uid, conflictNote)
                                    uploadedNotesCount++
                                }
                            } else {
                                // No conflict: Standard deterministic synchronization
                                if (localNote.updatedAt > remoteNote.updatedAt) {
                                    firestoreService.uploadNote(uid, localNote)
                                    database.noteDao().insertNoteSync(
                                        localNote.copy(syncStatus = "SYNCED", syncedAt = System.currentTimeMillis())
                                    )
                                    uploadedNotesCount++
                                } else if (remoteNote.updatedAt > localNote.updatedAt) {
                                    // Remote is newer: accept remote changes
                                    database.noteDao().insertNoteSync(
                                        remoteNote.copy(syncStatus = "SYNCED", syncedAt = System.currentTimeMillis())
                                    )
                                    downloadedNotesCount++
                                } else {
                                    // In sync
                                    if (localNote.syncStatus != "SYNCED") {
                                        database.noteDao().insertNoteSync(
                                            localNote.copy(syncStatus = "SYNCED", syncedAt = System.currentTimeMillis())
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Remote notes that don't exist locally
                    for (remoteNote in remoteNotes) {
                        if (!localNotesMap.containsKey(remoteNote.id)) {
                            database.noteDao().insertNoteSync(
                                remoteNote.copy(syncStatus = "SYNCED", syncedAt = System.currentTimeMillis())
                            )
                            downloadedNotesCount++
                        }
                    }
                }
            } catch (noteErr: Exception) {
                Log.w(tag, "Note sync reconciliation warning", noteErr)
            }

            // STEP 4: SYNC DOCUMENTS METADATA & GOOGLE DRIVE STORAGE
            var downloadedDocsCount = 0
            try {
                val isDriveConnected = driveService.checkAuthorization() is DriveAuthState.Connected

                if (isDriveConnected) {
                    // 1. Run legacy Firebase Storage to Drive migration (non-destructive)
                    try {
                        migrationManager.migratePendingDocuments()
                    } catch (mErr: Exception) {
                        Log.w(tag, "Document migration warning: ${mErr.message}")
                    }

                    // 2. Upload pending documents to Google Drive NOTES/Documents
                    val localDocs = database.documentDao().getAllDocumentsDirect()
                    for (doc in localDocs) {
                        if (!doc.isDeleted && (doc.uploadState == "PENDING_UPLOAD" || (doc.driveFileId == null && doc.localPath.isNotBlank()))) {
                            val localFile = File(doc.localPath)
                            if (localFile.exists() && localFile.canRead() && localFile.length() > 0) {
                                database.documentDao().updateUploadState(doc.id, "UPLOADING")
                                val uploadRes = driveDocumentService.uploadDocument(doc, localFile)
                                if (uploadRes.isSuccess) {
                                    val uploadResult = uploadRes.getOrThrow()
                                    val now = System.currentTimeMillis()
                                    database.documentDao().updateDriveSyncStatus(
                                        id = doc.id,
                                        driveFileId = uploadResult.driveFileId,
                                        syncStatus = "SYNCED",
                                        uploadState = "UPLOADED",
                                        lastSyncedAt = now,
                                        updatedAt = now
                                    )
                                    uploadedDocsCount++
                                } else {
                                    Log.w(tag, "Document upload failed for ${doc.id}: ${uploadRes.exceptionOrNull()?.message}")
                                    database.documentDao().updateUploadState(doc.id, "FAILED")
                                }
                            }
                        }
                    }

                    // 3. Reconcile documents from Firestore
                    val remoteDocsRes = firestoreService.fetchAllDocuments(uid)
                    if (remoteDocsRes.isSuccess) {
                        val remoteDocs = remoteDocsRes.getOrDefault(emptyList())
                        for (remoteDoc in remoteDocs) {
                            val localDoc = database.documentDao().getDocumentByIdDirect(remoteDoc.id)
                            if (localDoc == null) {
                                // Discovered on another device: store metadata as CLOUD_ONLY
                                val cloudOnlyDoc = remoteDoc.copy(
                                    downloadState = "CLOUD_ONLY",
                                    localPath = "",
                                    uploadState = if (remoteDoc.driveFileId != null) "UPLOADED" else "IDLE"
                                )
                                database.documentDao().insertDocument(cloudOnlyDoc)
                                downloadedDocsCount++
                            } else if (localDoc.driveFileId == null && remoteDoc.driveFileId != null) {
                                database.documentDao().updateDriveSyncStatus(
                                    id = localDoc.id,
                                    driveFileId = remoteDoc.driveFileId,
                                    syncStatus = "SYNCED",
                                    uploadState = "UPLOADED",
                                    lastSyncedAt = remoteDoc.lastSyncedAt
                                )
                            }
                        }
                    }

                    // 4. Update Firestore metadata with latest Drive references
                    val updatedLocalDocs = database.documentDao().getAllDocumentsDirect()
                    for (doc in updatedLocalDocs) {
                        try {
                            firestoreService.uploadDocumentMetadata(uid, doc)
                        } catch (singleDocErr: Exception) {
                            Log.w(tag, "Document metadata upload skipped for ${doc.id}", singleDocErr)
                        }
                    }
                } else {
                    // Google Drive not connected: gracefully sync metadata with Firestore
                    val localDocs = database.documentDao().getAllDocumentsDirect()
                    for (doc in localDocs) {
                        try {
                            firestoreService.uploadDocumentMetadata(uid, doc)
                        } catch (singleDocErr: Exception) {
                            Log.w(tag, "Document ${doc.id} metadata sync skipped", singleDocErr)
                        }
                    }
                }
            } catch (docSyncErr: Exception) {
                Log.w(tag, "Document sync general warning", docSyncErr)
            }

            val now = System.currentTimeMillis()
            _lastSyncTimestamp.value = now
            _syncState.value = SyncState.SYNCED

            val report = SyncReport(
                uploadedNotes = uploadedNotesCount,
                downloadedNotes = downloadedNotesCount,
                uploadedDocuments = uploadedDocsCount,
                downloadedDocuments = downloadedDocsCount,
                conflictsResolved = conflictsCount,
                pendingOperationsProcessed = pendingProcessed
            )
            _lastSyncReport.value = report
            Log.i(tag, "Sync completed successfully: $report")
            Result.success(report)
        } catch (e: Exception) {
            Log.e(tag, "Sync cycle encountered an error", e)
            _syncState.value = SyncState.ERROR
            val report = SyncReport(errorMessage = e.localizedMessage ?: "Sync error")
            _lastSyncReport.value = report
            Result.failure(e)
        }
    }
}
