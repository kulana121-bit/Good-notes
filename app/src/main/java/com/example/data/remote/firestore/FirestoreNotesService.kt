package com.example.data.remote.firestore

import android.content.Context
import android.util.Log
import com.example.data.local.entity.DocumentEntity
import com.example.data.local.entity.FolderEntity
import com.example.data.local.entity.NoteEntity
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class FirestoreNotesService(private val context: Context) {

    private val tag = "FirestoreNotesService"

    private val isFirebaseInitialized: Boolean
        get() = try {
            FirebaseApp.getApps(context).isNotEmpty()
        } catch (_: Exception) {
            false
        }

    private val firestore: FirebaseFirestore?
        get() = if (isFirebaseInitialized) FirebaseFirestore.getInstance() else null

    suspend fun uploadNote(uid: String, note: NoteEntity): Result<Unit> = withContext(Dispatchers.IO) {
        val db = firestore ?: return@withContext Result.failure(Exception("Firebase is not initialized"))

        try {
            val noteMap = hashMapOf(
                "id" to note.id,
                "title" to note.title,
                "content" to note.content,
                "folder" to note.folder,
                "isFavorite" to note.isFavorite,
                "isDeleted" to note.isDeleted,
                "createdAt" to note.createdAt,
                "updatedAt" to note.updatedAt,
                "cardType" to note.cardType,
                "isTodo" to note.isTodo,
                "isImportant" to note.isImportant,
                "checklistJson" to note.checklistJson,
                "sharedWithJson" to note.sharedWithJson,
                "tagsJson" to note.tagsJson,
                "noteCountText" to note.noteCountText,
                "imageUri" to note.imageUri,
                "audioUri" to note.audioUri,
                "audioDurationMs" to note.audioDurationMs,
                "serverUpdatedAt" to FieldValue.serverTimestamp()
            )

            db.collection("users")
                .document(uid)
                .collection("notes")
                .document(note.id)
                .set(noteMap, SetOptions.merge())
                .await()

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(tag, "Failed to upload note ${note.id}", e)
            Result.failure(e)
        }
    }

    suspend fun fetchAllNotes(uid: String): Result<List<NoteEntity>> = withContext(Dispatchers.IO) {
        val db = firestore ?: return@withContext Result.failure(Exception("Firebase is not initialized"))

        try {
            val snapshot = db.collection("users")
                .document(uid)
                .collection("notes")
                .get()
                .await()

            val notes = snapshot.documents.mapNotNull { doc ->
                val id = doc.getString("id") ?: doc.id
                val title = doc.getString("title") ?: ""
                val content = doc.getString("content") ?: ""
                val folder = doc.getString("folder") ?: "Personal"
                val isFavorite = doc.getBoolean("isFavorite") ?: false
                val isDeleted = doc.getBoolean("isDeleted") ?: false
                val createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis()
                val updatedAt = doc.getLong("updatedAt") ?: System.currentTimeMillis()
                val cardType = doc.getString("cardType") ?: "CREAM_LECTURE"
                val isTodo = doc.getBoolean("isTodo") ?: false
                val isImportant = doc.getBoolean("isImportant") ?: false
                val checklistJson = doc.getString("checklistJson") ?: "[]"
                val sharedWithJson = doc.getString("sharedWithJson") ?: "[]"
                val tagsJson = doc.getString("tagsJson") ?: "[]"
                val noteCountText = doc.getString("noteCountText")
                val imageUri = doc.getString("imageUri")
                val audioUri = doc.getString("audioUri")
                val audioDurationMs = doc.getLong("audioDurationMs") ?: 0L

                NoteEntity(
                    id = id,
                    title = title,
                    content = content,
                    folder = folder,
                    isFavorite = isFavorite,
                    isDeleted = isDeleted,
                    createdAt = createdAt,
                    updatedAt = updatedAt,
                    cardType = cardType,
                    isTodo = isTodo,
                    isImportant = isImportant,
                    checklistJson = checklistJson,
                    sharedWithJson = sharedWithJson,
                    tagsJson = tagsJson,
                    noteCountText = noteCountText,
                    imageUri = imageUri,
                    audioUri = audioUri,
                    audioDurationMs = audioDurationMs,
                    syncStatus = "SYNCED",
                    syncedAt = System.currentTimeMillis(),
                    remoteId = id
                )
            }

            Result.success(notes)
        } catch (e: Exception) {
            Log.e(tag, "Failed to fetch cloud notes", e)
            Result.failure(e)
        }
    }

    suspend fun uploadFolder(uid: String, folder: FolderEntity): Result<Unit> = withContext(Dispatchers.IO) {
        val db = firestore ?: return@withContext Result.failure(Exception("Firebase is not initialized"))

        try {
            val folderMap = hashMapOf(
                "id" to folder.id,
                "name" to folder.name,
                "colorHex" to folder.colorHex,
                "createdAt" to folder.createdAt,
                "serverUpdatedAt" to FieldValue.serverTimestamp()
            )

            db.collection("users")
                .document(uid)
                .collection("folders")
                .document(folder.id)
                .set(folderMap, SetOptions.merge())
                .await()

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(tag, "Failed to upload folder ${folder.id}", e)
            Result.failure(e)
        }
    }

    suspend fun fetchAllFolders(uid: String): Result<List<FolderEntity>> = withContext(Dispatchers.IO) {
        val db = firestore ?: return@withContext Result.failure(Exception("Firebase is not initialized"))

        try {
            val snapshot = db.collection("users")
                .document(uid)
                .collection("folders")
                .get()
                .await()

            val folders = snapshot.documents.mapNotNull { doc ->
                val id = doc.getString("id") ?: doc.id
                val name = doc.getString("name") ?: return@mapNotNull null
                val colorHex = doc.getLong("colorHex") ?: 0xFFEB7A53
                val createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis()

                FolderEntity(
                    id = id,
                    name = name,
                    colorHex = colorHex,
                    createdAt = createdAt
                )
            }

            Result.success(folders)
        } catch (e: Exception) {
            Log.e(tag, "Failed to fetch cloud folders", e)
            Result.failure(e)
        }
    }

    suspend fun uploadDocumentMetadata(uid: String, document: DocumentEntity): Result<Unit> = withContext(Dispatchers.IO) {
        val db = firestore ?: return@withContext Result.failure(Exception("Firebase is not initialized"))

        try {
            val docMap = hashMapOf(
                "id" to document.id,
                "fileName" to document.fileName,
                "displayName" to document.displayName,
                "fileSize" to document.fileSize,
                "mimeType" to document.mimeType,
                "pageCount" to document.pageCount,
                "createdAt" to document.createdAt,
                "updatedAt" to document.updatedAt,
                "lastOpenedAt" to document.lastOpenedAt,
                "isFavorite" to document.isFavorite,
                "isDeleted" to document.isDeleted,
                "remoteStorageRef" to document.remoteStorageRef,
                "accentColorHex" to document.accentColorHex,
                "serverUpdatedAt" to FieldValue.serverTimestamp()
            )

            db.collection("users")
                .document(uid)
                .collection("documents")
                .document(document.id)
                .set(docMap, SetOptions.merge())
                .await()

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(tag, "Failed to upload document metadata ${document.id}", e)
            Result.failure(e)
        }
    }

    suspend fun fetchAllDocuments(uid: String): Result<List<DocumentEntity>> = withContext(Dispatchers.IO) {
        val db = firestore ?: return@withContext Result.failure(Exception("Firebase is not initialized"))

        try {
            val snapshot = db.collection("users")
                .document(uid)
                .collection("documents")
                .get()
                .await()

            val docs = snapshot.documents.mapNotNull { doc ->
                val id = doc.getString("id") ?: doc.id
                val fileName = doc.getString("fileName") ?: return@mapNotNull null
                val displayName = doc.getString("displayName") ?: fileName
                val fileSize = doc.getLong("fileSize") ?: 0L
                val mimeType = doc.getString("mimeType") ?: "application/pdf"
                val pageCount = doc.getLong("pageCount")?.toInt() ?: 1
                val createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis()
                val updatedAt = doc.getLong("updatedAt") ?: System.currentTimeMillis()
                val lastOpenedAt = doc.getLong("lastOpenedAt") ?: System.currentTimeMillis()
                val isFavorite = doc.getBoolean("isFavorite") ?: false
                val isDeleted = doc.getBoolean("isDeleted") ?: false
                val remoteStorageRef = doc.getString("remoteStorageRef")
                val accentColorHex = doc.getLong("accentColorHex") ?: 0xFFFEEA9F

                DocumentEntity(
                    id = id,
                    fileName = fileName,
                    displayName = displayName,
                    localPath = "", // re-linked on download
                    fileSize = fileSize,
                    mimeType = mimeType,
                    pageCount = pageCount,
                    createdAt = createdAt,
                    updatedAt = updatedAt,
                    lastOpenedAt = lastOpenedAt,
                    lastOpenedPage = 0,
                    isFavorite = isFavorite,
                    isDeleted = isDeleted,
                    syncStatus = "SYNCED",
                    remoteStorageRef = remoteStorageRef,
                    accentColorHex = accentColorHex
                )
            }

            Result.success(docs)
        } catch (e: Exception) {
            Log.e(tag, "Failed to fetch cloud documents", e)
            Result.failure(e)
        }
    }
}
