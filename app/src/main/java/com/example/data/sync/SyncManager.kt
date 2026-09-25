package com.example.data.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
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

    fun isOnline(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    suspend fun syncNow(targetUserId: String? = null): Result<SyncReport> = withContext(Dispatchers.IO) {
        val uid = targetUserId ?: authService.getCurrentUserId()
        if (uid.isNullOrBlank()) {
            _syncState.value = SyncState.SIGN_IN_REQUIRED
            return@withContext Result.failure(Exception("Sign in to synchronize your notes."))
        }

        val currentUserEmail = authService.currentUser.value?.email

        // Account isolation validation
        val lastSyncedUid = database.settingDao().getSettingDirect("last_synced_user_id")
        if (lastSyncedUid != null && lastSyncedUid != uid) {
            Log.i(tag, "Account switch: previous user '$lastSyncedUid' -> current user '$uid'. Ensuring queue isolation.")
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
        var downloadedDocsCount = 0
        var conflictsCount = 0
        var pendingProcessed = 0

        try {
            // STEP 1: DRAIN PENDING OPERATIONS QUEUE FOR THIS SPECIFIC USER
            val pendingOps = database.pendingSyncDao().getAllPendingOperationsDirect(uid)
            for (op in pendingOps) {
                try {
                    when (op.entityType) {
                        "NOTE" -> {
                            when (op.operationType) {
                                "CREATE", "UPDATE" -> {
                                    val note = database.noteDao().getNoteByIdAndUser(uid, op.entityId)
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
                                    val folder = database.folderDao().getFolderByIdAndUser(uid, op.entityId)
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
                val localFolders = database.folderDao().getAllFoldersIncludingDeletedDirect(uid)
                val localFolderMap = localFolders.associateBy { it.id }
                val remoteFoldersResult = firestoreService.fetchAllFolders(uid)

                if (remoteFoldersResult.isSuccess) {
                    val remoteFolders = remoteFoldersResult.getOrThrow()
                    val remoteFolderMap = remoteFolders.associateBy { it.id }

                    for (remoteFolder in remoteFolders) {
                        val folderWithUser = remoteFolder.copy(userId = uid)
                        val localFolder = localFolderMap[folderWithUser.id]
                        if (localFolder == null) {
                            database.folderDao().insertFolderSync(folderWithUser)
                        } else {
                            if (folderWithUser.updatedAt > localFolder.updatedAt) {
                                if (folderWithUser.name != localFolder.name) {
                                    database.renameFolderWithNotes(
                                        userId = uid,
                                        oldName = localFolder.name,
                                        newName = folderWithUser.name,
                                        timestamp = folderWithUser.updatedAt
                                    )
                                }
                                database.folderDao().insertFolderSync(folderWithUser)
                            }
                        }
                    }

                    for (localFolder in localFolders) {
                        if (!remoteFolderMap.containsKey(localFolder.id) && !localFolder.isDeleted) {
                            firestoreService.uploadFolder(uid, localFolder)
                        }
                    }
                }
            } catch (folderErr: Exception) {
                Log.w(tag, "Folder sync reconciliation warning", folderErr)
            }

            // STEP 3: SYNC NOTES TWO-WAY WITH CONFLICT RESOLUTION
            try {
                val localNotes = database.noteDao().getAllNotesDirect(uid)
                val localNotesMap = localNotes.associateBy { it.id }
                val remoteNotesResult = firestoreService.fetchAllNotes(uid)

                if (remoteNotesResult.isSuccess) {
                    val remoteNotes = remoteNotesResult.getOrThrow()
                    val remoteNotesMap = remoteNotes.associateBy { it.id }

                    for (localNote in localNotes) {
                        val remoteNote = remoteNotesMap[localNote.id]
                        if (remoteNote == null) {
                            if (!localNote.isDeleted) {
                                firestoreService.uploadNote(uid, localNote)
                                database.noteDao().insertNoteSync(
                                    localNote.copy(syncStatus = "SYNCED", syncedAt = System.currentTimeMillis())
                                )
                                uploadedNotesCount++
                            }
                        } else {
                            val localHasPendingEdits = localNote.syncStatus == "PENDING_UPLOAD" ||
                                    localNote.syncStatus == "PENDING_DELETE" ||
                                    database.pendingSyncDao().getPendingOperationsForEntity(uid, localNote.id).isNotEmpty()

                            val remoteModifiedByOther = remoteNote.lastModifiedDeviceId.isNotEmpty() &&
                                    remoteNote.lastModifiedDeviceId != localDeviceId

                            if (localHasPendingEdits && remoteModifiedByOther && remoteNote.updatedAt != localNote.updatedAt) {
                                Log.i(tag, "Conflict detected on note: ${localNote.id} ('${localNote.title}')")

                                val isContentIdentical = localNote.title == remoteNote.title &&
                                        localNote.content == remoteNote.content &&
                                        localNote.checklistJson == remoteNote.checklistJson

                                if (isContentIdentical) {
                                    val mergedNote = localNote.copy(
                                        userId = uid,
                                        version = maxOf(localNote.version, remoteNote.version) + 1,
                                        updatedAt = maxOf(localNote.updatedAt, remoteNote.updatedAt),
                                        syncStatus = "SYNCED",
                                        syncedAt = System.currentTimeMillis()
                                    )
                                    database.noteDao().insertNoteSync(mergedNote)
                                    firestoreService.uploadNote(uid, mergedNote)
                                } else {
                                    conflictsCount++
                                    val (primaryNote, conflictSource) = if (localNote.updatedAt >= remoteNote.updatedAt) {
                                        localNote to remoteNote
                                    } else {
                                        remoteNote to localNote
                                    }

                                    database.noteDao().insertNoteSync(
                                        primaryNote.copy(
                                            userId = uid,
                                            syncStatus = "SYNCED",
                                            syncedAt = System.currentTimeMillis()
                                        )
                                    )
                                    firestoreService.uploadNote(uid, primaryNote)

                                    val conflictCopyId = "note_conflict_${UUID.randomUUID().toString().take(8)}"
                                    val conflictNote = conflictSource.copy(
                                        id = conflictCopyId,
                                        userId = uid,
                                        title = "${conflictSource.title.ifBlank { "Untitled Note" }} (conflict copy)",
                                        updatedAt = System.currentTimeMillis(),
                                        syncStatus = "PENDING_UPLOAD",
                                        syncedAt = 0L,
                                        remoteId = null,
                                        version = 1L,
                                        lastModifiedDeviceId = localDeviceId
                                    )
                                    database.noteDao().insertNoteSync(conflictNote)
                                    database.pendingSyncDao().enqueueCoalesced(userId = uid, entityType = "NOTE", entityId = conflictCopyId, operationType = "CREATE")
                                    firestoreService.uploadNote(uid, conflictNote)
                                    uploadedNotesCount++
                                }
                            } else {
                                if (localNote.updatedAt > remoteNote.updatedAt) {
                                    firestoreService.uploadNote(uid, localNote)
                                    database.noteDao().insertNoteSync(
                                        localNote.copy(syncStatus = "SYNCED", syncedAt = System.currentTimeMillis())
                                    )
                                    uploadedNotesCount++
                                } else if (remoteNote.updatedAt > localNote.updatedAt) {
                                    database.noteDao().insertNoteSync(
                                        remoteNote.copy(userId = uid, syncStatus = "SYNCED", syncedAt = System.currentTimeMillis())
                                    )
                                    downloadedNotesCount++
                                } else {
                                    if (localNote.syncStatus != "SYNCED") {
                                        database.noteDao().insertNoteSync(
                                            localNote.copy(syncStatus = "SYNCED", syncedAt = System.currentTimeMillis())
                                        )
                                    }
                                }
                            }
                        }
                    }

                    for (remoteNote in remoteNotes) {
                        if (!localNotesMap.containsKey(remoteNote.id)) {
                            database.noteDao().insertNoteSync(
                                remoteNote.copy(userId = uid, syncStatus = "SYNCED", syncedAt = System.currentTimeMillis())
                            )
                            downloadedNotesCount++
                        }
                    }
                }
            } catch (noteErr: Exception) {
                Log.w(tag, "Note sync reconciliation warning", noteErr)
            }

            // STEP 4: SYNC DOCUMENTS METADATA & GOOGLE DRIVE STORAGE
            try {
                val driveAuthState = driveService.checkAuthorization(expectedEmail = currentUserEmail)

                if (driveAuthState is DriveAuthState.Connected) {
                    // 1. Upload pending documents to Google Drive
                    val localDocs = database.documentDao().getAllDocumentsDirect(uid)
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

                    // 2. Reconcile documents from Firestore
                    val remoteDocsRes = firestoreService.fetchAllDocuments(uid)
                    if (remoteDocsRes.isSuccess) {
                        val remoteDocs = remoteDocsRes.getOrDefault(emptyList())
                        for (remoteDoc in remoteDocs) {
                            val localDoc = database.documentDao().getDocumentByIdDirect(remoteDoc.id)
                            if (localDoc == null) {
                                val cloudOnlyDoc = remoteDoc.copy(
                                    userId = uid,
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

                    // 3. Update Firestore metadata with latest Drive references
                    val updatedLocalDocs = database.documentDao().getAllDocumentsDirect(uid)
                    for (doc in updatedLocalDocs) {
                        try {
                            firestoreService.uploadDocumentMetadata(uid, doc)
                        } catch (singleDocErr: Exception) {
                            Log.w(tag, "Document metadata upload skipped for ${doc.id}", singleDocErr)
                        }
                    }
                } else if (driveAuthState is DriveAuthState.AccountMismatch) {
                    Log.w(tag, "Google Drive account mismatch (${driveAuthState.driveEmail} != ${driveAuthState.firebaseEmail}). Skipping Drive uploads to protect account isolation.")
                } else {
                    // Drive not connected: sync metadata to Firestore
                    val localDocs = database.documentDao().getAllDocumentsDirect(uid)
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
            Log.i(tag, "Sync cycle completed successfully for user '$uid': $report")
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
