package com.example.data.repository

import android.content.Context
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.util.Log
import androidx.documentfile.provider.DocumentFile
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

    fun getActiveDocuments(userId: String): Flow<List<Document>> =
        documentDao.getActiveDocuments(userId).map { list ->
            list.map { DocumentMappers.toDomain(it) }
        }

    fun getFavoriteDocuments(userId: String): Flow<List<Document>> =
        documentDao.getFavoriteDocuments(userId).map { list ->
            list.map { DocumentMappers.toDomain(it) }
        }

    fun getRecentDocuments(userId: String, limit: Int = 10): Flow<List<Document>> =
        documentDao.getRecentDocuments(userId, limit).map { list ->
            list.map { DocumentMappers.toDomain(it) }
        }

    fun getTrashDocuments(userId: String): Flow<List<Document>> =
        documentDao.getTrashDocuments(userId).map { list ->
            list.map { DocumentMappers.toDomain(it) }
        }

    fun getActiveDocumentsCount(userId: String): Flow<Int> =
        documentDao.getActiveDocumentsCount(userId)

    fun getTotalDocumentsSize(userId: String): Flow<Long> =
        documentDao.getTotalDocumentsSize(userId).map { it ?: 0L }

    fun searchDocuments(userId: String, query: String): Flow<List<Document>> {
        val trimmed = query.trim()
        return if (trimmed.isEmpty()) {
            getActiveDocuments(userId)
        } else {
            documentDao.searchDocuments(userId, trimmed).map { list ->
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

    suspend fun getAllDocumentsDirect(userId: String): List<Document> {
        return documentDao.getAllDocumentsDirect(userId).map { DocumentMappers.toDomain(it) }
    }

    /**
     * Recursively scans a selected directory tree via Android Storage Access Framework (SAF)
     * using DocumentFile, copies each discovered PDF into app-private storage,
     * calculates SHA-256 for deduplication, generates thumbnails, and stages them for Drive sync.
     */
    suspend fun scanFolderViaTreeUri(
        context: Context,
        treeUri: Uri,
        userId: String
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val rootDoc = DocumentFile.fromTreeUri(context, treeUri)
                ?: return@withContext Result.failure(IllegalArgumentException("Could not open selected folder"))

            var importedCount = 0
            val queue = ArrayDeque<DocumentFile>()
            queue.add(rootDoc)

            val maxDepth = 6
            var currentDepth = 0

            while (queue.isNotEmpty() && currentDepth < maxDepth) {
                val levelSize = queue.size
                for (i in 0 until levelSize) {
                    val currentDir = queue.removeFirst()
                    val files = try {
                        currentDir.listFiles()
                    } catch (e: Exception) {
                        Log.w(tag, "Could not list files in directory: ${e.message}")
                        emptyArray()
                    }

                    for (file in files) {
                        if (file.isDirectory) {
                            queue.add(file)
                        } else if (isPdfFile(file)) {
                            val importResult = importFromDocumentFile(context, file, userId)
                            if (importResult.isSuccess) {
                                importedCount++
                            }
                        }
                    }
                }
                currentDepth++
            }

            Log.i(tag, "Tree scan completed for user '$userId'. Discovered & indexed $importedCount new PDF files.")
            Result.success(importedCount)
        } catch (e: Exception) {
            Log.e(tag, "Failed scanning folder tree via SAF", e)
            Result.failure(e)
        }
    }

    private fun isPdfFile(file: DocumentFile): Boolean {
        val name = file.name.orEmpty()
        val type = file.type.orEmpty()
        return type.equals("application/pdf", ignoreCase = true) ||
                name.endsWith(".pdf", ignoreCase = true)
    }

    private suspend fun importFromDocumentFile(
        context: Context,
        docFile: DocumentFile,
        userId: String
    ): Result<DocumentEntity> = withContext(Dispatchers.IO) {
        val rawName = docFile.name ?: "document_${System.currentTimeMillis()}.pdf"
        val safeName = SecurityUtils.sanitizeFileName(rawName)
        val docId = "doc_${UUID.randomUUID().toString().take(8)}"

        val destinationFile = SecurityUtils.getSafeDocumentFile(context, docId, safeName)
        val tempFile = File(destinationFile.parentFile, "${destinationFile.name}.scan_${System.currentTimeMillis()}.tmp")

        try {
            val digest = MessageDigest.getInstance("SHA-256")
            val inputStream = context.contentResolver.openInputStream(docFile.uri)
                ?: return@withContext Result.failure(IllegalStateException("Cannot open input stream for: ${docFile.uri}"))

            inputStream.use { input ->
                FileOutputStream(tempFile).use { output ->
                    val buffer = ByteArray(16384)
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        digest.update(buffer, 0, read)
                        output.write(buffer, 0, read)
                    }
                    output.flush()
                }
            }

            val fileSize = tempFile.length()
            if (fileSize == 0L) {
                tempFile.delete()
                return@withContext Result.failure(IllegalStateException("Discovered file is empty (0 bytes)"))
            }

            val contentHash = digest.digest().joinToString("") { "%02x".format(it) }

            // Deduplication check for this user
            val existing = documentDao.findDocumentByHashAndSize(userId, contentHash, fileSize)
            if (existing != null && !existing.isDeleted) {
                tempFile.delete()
                Log.d(tag, "Duplicate document skipped: ${existing.displayName} (${existing.id})")
                return@withContext Result.success(existing)
            }

            if (!tempFile.renameTo(destinationFile)) {
                tempFile.copyTo(destinationFile, overwrite = true)
                tempFile.delete()
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
            val displayName = safeName.removeSuffix(".pdf").replace("_", " ").trim().ifBlank { "Document" }
            val thumb = PdfThumbnailHelper.generateThumbnail(context, docId, destinationFile)

            val entity = DocumentEntity(
                id = docId,
                userId = userId,
                fileName = safeName,
                displayName = displayName,
                localPath = destinationFile.absolutePath,
                fileSize = fileSize,
                mimeType = "application/pdf",
                contentHash = contentHash,
                pageCount = pageCount.coerceAtLeast(1),
                createdAt = docFile.lastModified().takeIf { it > 0 } ?: System.currentTimeMillis(),
                updatedAt = docFile.lastModified().takeIf { it > 0 } ?: System.currentTimeMillis(),
                lastOpenedAt = System.currentTimeMillis(),
                lastOpenedPage = 0,
                lastSyncedAt = 0L,
                isFavorite = false,
                isDeleted = false,
                syncStatus = "PENDING",
                downloadState = "AVAILABLE_OFFLINE",
                uploadState = "PENDING_UPLOAD",
                thumbnailPath = thumb,
                accentColorHex = accentColor
            )

            documentDao.insertDocument(entity)
            Log.i(tag, "Imported device PDF: $displayName ($docId) to ${destinationFile.absolutePath}")
            Result.success(entity)
        } catch (e: Exception) {
            tempFile.delete()
            Log.e(tag, "Failed importing PDF file ${docFile.name}", e)
            Result.failure(e)
        }
    }

    /**
     * Imports a PDF from an Android system URI (OpenDocument contract) into app-private storage.
     */
    suspend fun importPdf(uri: Uri, context: Context, userId: String): Result<Document> = withContext(Dispatchers.IO) {
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

            // Deduplication check for this user
            val existingDoc = documentDao.findDocumentByHashAndSize(userId, contentHash, fileSize)
            if (existingDoc != null && !existingDoc.isDeleted) {
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
                userId = userId,
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

    suspend fun saveDocumentDirect(document: Document, userId: String = "") {
        documentDao.insertDocument(DocumentMappers.toEntity(document, userId))
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
