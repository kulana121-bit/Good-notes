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

    suspend fun syncNow(): Result<SyncReport> = withContext(Dispatchers.IO) {
        val uid = authService.getCurrentUserId()
        if (uid.isNullOrBlank()) {
            _syncState.value = SyncState.SIGN_IN_REQUIRED
            return@withContext Result.failure(Exception("Sign in to synchronize your notes."))
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
            try {
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
            } catch (folderSyncErr: Exception) {
                Log.w(tag, "Folder sync warning", folderSyncErr)
            }

            // 2. SYNC NOTES (LAST-WRITE-WINS)
            try {
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
                            firestoreService.uploadNote(uid, localNote)
                            database.noteDao().insertNoteSync(
                                localNote.copy(syncStatus = "SYNCED", syncedAt = System.currentTimeMillis())
                            )
                            uploadedNotesCount++
                        } else {
                            if (localNote.updatedAt > remoteNote.updatedAt) {
                                firestoreService.uploadNote(uid, localNote)
                                database.noteDao().insertNoteSync(
                                    localNote.copy(syncStatus = "SYNCED", syncedAt = System.currentTimeMillis())
                                )
                                uploadedNotesCount++
                            } else if (remoteNote.updatedAt > localNote.updatedAt) {
                                database.noteDao().insertNoteSync(
                                    remoteNote.copy(syncStatus = "SYNCED", syncedAt = System.currentTimeMillis())
                                )
                                downloadedNotesCount++
                            }
                        }
                    }

                    // Check remote notes not present locally
                    for (remoteNote in remoteNotes) {
                        if (!localNotesMap.containsKey(remoteNote.id)) {
                            database.noteDao().insertNoteSync(
                                remoteNote.copy(syncStatus = "SYNCED", syncedAt = System.currentTimeMillis())
                            )
                            downloadedNotesCount++
                        }
                    }
                }
            } catch (noteSyncErr: Exception) {
                Log.w(tag, "Note sync warning", noteSyncErr)
            }

            // 3. SYNC DOCUMENTS METADATA & STORAGE SAFELY
            try {
                val localDocs = database.documentDao().getAllDocumentsDirect()
                for (doc in localDocs) {
                    try {
                        var updatedDoc = doc
                        if (doc.remoteStorageRef == null) {
                            if (doc.localPath.startsWith("content://")) {
                                val uri = Uri.parse(doc.localPath)
                                val uploadResult = storageService.uploadPdfUri(uid, doc.id, uri, doc.displayName)
                                if (uploadResult.isSuccess) {
                                    val storageRef = uploadResult.getOrNull()
                                    updatedDoc = doc.copy(
                                        remoteStorageRef = storageRef,
                                        syncStatus = "SYNCED"
                                    )
                                    database.documentDao().insertDocumentSync(updatedDoc)
                                    uploadedDocsCount++
                                }
                            } else {
                                val file = File(doc.localPath)
                                if (file.exists() && file.isFile) {
                                    val uploadResult = storageService.uploadPdfDocument(uid, doc.id, file)
                                    if (uploadResult.isSuccess) {
                                        val storageRef = uploadResult.getOrNull()
                                        updatedDoc = doc.copy(
                                            remoteStorageRef = storageRef,
                                            syncStatus = "SYNCED"
                                        )
                                        database.documentDao().insertDocumentSync(updatedDoc)
                                        uploadedDocsCount++
                                    }
                                }
                            }
                        }
                        firestoreService.uploadDocumentMetadata(uid, updatedDoc)
                    } catch (singleDocErr: Exception) {
                        Log.w(tag, "Document ${doc.id} sync skipped", singleDocErr)
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
