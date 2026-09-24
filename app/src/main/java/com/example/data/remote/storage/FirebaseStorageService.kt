package com.example.data.remote.storage

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File

class FirebaseStorageService(private val context: Context) {

    private val tag = "FirebaseStorageService"

    private val isFirebaseInitialized: Boolean
        get() = try {
            FirebaseApp.getApps(context).isNotEmpty()
        } catch (_: Throwable) {
            false
        }

    private val storage: FirebaseStorage?
        get() = if (isFirebaseInitialized) {
            try {
                FirebaseStorage.getInstance()
            } catch (t: Throwable) {
                Log.w(tag, "FirebaseStorage.getInstance failed: ${t.message}")
                null
            }
        } else null

    suspend fun uploadPdfDocument(
        uid: String,
        documentId: String,
        file: File
    ): Result<String> = withContext(Dispatchers.IO) {
        val storageInstance = storage ?: return@withContext Result.failure(Exception("Firebase Storage is not initialized"))

        if (!file.exists() || !file.isFile) {
            return@withContext Result.failure(Exception("File does not exist: ${file.absolutePath}"))
        }

        try {
            val safeName = file.name.replace("[^a-zA-Z0-9._-]".toRegex(), "_")
            val storagePath = "users/$uid/documents/$documentId/$safeName"
            val ref = storageInstance.reference.child(storagePath)

            val fileUri = Uri.fromFile(file)
            ref.putFile(fileUri).await()

            Result.success(storagePath)
        } catch (e: Exception) {
            Log.e(tag, "Failed to upload document file ${file.name}", e)
            Result.failure(e)
        }
    }

    suspend fun uploadPdfUri(
        uid: String,
        documentId: String,
        uri: Uri,
        displayName: String
    ): Result<String> = withContext(Dispatchers.IO) {
        val storageInstance = storage ?: return@withContext Result.failure(Exception("Firebase Storage is not initialized"))

        try {
            val safeName = displayName.replace("[^a-zA-Z0-9._-]".toRegex(), "_").ifBlank { "document" }
            val storagePath = "users/$uid/documents/$documentId/$safeName.pdf"
            val ref = storageInstance.reference.child(storagePath)

            ref.putFile(uri).await()
            Result.success(storagePath)
        } catch (e: Exception) {
            Log.e(tag, "Failed to upload document URI $uri", e)
            Result.failure(e)
        }
    }

    suspend fun downloadPdfDocument(
        storagePath: String,
        destinationFile: File
    ): Result<File> = withContext(Dispatchers.IO) {
        val storageInstance = storage ?: return@withContext Result.failure(Exception("Firebase Storage is not initialized"))

        try {
            val ref = storageInstance.reference.child(storagePath)
            ref.getFile(destinationFile).await()
            Result.success(destinationFile)
        } catch (e: Exception) {
            Log.e(tag, "Failed to download document from $storagePath", e)
            Result.failure(e)
        }
    }
}
