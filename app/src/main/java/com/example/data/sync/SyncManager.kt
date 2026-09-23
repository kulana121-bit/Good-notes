package com.example.data.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.example.data.local.NotesDatabase
import com.example.data.local.entity.DocumentEntity
import com.example.data.local.entity.FolderEntity
import com.example.data.local.entity.NoteEntity
import com.example.data.remote.auth.FirebaseAuthService
import com.example.data.remote.firestore.FirestoreNotesService
import com.example.data.remote.storage.FirebaseStorageService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File

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
    val errorMessage: String? = null
)

class SyncManager(
    private val context: Context,
    private val database: NotesDatabase,
    private val authService: FirebaseAuthService = FirebaseAuthService(context),
    private val firestoreService: FirestoreNotesService = FirestoreNotesService(context),
    private val storageService: FirebaseStorageService = FirebaseStorageService(context)
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

    /**
     * Deterministic Last-Write-Wins (LWW) conflict strategy implementation:
     * - Compare local.updatedAt vs remote.updatedAt
     * - If remote.updatedAt > local.updatedAt -> Remote wins, write to Room
     * - If local.updatedAt > remote.updatedAt -> Local wins, upload to Firestore
     * - Soft-delete flags (isDeleted) are treated as regular state changes and propagate naturally.
     */
    suspend fun syncNow(): Result<SyncReport> = withContext(Dispatchers.IO) {
        val uid = authService.getCurrentUserId()
        if (uid.isNullOrBlank()) {
            _syncState.value = SyncState.SIGN_IN_REQUIRED
            return@withContext Result.failure(Exception("Sign in with Google to synchronize your notes."))
        }

        if (!isOnline()) {
            _syncState.value = SyncState.OFFLINE
            return@withContext Result.failure(Exception("No internet connection available. Changes will sync when online."))
        }

        _syncState.value = SyncState.SYNCING

        var uploadedNotesCount = 0
        var downloadedNotesCount = 0
        var uploadedDocsCount = 0

        try {
            // 1. SYNC FOLDERS
            val localFolders = database.folderDao().getAllFoldersDirect()
            val remoteFoldersResult = firestoreService.fetchAllFolders(uid)
            if (remoteFoldersResult.isSuccess) {
                val remoteFolders = remoteFoldersResult.getOrThrow()
                val remoteFolderMap = remoteFolders.associateBy { it.id }

                // Upload missing local folders
                for (localFolder in localFolders) {
                    if (!remoteFolderMap.containsKey(localFolder.id)) {
                        firestoreService.uploadFolder(uid, localFolder)
                    }
                }
                // Insert missing remote folders locally
                val localFolderIds = localFolders.map { it.id }.toSet()
                for (remoteFolder in remoteFolders) {
                    if (!localFolderIds.contains(remoteFolder.id)) {
                        database.folderDao().insertFolderSync(remoteFolder)
                    }
                }
            }

            // 2. SYNC NOTES (LAST-WRITE-WINS)
            val localNotes = database.noteDao().getAllNotesDirect()
            val remoteNotesResult = firestoreService.fetchAllNotes(uid)

            if (remoteNotesResult.isSuccess) {
                val remoteNotes = remoteNotesResult.getOrThrow()
                val remoteNotesMap = remoteNotes.associateBy { it.id }
                val localNotesMap = localNotes.associateBy { it.id }

                // Check local notes against remote
                for (localNote in localNotes) {
                    val remoteNote = remoteNotesMap[localNote.id]
                    if (remoteNote == null) {
                        // Exists locally only -> upload
                        firestoreService.uploadNote(uid, localNote)
                        database.noteDao().insertNoteSync(
                            localNote.copy(syncStatus = "SYNCED", syncedAt = System.currentTimeMillis())
                        )
                        uploadedNotesCount++
                    } else {
                        // Conflict resolution: LAST-WRITE-WINS
                        if (localNote.updatedAt > remoteNote.updatedAt) {
                            // Local is newer -> upload to Firestore
                            firestoreService.uploadNote(uid, localNote)
                            database.noteDao().insertNoteSync(
                                localNote.copy(syncStatus = "SYNCED", syncedAt = System.currentTimeMillis())
                            )
                            uploadedNotesCount++
                        } else if (remoteNote.updatedAt > localNote.updatedAt) {
                            // Remote is newer -> download to Room
                            database.noteDao().insertNoteSync(
                                remoteNote.copy(syncStatus = "SYNCED", syncedAt = System.currentTimeMillis())
                            )
                            downloadedNotesCount++
                        }
                    }
                }

                // Check remote notes not present locally -> insert to Room
                for (remoteNote in remoteNotes) {
                    if (!localNotesMap.containsKey(remoteNote.id)) {
                        database.noteDao().insertNoteSync(
                            remoteNote.copy(syncStatus = "SYNCED", syncedAt = System.currentTimeMillis())
                        )
                        downloadedNotesCount++
                    }
                }
            }

            // 3. SYNC DOCUMENTS METADATA & STORAGE
            val localDocs = database.documentDao().getAllDocumentsDirect()
            for (doc in localDocs) {
                var updatedDoc = doc
                // If local file exists and not yet uploaded to Storage, upload it
                if (doc.remoteStorageRef == null) {
                    val file = File(doc.localPath)
                    if (file.exists() && file.isFile) {
                        val uploadResult = storageService.uploadPdfDocument(uid, doc.id, file)
                        if (uploadResult.isSuccess) {
                            val storageRef = uploadResult.getOrThrow()
                            updatedDoc = doc.copy(
                                remoteStorageRef = storageRef,
                                syncStatus = "SYNCED"
                            )
                            database.documentDao().insertDocumentSync(updatedDoc)
                            uploadedDocsCount++
                        }
                    }
                }
                // Upload metadata to Firestore
                firestoreService.uploadDocumentMetadata(uid, updatedDoc)
            }

            val now = System.currentTimeMillis()
            _lastSyncTimestamp.value = now
            _syncState.value = SyncState.SYNCED

            val report = SyncReport(
                uploadedNotes = uploadedNotesCount,
                downloadedNotes = downloadedNotesCount,
                uploadedDocuments = uploadedDocsCount
            )
            _lastSyncReport.value = report
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
