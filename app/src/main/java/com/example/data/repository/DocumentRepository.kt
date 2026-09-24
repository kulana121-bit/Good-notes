package com.example.data.repository

import android.content.ContentUris
import android.content.Context
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Log
import com.example.data.local.converters.DocumentMappers
import com.example.data.local.dao.DocumentDao
import com.example.data.local.entity.DocumentEntity
import com.example.data.model.Document
import com.example.data.remote.drive.GoogleDriveDocumentService
import com.example.data.util.PdfThumbnailHelper
import com.example.util.SecurityUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.UUID

class DocumentRepository(
    private val documentDao: DocumentDao,
    private val driveDocumentService: GoogleDriveDocumentService? = null
) {
    private val tag = "DocumentRepository"

    private val accentColors = listOf(
        0xFFFEEA9F, // NoteYellow
        0xFFEB7A53, // NoteCoral
        0xFF9887DB, // Editorial Lilac
        0xFFA8D672, // Mint Sage
        0xFF7CC4FA  // Sky Blue
    )

    val activeDocuments: Flow<List<Document>> = documentDao.getActiveDocuments().map { list ->
        list.map { DocumentMappers.toDomain(it) }
    }

    val favoriteDocuments: Flow<List<Document>> = documentDao.getFavoriteDocuments().map { list ->
        list.map { DocumentMappers.toDomain(it) }
    }

    val recentDocuments: Flow<List<Document>> = documentDao.getRecentDocuments(10).map { list ->
        list.map { DocumentMappers.toDomain(it) }
    }

    val trashDocuments: Flow<List<Document>> = documentDao.getTrashDocuments().map { list ->
        list.map { DocumentMappers.toDomain(it) }
    }

    val activeDocumentsCount: Flow<Int> = documentDao.getActiveDocumentsCount()

    val totalDocumentsSize: Flow<Long> = documentDao.getTotalDocumentsSize().map { it ?: 0L }

    fun searchDocuments(query: String): Flow<List<Document>> {
        val trimmed = query.trim()
        return if (trimmed.isEmpty()) {
            activeDocuments
        } else {
            documentDao.searchDocuments(trimmed).map { list ->
                list.map { DocumentMappers.toDomain(it) }
            }
        }
    }

    fun getDocumentById(id: String): Flow<Document?> {
        return documentDao.getDocumentByIdFlow(id).map { entity ->
            entity?.let { DocumentMappers.toDomain(it) }
        }
    }

    suspend fun getDocumentByIdDirect(id: String): Document? {
        return documentDao.getDocumentByIdDirect(id)?.let { DocumentMappers.toDomain(it) }
    }

    suspend fun getAllDocumentsDirect(): List<Document> {
        return documentDao.getAllDocumentsDirect().map { DocumentMappers.toDomain(it) }
    }

    /**
     * Scans the device in real-time for PDF documents and indexes them.
     */
    suspend fun scanDevicePdfDocuments(context: Context): Result<Int> = withContext(Dispatchers.IO) {
        try {
            var scannedCount = 0
            val existingDocs = documentDao.getAllDocumentsDirect()
            val existingPaths = existingDocs.map { it.localPath }.toSet()

            // 1. Scan via MediaStore
            try {
                val projection = arrayOf(
                    MediaStore.Files.FileColumns._ID,
                    MediaStore.Files.FileColumns.DISPLAY_NAME,
                    MediaStore.Files.FileColumns.SIZE,
                    MediaStore.Files.FileColumns.DATE_MODIFIED
                )

                val selection = "${MediaStore.Files.FileColumns.MIME_TYPE} = ? OR ${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE '%.pdf'"
                val selectionArgs = arrayOf("application/pdf")

                context.contentResolver.query(
                    MediaStore.Files.getContentUri("external"),
                    projection,
                    selection,
                    selectionArgs,
                    "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC"
                )?.use { cursor ->
                    val idCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                    val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
                    val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
                    val modCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED)

                    while (cursor.moveToNext()) {
                        val mediaId = cursor.getLong(idCol)
                        val fileName = cursor.getString(nameCol) ?: "document_$mediaId.pdf"
                        val size = cursor.getLong(sizeCol)
                        val modDate = cursor.getLong(modCol) * 1000L
                        val contentUri = ContentUris.withAppendedId(MediaStore.Files.getContentUri("external"), mediaId)
                        val uriString = contentUri.toString()

                        if (!existingPaths.contains(uriString) && size > 0) {
                            val docId = "doc_ms_$mediaId"
                            var pageCount = 1
                            try {
                                context.contentResolver.openFileDescriptor(contentUri, "r")?.use { pfd ->
                                    PdfRenderer(pfd).use { renderer ->
                                        pageCount = renderer.pageCount
                                    }
                                }
                            } catch (_: Exception) {}

                            val accentColor = accentColors[(uriString.hashCode() and 0x7FFFFFFF) % accentColors.size]
                            val displayName = fileName.removeSuffix(".pdf").replace("_", " ")

                            val thumb = PdfThumbnailHelper.generateThumbnailFromUri(context, docId, contentUri)

                            val docEntity = DocumentEntity(
                                id = docId,
                                fileName = fileName,
                                displayName = displayName,
                                localPath = uriString,
                                fileSize = size,
                                mimeType = "application/pdf",
                                pageCount = pageCount.coerceAtLeast(1),
                                createdAt = modDate,
                                updatedAt = modDate,
                                lastOpenedAt = modDate,
                                lastOpenedPage = 0,
                                isFavorite = false,
                                isDeleted = false,
                                syncStatus = "PENDING",
                                downloadState = "AVAILABLE_OFFLINE",
                                uploadState = "PENDING_UPLOAD",
                                thumbnailPath = thumb,
                                accentColorHex = accentColor
                            )
                            documentDao.insertDocument(docEntity)
                            scannedCount++
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(tag, "MediaStore PDF query warning: ${e.message}")
            }

            // 2. Scan standard directories
            val targetDirs = listOf(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
            )

            for (dir in targetDirs) {
                if (dir.exists() && dir.isDirectory) {
                    val files = dir.listFiles { f -> f.isFile && f.name.endsWith(".pdf", ignoreCase = true) } ?: emptyArray()
                    for (file in files) {
                        val path = file.absolutePath
                        if (!existingPaths.contains(path) && file.length() > 0) {
                            val docId = "doc_f_${path.hashCode() and 0x7FFFFFFF}"
                            var pageCount = 1
                            try {
                                ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                                    PdfRenderer(pfd).use { renderer ->
                                        pageCount = renderer.pageCount
                                    }
                                }
                            } catch (_: Exception) {}

                            val accentColor = accentColors[(path.hashCode() and 0x7FFFFFFF) % accentColors.size]
                            val displayName = file.name.removeSuffix(".pdf").replace("_", " ")
                            val thumb = PdfThumbnailHelper.generateThumbnail(context, docId, file)

                            val docEntity = DocumentEntity(
                                id = docId,
                                fileName = file.name,
                                displayName = displayName,
                                localPath = path,
                                fileSize = file.length(),
                                mimeType = "application/pdf",
                                pageCount = pageCount.coerceAtLeast(1),
                                createdAt = file.lastModified(),
                                updatedAt = file.lastModified(),
                                lastOpenedAt = file.lastModified(),
                                lastOpenedPage = 0,
                                isFavorite = false,
                                isDeleted = false,
                                syncStatus = "PENDING",
                                downloadState = "AVAILABLE_OFFLINE",
                                uploadState = "PENDING_UPLOAD",
                                thumbnailPath = thumb,
                                accentColorHex = accentColor
                            )
                            documentDao.insertDocument(docEntity)
                            scannedCount++
                        }
                    }
                }
            }

            Result.success(scannedCount)
        } catch (e: Exception) {
            Log.e(tag, "Failed to scan device PDFs", e)
            Result.failure(e)
        }
    }

    /**
     * Imports a PDF from an Android system URI into app-private storage.
     * Computes SHA-256 hash for deduplication, generates a lightweight thumbnail,
     * and sets uploadState = PENDING_UPLOAD so it synchronizes automatically to Drive.
     */
    suspend fun importPdf(uri: Uri, context: Context): Result<Document> = withContext(Dispatchers.IO) {
        try {
            var rawFileName = "document_${System.currentTimeMillis()}.pdf"
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (cursor.moveToFirst() && nameIndex != -1) {
                    cursor.getString(nameIndex)?.let { rawFileName = it }
                }
            }

            val safeFileName = SecurityUtils.sanitizeFileName(rawFileName)
            val docId = "doc_${UUID.randomUUID().toString().take(8)}"

            // Copy file to app-managed storage with traversal protection
            val destinationFile = SecurityUtils.getSafeDocumentFile(context, docId, safeFileName)

            val digest = MessageDigest.getInstance("SHA-256")
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destinationFile).use { output ->
                    val buffer = ByteArray(16384)
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        digest.update(buffer, 0, read)
                        output.write(buffer, 0, read)
                    }
                    output.flush()
                }
            } ?: return@withContext Result.failure(IllegalStateException("Could not open input stream for URI: $uri"))

            val fileSize = destinationFile.length()
            val contentHash = digest.digest().joinToString("") { "%02x".format(it) }

            // Deduplication check
            val existingDoc = documentDao.findDocumentByHashAndSize(contentHash, fileSize)
            if (existingDoc != null && !existingDoc.isDeleted) {
                // Same content already imported: clean up duplicate copy and return existing
                destinationFile.delete()
                Log.d(tag, "Identical document already exists: ${existingDoc.id}. Reusing existing entry.")
                return@withContext Result.success(DocumentMappers.toDomain(existingDoc))
            }

            var pageCount = 1
            try {
                ParcelFileDescriptor.open(destinationFile, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                    PdfRenderer(pfd).use { renderer ->
                        pageCount = renderer.pageCount
                    }
                }
            } catch (_: Exception) {}

            val accentColor = accentColors[(destinationFile.absolutePath.hashCode() and 0x7FFFFFFF) % accentColors.size]
            val displayName = safeFileName.removeSuffix(".pdf").replace("_", " ").trim()
            val thumbnailPath = PdfThumbnailHelper.generateThumbnail(context, docId, destinationFile)

            val entity = DocumentEntity(
                id = docId,
                fileName = safeFileName,
                displayName = displayName.ifBlank { "Document" },
                localPath = destinationFile.absolutePath,
                fileSize = fileSize,
                mimeType = "application/pdf",
                contentHash = contentHash,
                pageCount = pageCount.coerceAtLeast(1),
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
                lastOpenedAt = System.currentTimeMillis(),
                lastOpenedPage = 0,
                lastSyncedAt = 0L,
                isFavorite = false,
                isDeleted = false,
                syncStatus = "PENDING",
                downloadState = "AVAILABLE_OFFLINE",
                uploadState = "PENDING_UPLOAD",
                thumbnailPath = thumbnailPath,
                accentColorHex = accentColor
            )

            documentDao.insertDocument(entity)
            Log.i(tag, "Imported document $docId to ${destinationFile.absolutePath} ($fileSize bytes)")
            Result.success(DocumentMappers.toDomain(entity))
        } catch (e: Exception) {
            Log.e(tag, "Failed to import PDF document", e)
            Result.failure(e)
        }
    }

    /**
     * Downloads a cloud-only document from Google Drive into app-managed local storage.
     */
    suspend fun downloadDocumentFromDrive(documentId: String, context: Context): Result<Document> = withContext(Dispatchers.IO) {
        val entity = documentDao.getDocumentByIdDirect(documentId)
            ?: return@withContext Result.failure(IllegalArgumentException("Document not found: $documentId"))

        val driveFileId = entity.driveFileId
            ?: return@withContext Result.failure(IllegalStateException("Document has no Google Drive file ID."))

        val driveService = driveDocumentService ?: GoogleDriveDocumentService(context)

        // Mark downloading in UI
        documentDao.updateDownloadState(documentId, "DOWNLOADING", entity.localPath, entity.thumbnailPath)

        val targetFile = SecurityUtils.getSafeDocumentFile(context, entity.id, entity.fileName)

        val downloadResult = driveService.downloadDocument(driveFileId, targetFile)
        if (downloadResult.isFailure) {
            documentDao.updateDownloadState(documentId, "FAILED", entity.localPath, entity.thumbnailPath)
            return@withContext Result.failure(downloadResult.exceptionOrNull() ?: Exception("Drive download failed"))
        }

        val downloadedFile = downloadResult.getOrThrow()
        val thumb = PdfThumbnailHelper.generateThumbnail(context, documentId, downloadedFile)

        documentDao.updateDownloadState(
            id = documentId,
            downloadState = "AVAILABLE_OFFLINE",
            localPath = downloadedFile.absolutePath,
            thumbnailPath = thumb
        )

        val updated = documentDao.getDocumentByIdDirect(documentId)
        Result.success(DocumentMappers.toDomain(updated ?: entity))
    }

    suspend fun saveDocumentDirect(document: Document) {
        documentDao.insertDocument(DocumentMappers.toEntity(document))
    }

    suspend fun updateLastOpened(id: String, page: Int) {
        documentDao.updateLastOpened(id, System.currentTimeMillis(), page)
    }

    suspend fun toggleFavorite(id: String) {
        val doc = documentDao.getDocumentByIdDirect(id) ?: return
        documentDao.setFavorite(id, !doc.isFavorite, System.currentTimeMillis())
    }

    suspend fun softDeleteDocument(id: String) {
        documentDao.softDeleteDocument(id, deletedAt = System.currentTimeMillis(), updatedAt = System.currentTimeMillis())
    }

    suspend fun restoreDocument(id: String) {
        documentDao.restoreDocument(id, updatedAt = System.currentTimeMillis())
    }

    /**
     * Permanently deletes document from database, local storage, and Google Drive.
     */
    suspend fun permanentlyDeleteDocument(id: String, context: Context? = null) = withContext(Dispatchers.IO) {
        val doc = documentDao.getDocumentByIdDirect(id)
        if (doc != null) {
            // Delete from Google Drive if synced
            val driveId = doc.driveFileId
            if (driveId != null && context != null) {
                try {
                    val driveService = driveDocumentService ?: GoogleDriveDocumentService(context)
                    driveService.deleteDocument(driveId)
                } catch (e: Exception) {
                    Log.w(tag, "Drive deletion non-critical warning for $driveId: ${e.message}")
                }
            }

            // Delete physical local file
            try {
                if (doc.localPath.startsWith("content://") && context != null) {
                    context.contentResolver.delete(Uri.parse(doc.localPath), null, null)
                } else if (doc.localPath.isNotBlank()) {
                    val file = File(doc.localPath)
                    if (file.exists()) {
                        file.delete()
                    }
                }
                // Delete cached thumbnail if present
                doc.thumbnailPath?.let { File(it).delete() }
            } catch (e: Exception) {
                Log.w(tag, "Could not delete physical file for ${doc.localPath}: ${e.message}")
            }

            documentDao.permanentlyDeleteDocument(id)
        }
    }
}
