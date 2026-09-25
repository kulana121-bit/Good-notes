package com.example.data.remote.drive

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class GoogleDriveService(
    private val context: Context,
    private val authManager: GoogleDriveAuthManager = GoogleDriveAuthManager(context)
) {

    private val tag = "GoogleDriveService"

    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=UTF-8".toMediaType()

    // In-memory cache for folder IDs to avoid redundant queries during the session
    @Volatile
    private var cachedFolderStructure: DriveFolderStructure? = null

    fun checkAuthorization(expectedEmail: String? = null): DriveAuthState = authManager.checkDriveAuthorization(expectedEmail)

    /**
     * Retrieves actual authenticated user's Google Drive storage information using Drive API v3.
     */
    suspend fun getStorageQuota(): Result<DriveStorageInfo> = withContext(Dispatchers.IO) {
        val token = authManager.getAccessToken().getOrElse {
            return@withContext Result.failure(it)
        }

        try {
            val url = "https://www.googleapis.com/drive/v3/about?fields=storageQuota,user"
            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .get()
                .build()

            val response = okHttpClient.newCall(request).execute()
            val bodyString = response.body?.string().orEmpty()

            if (!response.isSuccessful) {
                return@withContext Result.failure(parseError(response.code, bodyString))
            }

            val json = JSONObject(bodyString)
            val storageQuota = json.optJSONObject("storageQuota")
            val user = json.optJSONObject("user")

            val usageBytes = storageQuota?.optLong("usage", 0L) ?: 0L
            val limitStr = storageQuota?.optString("limit")
            val limitBytes = if (!limitStr.isNullOrBlank() && limitStr != "0") limitStr.toLongOrNull() else null

            val availableBytes = if (limitBytes != null) {
                (limitBytes - usageBytes).coerceAtLeast(0L)
            } else null

            val storageInfo = DriveStorageInfo(
                usageBytes = usageBytes,
                limitBytes = limitBytes,
                availableBytes = availableBytes,
                userDisplayName = user?.optString("displayName"),
                userEmail = user?.optString("emailAddress")
            )
            Result.success(storageInfo)
        } catch (e: IOException) {
            Log.e(tag, "Network error fetching Drive storage quota")
            Result.failure(Exception("Network unavailable. Could not fetch Google Drive storage information."))
        } catch (e: Exception) {
            Log.e(tag, "Unexpected error fetching storage quota: ${e.javaClass.simpleName}")
            Result.failure(e)
        }
    }

    /**
     * Searches for a folder with the given name under parentFolderId.
     * If not found, creates the folder under parentFolderId.
     * Prevents creating duplicate folders.
     */
    suspend fun findOrCreateFolder(name: String, parentFolderId: String = "root"): Result<String> = withContext(Dispatchers.IO) {
        val token = authManager.getAccessToken().getOrElse {
            return@withContext Result.failure(it)
        }

        try {
            // Step 1: Query existing folder
            val safeName = name.replace("'", "\\'")
            val query = "'$parentFolderId' in parents and name = '$safeName' and mimeType = 'application/vnd.google-apps.folder' and trashed = false"
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val listUrl = "https://www.googleapis.com/drive/v3/files?q=$encodedQuery&fields=files(id,name,mimeType,parents,trashed)&pageSize=10"

            val searchRequest = Request.Builder()
                .url(listUrl)
                .addHeader("Authorization", "Bearer $token")
                .get()
                .build()

            val searchResponse = okHttpClient.newCall(searchRequest).execute()
            val searchBody = searchResponse.body?.string().orEmpty()

            if (!searchResponse.isSuccessful) {
                return@withContext Result.failure(parseError(searchResponse.code, searchBody))
            }

            val searchJson = JSONObject(searchBody)
            val filesArray = searchJson.optJSONArray("files")
            if (filesArray != null && filesArray.length() > 0) {
                val existingFolder = filesArray.getJSONObject(0)
                val existingId = existingFolder.optString("id")
                if (existingId.isNotBlank()) {
                    Log.d(tag, "Found existing folder '$name' with ID: $existingId")
                    return@withContext Result.success(existingId)
                }
            }

            // Step 2: Folder not found, create new folder
            val createUrl = "https://www.googleapis.com/drive/v3/files"
            val metadataJson = JSONObject().apply {
                put("name", name)
                put("mimeType", "application/vnd.google-apps.folder")
                put("parents", JSONArray().put(parentFolderId))
            }

            val createRequest = Request.Builder()
                .url(createUrl)
                .addHeader("Authorization", "Bearer $token")
                .post(metadataJson.toString().toRequestBody(jsonMediaType))
                .build()

            val createResponse = okHttpClient.newCall(createRequest).execute()
            val createBody = createResponse.body?.string().orEmpty()

            if (!createResponse.isSuccessful) {
                return@withContext Result.failure(parseError(createResponse.code, createBody))
            }

            val createdFolderId = JSONObject(createBody).optString("id")
            if (createdFolderId.isNotBlank()) {
                Log.i(tag, "Successfully created folder '$name' with ID: $createdFolderId")
                Result.success(createdFolderId)
            } else {
                Result.failure(Exception("Failed to obtain folder ID for '$name'."))
            }
        } catch (e: IOException) {
            Log.e(tag, "Network error finding/creating folder '$name'")
            Result.failure(Exception("Network unavailable. Could not verify or create folder in Google Drive."))
        } catch (e: Exception) {
            Log.e(tag, "Error finding/creating folder '$name': ${e.javaClass.simpleName}")
            Result.failure(e)
        }
    }

    /**
     * Resolves the standard NOTES directory hierarchy:
     * My Drive
     *   └── NOTES
     *         ├── Backup
     *         └── Documents
     */
    suspend fun getOrCreateNotesFolderStructure(forceRefresh: Boolean = false): Result<DriveFolderStructure> = withContext(Dispatchers.IO) {
        if (!forceRefresh) {
            cachedFolderStructure?.let { return@withContext Result.success(it) }
        }

        val rootFolderResult = findOrCreateFolder("NOTES", "root")
        val rootId = rootFolderResult.getOrElse { return@withContext Result.failure(it) }

        val backupResult = findOrCreateFolder("Backup", rootId)
        val backupId = backupResult.getOrElse { return@withContext Result.failure(it) }

        val documentsResult = findOrCreateFolder("Documents", rootId)
        val documentsId = documentsResult.getOrElse { return@withContext Result.failure(it) }

        val structure = DriveFolderStructure(
            rootFolderId = rootId,
            backupFolderId = backupId,
            documentsFolderId = documentsId
        )
        cachedFolderStructure = structure
        Result.success(structure)
    }

    /**
     * Uploads file content to the specified parent folder using Drive v3 multipart upload.
     */
    suspend fun uploadFile(
        parentFolderId: String,
        fileName: String,
        mimeType: String,
        content: ByteArray
    ): Result<String> = withContext(Dispatchers.IO) {
        val token = authManager.getAccessToken().getOrElse {
            return@withContext Result.failure(it)
        }

        try {
            val metadataJson = JSONObject().apply {
                put("name", fileName)
                put("parents", JSONArray().put(parentFolderId))
            }

            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                    "metadata",
                    null,
                    metadataJson.toString().toRequestBody(jsonMediaType)
                )
                .addFormDataPart(
                    "file",
                    fileName,
                    content.toRequestBody(mimeType.toMediaType())
                )
                .build()

            val uploadUrl = "https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart"
            val request = Request.Builder()
                .url(uploadUrl)
                .addHeader("Authorization", "Bearer $token")
                .post(requestBody)
                .build()

            val response = okHttpClient.newCall(request).execute()
            val bodyString = response.body?.string().orEmpty()

            if (!response.isSuccessful) {
                return@withContext Result.failure(parseError(response.code, bodyString))
            }

            val fileId = JSONObject(bodyString).optString("id")
            if (fileId.isNotBlank()) {
                Log.d(tag, "Uploaded file '$fileName' to Drive ID: $fileId")
                Result.success(fileId)
            } else {
                Result.failure(Exception("Drive upload response did not include a valid file ID."))
            }
        } catch (e: IOException) {
            Log.e(tag, "Network error uploading file '$fileName' to Google Drive")
            Result.failure(Exception("Network unavailable while uploading to Google Drive."))
        } catch (e: Exception) {
            Log.e(tag, "Upload error for file '$fileName': ${e.javaClass.simpleName}")
            Result.failure(e)
        }
    }

    /**
     * Searches for a file by name inside a specific parent folder.
     */
    suspend fun findFile(parentFolderId: String, fileName: String): Result<DriveFileInfo?> = withContext(Dispatchers.IO) {
        val token = authManager.getAccessToken().getOrElse {
            return@withContext Result.failure(it)
        }

        try {
            val safeName = fileName.replace("'", "\\'")
            val query = "'$parentFolderId' in parents and name = '$safeName' and trashed = false"
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
                    name = fileObj.optString("name", fileName),
                    mimeType = fileObj.optString("mimeType", "application/octet-stream"),
                    size = fileObj.optLong("size", 0L),
                    modifiedTime = fileObj.optString("modifiedTime"),
                    md5Checksum = fileObj.optString("md5Checksum")
                )
                Result.success(info)
            } else {
                Result.success(null)
            }
        } catch (e: Exception) {
            Log.e(tag, "Error searching file '$fileName' in Drive", e)
            Result.failure(e)
        }
    }

    /**
     * Lists all files in a parent folder.
     */
    suspend fun listFiles(parentFolderId: String): Result<List<DriveFileInfo>> = withContext(Dispatchers.IO) {
        val token = authManager.getAccessToken().getOrElse {
            return@withContext Result.failure(it)
        }

        try {
            val query = "'$parentFolderId' in parents and trashed = false"
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val listUrl = "https://www.googleapis.com/drive/v3/files?q=$encodedQuery&fields=files(id,name,mimeType,size,modifiedTime,md5Checksum)&pageSize=100"

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
            val list = mutableListOf<DriveFileInfo>()
            if (files != null) {
                for (i in 0 until files.length()) {
                    val fileObj = files.getJSONObject(i)
                    list.add(
                        DriveFileInfo(
                            id = fileObj.getString("id"),
                            name = fileObj.optString("name"),
                            mimeType = fileObj.optString("mimeType"),
                            size = fileObj.optLong("size", 0L),
                            modifiedTime = fileObj.optString("modifiedTime"),
                            md5Checksum = fileObj.optString("md5Checksum")
                        )
                    )
                }
            }
            Result.success(list)
        } catch (e: Exception) {
            Log.e(tag, "Error listing files in Drive folder '$parentFolderId'", e)
            Result.failure(e)
        }
    }

    /**
     * Updates an existing file's media content in Google Drive.
     */
    suspend fun updateFile(fileId: String, mimeType: String, content: ByteArray): Result<String> = withContext(Dispatchers.IO) {
        val token = authManager.getAccessToken().getOrElse {
            return@withContext Result.failure(it)
        }

        try {
            val uploadUrl = "https://www.googleapis.com/upload/drive/v3/files/$fileId?uploadType=media"
            val request = Request.Builder()
                .url(uploadUrl)
                .addHeader("Authorization", "Bearer $token")
                .patch(content.toRequestBody(mimeType.toMediaType()))
                .build()

            val response = okHttpClient.newCall(request).execute()
            val bodyString = response.body?.string().orEmpty()

            if (!response.isSuccessful) {
                return@withContext Result.failure(parseError(response.code, bodyString))
            }

            val updatedId = JSONObject(bodyString).optString("id", fileId)
            Log.d(tag, "Successfully updated file content for Drive ID: $updatedId")
            Result.success(updatedId)
        } catch (e: Exception) {
            Log.e(tag, "Error updating file '$fileId'", e)
            Result.failure(e)
        }
    }

    /**
     * Uploads or updates a file (prevents duplicate files!).
     */
    suspend fun uploadOrUpdateFile(
        parentFolderId: String,
        fileName: String,
        mimeType: String,
        content: ByteArray
    ): Result<String> = withContext(Dispatchers.IO) {
        val existing = findFile(parentFolderId, fileName).getOrNull()
        if (existing != null) {
            updateFile(existing.id, mimeType, content)
        } else {
            uploadFile(parentFolderId, fileName, mimeType, content)
        }
    }

    /**
     * Downloads file content from Drive by file ID.
     */
    suspend fun downloadFile(fileId: String): Result<ByteArray> = withContext(Dispatchers.IO) {
        val token = authManager.getAccessToken().getOrElse {
            return@withContext Result.failure(it)
        }

        try {
            val downloadUrl = "https://www.googleapis.com/drive/v3/files/$fileId?alt=media"
            val request = Request.Builder()
                .url(downloadUrl)
                .addHeader("Authorization", "Bearer $token")
                .get()
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                val errorBody = response.body?.string().orEmpty()
                return@withContext Result.failure(parseError(response.code, errorBody))
            }

            val bytes = response.body?.bytes()
            if (bytes != null) {
                Result.success(bytes)
            } else {
                Result.failure(Exception("Empty response body received from Google Drive."))
            }
        } catch (e: IOException) {
            Log.e(tag, "Network error downloading file '$fileId'")
            Result.failure(Exception("Network unavailable while downloading from Google Drive."))
        } catch (e: Exception) {
            Log.e(tag, "Download error for file '$fileId': ${e.javaClass.simpleName}")
            Result.failure(e)
        }
    }

    /**
     * Deletes a file or folder from Google Drive by ID.
     */
    suspend fun deleteFile(fileId: String): Result<Unit> = withContext(Dispatchers.IO) {
        val token = authManager.getAccessToken().getOrElse {
            return@withContext Result.failure(it)
        }

        try {
            val deleteUrl = "https://www.googleapis.com/drive/v3/files/$fileId"
            val request = Request.Builder()
                .url(deleteUrl)
                .addHeader("Authorization", "Bearer $token")
                .delete()
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (response.isSuccessful || response.code == 404) {
                Log.d(tag, "Successfully deleted file '$fileId'")
                Result.success(Unit)
            } else {
                val errorBody = response.body?.string().orEmpty()
                Result.failure(parseError(response.code, errorBody))
            }
        } catch (e: IOException) {
            Log.e(tag, "Network error deleting file '$fileId'")
            Result.failure(Exception("Network unavailable while deleting from Google Drive."))
        } catch (e: Exception) {
            Log.e(tag, "Delete error for file '$fileId': ${e.javaClass.simpleName}")
            Result.failure(e)
        }
    }

    /**
     * Executes the Phase 7 internal sanity integration test flow:
     * 1. Check/verify connection
     * 2. Read storage quota
     * 3. Find/create NOTES folder
     * 4. Find/create Backup folder
     * 5. Find/create Documents folder
     * 6. Upload temporary test file to NOTES/Backup/
     * 7. Download and verify test file content
     * 8. Delete temporary test file
     * 9. Produce comprehensive test report
     */
    suspend fun testDriveIntegration(): Result<DriveTestReport> = withContext(Dispatchers.IO) {
        try {
            // 1. Quota Check
            val storageInfo = getStorageQuota().getOrNull()

            // 2. Folder Hierarchy
            val foldersResult = getOrCreateNotesFolderStructure()
            val folders = foldersResult.getOrElse {
                return@withContext Result.success(
                    DriveTestReport(
                        success = false,
                        storageInfo = storageInfo,
                        message = "Could not initialize NOTES folder structure: ${it.localizedMessage}"
                    )
                )
            }

            // 3. Upload Test File
            val testFileName = "__drive_connectivity_test_${System.currentTimeMillis()}.txt"
            val testPayload = "NOTES Google Drive integration test - timestamp: ${System.currentTimeMillis()}".toByteArray(Charsets.UTF_8)

            val uploadResult = uploadFile(
                parentFolderId = folders.backupFolderId,
                fileName = testFileName,
                mimeType = "text/plain",
                content = testPayload
            )
            val fileId = uploadResult.getOrElse {
                return@withContext Result.success(
                    DriveTestReport(
                        success = false,
                        rootFolderId = folders.rootFolderId,
                        backupFolderId = folders.backupFolderId,
                        documentsFolderId = folders.documentsFolderId,
                        storageInfo = storageInfo,
                        message = "Test file upload failed: ${it.localizedMessage}"
                    )
                )
            }

            // 4. Download and Verify
            val downloadResult = downloadFile(fileId)
            val downloadedBytes = downloadResult.getOrNull()
            val downloadSuccess = downloadedBytes != null && downloadedBytes.contentEquals(testPayload)

            // 5. Clean up temporary test file
            val deleteSuccess = deleteFile(fileId).isSuccess

            val report = DriveTestReport(
                success = downloadSuccess,
                rootFolderId = folders.rootFolderId,
                backupFolderId = folders.backupFolderId,
                documentsFolderId = folders.documentsFolderId,
                storageInfo = storageInfo,
                testFileUploaded = true,
                testFileDownloaded = downloadSuccess,
                testFileDeleted = deleteSuccess,
                message = if (downloadSuccess) {
                    "Google Drive connection test passed! Folders and test file read/write verified."
                } else {
                    "Test file was uploaded but downloaded content did not match expected bytes."
                }
            )
            Result.success(report)
        } catch (e: Exception) {
            Log.e(tag, "testDriveIntegration failed: ${e.javaClass.simpleName}", e)
            Result.failure(e)
        }
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
                        Exception("Google Drive storage quota exceeded.")
                    } else if (message.contains("rateLimit", ignoreCase = true)) {
                        Exception("Google Drive API rate limit reached. Please retry in a few moments.")
                    } else {
                        Exception("Google Drive permission denied. Please verify app permissions.")
                    }
                }
                404 -> Exception("File or folder not found in Google Drive.")
                else -> Exception(message)
            }
        } catch (_: Exception) {
            Exception("Drive API communication failed with status code: $statusCode")
        }
    }

    /**
     * Clears in-memory cached Drive folder structure on account disconnect or sign-out.
     */
    fun clearCache() {
        cachedFolderStructure = null
    }
}
