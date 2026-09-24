package com.example.data.remote.drive

import android.content.Context
import android.util.Log
import com.example.data.local.entity.DocumentEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class GoogleDriveDocumentService(
    private val context: Context,
    private val authManager: GoogleDriveAuthManager = GoogleDriveAuthManager(context),
    private val driveService: GoogleDriveService = GoogleDriveService(context)
) {
    private val tag = "GoogleDriveDocService"

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()

    // Cached folder ID for rapid document operations
    @Volatile
    private var cachedDocumentsFolderId: String? = null

    /**
     * Resolves the "My Drive/NOTES/Documents" folder ID.
     * Auto-heals by validating existence and recreating if the folder was trashed or deleted.
     */
    suspend fun getOrCreateDocumentsFolder(forceRefresh: Boolean = false): Result<String> = withContext(Dispatchers.IO) {
        val currentFolderId = cachedDocumentsFolderId
        if (!forceRefresh && currentFolderId != null) {
            val exists = verifyFolderExists(currentFolderId)
            if (exists) {
                return@withContext Result.success(currentFolderId)
            }
        }

        // Folder is missing or not cached yet: resolve from GoogleDriveService
        val structureResult = driveService.getOrCreateNotesFolderStructure()
        structureResult.map { structure ->
            cachedDocumentsFolderId = structure.documentsFolderId
            structure.documentsFolderId
        }
    }

    /**
     * Verifies if a folder exists and is not trashed on Google Drive.
     */
    private suspend fun verifyFolderExists(folderId: String): Boolean {
        val token = authManager.getAccessToken().getOrNull() ?: return false
        return try {
            val url = "https://www.googleapis.com/drive/v3/files/$folderId?fields=id,trashed"
            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .get()
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string().orEmpty()
                val json = JSONObject(body)
                !json.optBoolean("trashed", false)
            } else {
                false
            }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Uploads a document to Google Drive inside NOTES/Documents.
     * Uses streaming resumable upload for memory safety on large PDFs.
     * Pre-checks storage quota to prevent failing mid-upload.
     */
    suspend fun uploadDocument(
        document: DocumentEntity,
        file: File
    ): Result<DriveUploadResult> = withContext(Dispatchers.IO) {
        val token = authManager.getAccessToken().getOrElse {
            return@withContext Result.failure(it)
        }

        if (!file.exists() || !file.canRead() || file.length() == 0L) {
            return@withContext Result.failure(IllegalArgumentException("Local document file is missing or empty: ${file.absolutePath}"))
        }

        val fileSize = file.length()

        // 1. Quota Check
        try {
            val quota = driveService.getStorageQuota().getOrNull()
            if (quota != null && quota.availableBytes != null && quota.availableBytes < fileSize) {
                return@withContext Result.failure(
                    DriveQuotaExceededException("Not enough Google Drive storage space to upload ${document.displayName} (${formatBytes(fileSize)}).")
                )
            }
        } catch (e: Exception) {
            Log.w(tag, "Quota check non-blocking warning: ${e.message}")
        }

        // 2. Resolve NOTES/Documents folder
        val folderId = getOrCreateDocumentsFolder().getOrElse {
            return@withContext Result.failure(it)
        }

        val targetDriveFileName = "${document.id}_${document.fileName}"
        val mimeType = document.mimeType.ifBlank { "application/pdf" }

        // 3. Duplicate Prevention: Check if file already exists in NOTES/Documents
        val existingFile = findDocumentByTargetName(folderId, targetDriveFileName).getOrNull()
        if (existingFile != null) {
            // Update existing file content via resumable patch
            return@withContext updateExistingDocumentStream(existingFile.id, file, mimeType, targetDriveFileName)
        }

        // 4. Perform Resumable Upload
        try {
            val initUrl = "https://www.googleapis.com/upload/drive/v3/files?uploadType=resumable"
            val metadataJson = JSONObject().apply {
                put("name", targetDriveFileName)
                put("parents", org.json.JSONArray().put(folderId))
                put("description", "NOTES Document ID: ${document.id}")
                put("properties", JSONObject().apply {
                    put("documentId", document.id)
                    put("contentHash", document.contentHash ?: "")
                })
            }

            val initRequest = Request.Builder()
                .url(initUrl)
                .addHeader("Authorization", "Bearer $token")
                .addHeader("X-Upload-Content-Type", mimeType)
                .addHeader("X-Upload-Content-Length", fileSize.toString())
                .post(metadataJson.toString().toRequestBody("application/json; charset=UTF-8".toMediaType()))
                .build()

            val initResponse = okHttpClient.newCall(initRequest).execute()
            if (!initResponse.isSuccessful) {
                val err = initResponse.body?.string().orEmpty()
                return@withContext Result.failure(parseError(initResponse.code, err))
            }

            val sessionUrl = initResponse.header("Location")
                ?: return@withContext Result.failure(IllegalStateException("No resumable session URL returned by Google Drive."))

            // Stream file contents directly from disk
            val fileRequestBody = file.asRequestBody(mimeType.toMediaType())
            val uploadRequest = Request.Builder()
                .url(sessionUrl)
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Content-Length", fileSize.toString())
                .put(fileRequestBody)
                .build()

            val uploadResponse = okHttpClient.newCall(uploadRequest).execute()
            val bodyString = uploadResponse.body?.string().orEmpty()

            if (!uploadResponse.isSuccessful) {
                return@withContext Result.failure(parseError(uploadResponse.code, bodyString))
            }

            val json = JSONObject(bodyString)
            val fileId = json.getString("id")
            val md5 = json.optString("md5Checksum", null)

            Log.i(tag, "Successfully uploaded document ${document.id} to Drive (ID: $fileId)")
            Result.success(
                DriveUploadResult(
                    driveFileId = fileId,
                    fileName = targetDriveFileName,
                    md5Checksum = md5,
                    size = fileSize
                )
            )
        } catch (e: IOException) {
            Log.e(tag, "Network error uploading document ${document.id}", e)
            Result.failure(e)
        } catch (e: Exception) {
            Log.e(tag, "Unexpected error uploading document ${document.id}", e)
            Result.failure(e)
        }
    }

    /**
     * Updates an existing file's content in Drive via resumable streaming patch.
     */
    suspend fun updateExistingDocument(
        driveFileId: String,
        file: File,
        mimeType: String
    ): Result<DriveUploadResult> = withContext(Dispatchers.IO) {
        val targetName = file.name
        updateExistingDocumentStream(driveFileId, file, mimeType, targetName)
    }

    private suspend fun updateExistingDocumentStream(
        driveFileId: String,
        file: File,
        mimeType: String,
        fileName: String
    ): Result<DriveUploadResult> = withContext(Dispatchers.IO) {
        val token = authManager.getAccessToken().getOrElse {
            return@withContext Result.failure(it)
        }

        val fileSize = file.length()

        try {
            val initUrl = "https://www.googleapis.com/upload/drive/v3/files/$driveFileId?uploadType=resumable"
            val metadataJson = JSONObject().apply {
                put("name", fileName)
            }

            val initRequest = Request.Builder()
                .url(initUrl)
                .addHeader("Authorization", "Bearer $token")
                .addHeader("X-Upload-Content-Type", mimeType)
                .addHeader("X-Upload-Content-Length", fileSize.toString())
                .patch(metadataJson.toString().toRequestBody("application/json; charset=UTF-8".toMediaType()))
                .build()

            val initResponse = okHttpClient.newCall(initRequest).execute()
            if (!initResponse.isSuccessful) {
                val err = initResponse.body?.string().orEmpty()
                return@withContext Result.failure(parseError(initResponse.code, err))
            }

            val sessionUrl = initResponse.header("Location")
                ?: return@withContext Result.failure(IllegalStateException("No resumable session URL returned for patch."))

            val fileRequestBody = file.asRequestBody(mimeType.toMediaType())
            val uploadRequest = Request.Builder()
                .url(sessionUrl)
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Content-Length", fileSize.toString())
                .put(fileRequestBody)
                .build()

            val uploadResponse = okHttpClient.newCall(uploadRequest).execute()
            val bodyString = uploadResponse.body?.string().orEmpty()

            if (!uploadResponse.isSuccessful) {
                return@withContext Result.failure(parseError(uploadResponse.code, bodyString))
            }

            val json = JSONObject(bodyString)
            val updatedId = json.optString("id", driveFileId)
            val md5 = json.optString("md5Checksum", null)

            Log.i(tag, "Successfully patched document on Drive (ID: $updatedId)")
            Result.success(
                DriveUploadResult(
                    driveFileId = updatedId,
                    fileName = fileName,
                    md5Checksum = md5,
                    size = fileSize
                )
            )
        } catch (e: IOException) {
            Log.e(tag, "Network error updating document $driveFileId", e)
            Result.failure(e)
        } catch (e: Exception) {
            Log.e(tag, "Error updating document $driveFileId", e)
            Result.failure(e)
        }
    }

    /**
     * Downloads a document file from Google Drive directly into a local file.
     * Streams to temporary file first, verifies integrity, then atomically renames.
     */
    suspend fun downloadDocument(
        driveFileId: String,
        destinationFile: File
    ): Result<File> = withContext(Dispatchers.IO) {
        val token = authManager.getAccessToken().getOrElse {
            return@withContext Result.failure(it)
        }

        val parentDir = destinationFile.parentFile
        if (parentDir != null && !parentDir.exists()) {
            parentDir.mkdirs()
        }

        val tempFile = File(destinationFile.parentFile, "${destinationFile.name}.download_${System.currentTimeMillis()}.tmp")

        try {
            val downloadUrl = "https://www.googleapis.com/drive/v3/files/$driveFileId?alt=media"
            val request = Request.Builder()
                .url(downloadUrl)
                .addHeader("Authorization", "Bearer $token")
                .get()
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                val err = response.body?.string().orEmpty()
                return@withContext Result.failure(parseError(response.code, err))
            }

            val responseBody = response.body
                ?: return@withContext Result.failure(IllegalStateException("Empty response body from Google Drive."))

            // Stream response body directly to tempFile
            responseBody.byteStream().use { input ->
                FileOutputStream(tempFile).use { output ->
                    val buffer = ByteArray(16384)
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                    }
                    output.flush()
                }
            }

            if (tempFile.length() == 0L) {
                tempFile.delete()
                return@withContext Result.failure(IllegalStateException("Downloaded file is empty (0 bytes)."))
            }

            // Atomically replace destinationFile
            if (destinationFile.exists()) {
                destinationFile.delete()
            }
            if (!tempFile.renameTo(destinationFile)) {
                // Fallback copy if renameTo fails across file systems
                tempFile.copyTo(destinationFile, overwrite = true)
                tempFile.delete()
            }

            Log.i(tag, "Successfully downloaded document to ${destinationFile.absolutePath} (${destinationFile.length()} bytes)")
            Result.success(destinationFile)
        } catch (e: IOException) {
            tempFile.delete()
            Log.e(tag, "Network error downloading document $driveFileId", e)
            Result.failure(e)
        } catch (e: Exception) {
            tempFile.delete()
            Log.e(tag, "Download failed for $driveFileId", e)
            Result.failure(e)
        }
    }

    /**
     * Finds a file in NOTES/Documents by target name (e.g. "${docId}_${fileName}").
     */
    suspend fun findDocumentByTargetName(
        folderId: String,
        targetFileName: String
    ): Result<DriveFileInfo?> = withContext(Dispatchers.IO) {
        val token = authManager.getAccessToken().getOrElse {
            return@withContext Result.failure(it)
        }

        try {
            val safeName = targetFileName.replace("'", "\\'")
            val query = "'$folderId' in parents and name = '$safeName' and trashed = false"
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val listUrl = "https://www.googleapis.com/drive/v3/files?q=$encodedQuery&fields=files(id,name,mimeType,size,modifiedTime,md5Checksum)&pageSize=5"

            val request = Request.Builder()
                .url(listUrl)
                .addHeader("Authorization", "Bearer $token")
                .get()
                .build()

            val response = okHttpClient.newCall(request).execute()
            val body = response.body?.string().orEmpty()

            if (!response.isSuccessful) {
                return@withContext Result.failure(parseError(response.code, body))
            }

            val json = JSONObject(body)
            val files = json.optJSONArray("files")
            if (files != null && files.length() > 0) {
                val fileObj = files.getJSONObject(0)
                val info = DriveFileInfo(
                    id = fileObj.getString("id"),
                    name = fileObj.optString("name", targetFileName),
                    mimeType = fileObj.optString("mimeType", "application/pdf"),
                    size = fileObj.optLong("size", 0L),
                    modifiedTime = fileObj.optString("modifiedTime"),
                    md5Checksum = fileObj.optString("md5Checksum")
                )
                Result.success(info)
            } else {
                Result.success(null)
            }
        } catch (e: Exception) {
            Log.e(tag, "Error searching document '$targetFileName' in Drive", e)
            Result.failure(e)
        }
    }

    /**
     * Checks if a document exists and is not trashed in Google Drive.
     */
    suspend fun verifyFileExistence(driveFileId: String): Result<Boolean> = withContext(Dispatchers.IO) {
        val token = authManager.getAccessToken().getOrElse {
            return@withContext Result.failure(it)
        }

        try {
            val url = "https://www.googleapis.com/drive/v3/files/$driveFileId?fields=id,trashed"
            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .get()
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (response.code == 404) {
                return@withContext Result.success(false)
            }
            if (response.isSuccessful) {
                val body = response.body?.string().orEmpty()
                val json = JSONObject(body)
                val trashed = json.optBoolean("trashed", false)
                Result.success(!trashed)
            } else {
                Result.success(false)
            }
        } catch (e: Exception) {
            Log.e(tag, "Error verifying file existence for $driveFileId", e)
            Result.failure(e)
        }
    }

    /**
     * Deletes a document from Google Drive.
     */
    suspend fun deleteDocument(driveFileId: String): Result<Unit> = withContext(Dispatchers.IO) {
        driveService.deleteFile(driveFileId)
    }

    /**
     * Retrieves current Drive quota information.
     */
    suspend fun getStorageQuota(): Result<DriveStorageInfo> = withContext(Dispatchers.IO) {
        driveService.getStorageQuota()
    }

    private fun parseError(statusCode: Int, responseBody: String): Exception {
        return try {
            val json = JSONObject(responseBody)
            val errorObj = json.optJSONObject("error")
            val message = errorObj?.optString("message") ?: "Drive API error (Status $statusCode)"
            when (statusCode) {
                401 -> Exception("Google Drive authorization expired. Please reconnect in Settings.")
                403 -> {
                    if (message.contains("quota", ignoreCase = true)) {
                        DriveQuotaExceededException("Google Drive storage quota exceeded.")
                    } else if (message.contains("rateLimit", ignoreCase = true)) {
                        Exception("Google Drive API rate limit reached. Please retry in a few moments.")
                    } else {
                        Exception("Google Drive permission denied. Please verify app permissions.")
                    }
                }
                404 -> Exception("File not found in Google Drive.")
                else -> Exception(message)
            }
        } catch (_: Exception) {
            Exception("Drive API communication failed with status code: $statusCode")
        }
    }

    private fun formatBytes(bytes: Long): String {
        return if (bytes < 1024L * 1024L) {
            "${bytes / 1024} KB"
        } else {
            String.format(java.util.Locale.US, "%.1f MB", bytes.toDouble() / (1024 * 1024))
        }
    }

    /**
     * Clears in-memory cached Documents folder ID on account disconnect or sign-out.
     */
    fun clearCache() {
        cachedDocumentsFolderId = null
        driveService.clearCache()
    }
}

class DriveQuotaExceededException(message: String) : Exception(message)
