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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class DocumentRepository(private val documentDao: DocumentDao) {

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
     * Scans the device in real-time for all PDF documents and indexes them directly
     * without making separate copies or clones.
     */
    suspend fun scanDevicePdfDocuments(context: Context): Result<Int> = withContext(Dispatchers.IO) {
        try {
            var scannedCount = 0
            val existingDocs = documentDao.getAllDocumentsDirect()
            val existingPaths = existingDocs.map { it.localPath }.toSet()

            // 1. Scan via MediaStore safely
            try {
                val projection = arrayOf(
                    MediaStore.Files.FileColumns._ID,
                    MediaStore.Files.FileColumns.DISPLAY_NAME,
                    MediaStore.Files.FileColumns.SIZE,
                    MediaStore.Files.FileColumns.DATE_MODIFIED
                )

                val selection = "${MediaStore.Files.FileColumns.MIME_TYPE} = ? OR ${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE '%.pdf'"
                val selectionArgs = arrayOf("application/pdf")
                val sortOrder = "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC"

                val queryUri = MediaStore.Files.getContentUri("external")
                val cursor = context.contentResolver.query(queryUri, projection, selection, selectionArgs, sortOrder)

                cursor?.use {
                    val idCol = it.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                    val nameCol = it.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
                    val sizeCol = it.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
                    val dateCol = it.getColumnIndex(MediaStore.Files.FileColumns.DATE_MODIFIED)

                    while (it.moveToNext()) {
                        try {
                            val mediaId = it.getLong(idCol)
                            val rawName = it.getString(nameCol) ?: "document_${mediaId}.pdf"
                            val fileSize = it.getLong(sizeCol)
                            val dateModified = if (dateCol != -1) it.getLong(dateCol) * 1000L else System.currentTimeMillis()
                            val targetPath = ContentUris.withAppendedId(queryUri, mediaId).toString()

                            if (!existingPaths.contains(targetPath)) {
                                var pageCount = 1
                                try {
                                    context.contentResolver.openFileDescriptor(Uri.parse(targetPath), "r")?.use { pfd ->
                                        PdfRenderer(pfd).use { r -> pageCount = r.pageCount }
                                    }
                                } catch (_: Exception) { }

                                val accentColor = accentColors[(targetPath.hashCode() and 0x7FFFFFFF) % accentColors.size]
                                val displayName = rawName.removeSuffix(".pdf").replace("_", " ")

                                val docEntity = DocumentEntity(
                                    id = "pdf_${mediaId}_${UUID.randomUUID().toString().take(4)}",
                                    fileName = rawName,
                                    displayName = displayName,
                                    localPath = targetPath,
                                    fileSize = fileSize,
                                    mimeType = "application/pdf",
                                    pageCount = pageCount.coerceAtLeast(1),
                                    createdAt = dateModified,
                                    updatedAt = dateModified,
                                    lastOpenedAt = dateModified,
                                    lastOpenedPage = 0,
                                    isFavorite = false,
                                    isDeleted = false,
                                    syncStatus = "SYNCED",
                                    remoteStorageRef = null,
                                    accentColorHex = accentColor
                                )
                                documentDao.insertDocument(docEntity)
                                scannedCount++
                            }
                        } catch (itemErr: Exception) {
                            Log.w(tag, "Skipping unreadable MediaStore item", itemErr)
                        }
                    }
                }
            } catch (mediaStoreErr: Exception) {
                Log.w(tag, "MediaStore query error", mediaStoreErr)
            }

            // 2. Scan standard storage Download & Documents directories directly
            val publicDirs = listOf(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
            )

            for (dir in publicDirs) {
                if (dir != null && dir.exists() && dir.isDirectory) {
                    val pdfFiles = dir.listFiles { file -> file.isFile && file.name.endsWith(".pdf", ignoreCase = true) }
                    pdfFiles?.forEach { file ->
                        val path = file.absolutePath
                        if (!existingPaths.contains(path)) {
                            var pageCount = 1
                            try {
                                ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)?.use { pfd ->
                                    PdfRenderer(pfd).use { r -> pageCount = r.pageCount }
                                }
                            } catch (_: Exception) {}

                            val accentColor = accentColors[(path.hashCode() and 0x7FFFFFFF) % accentColors.size]
                            val displayName = file.name.removeSuffix(".pdf").replace("_", " ")

                            val docEntity = DocumentEntity(
                                id = "pdf_file_${UUID.randomUUID().toString().take(8)}",
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
                                syncStatus = "SYNCED",
                                remoteStorageRef = null,
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

    suspend fun importPdf(uri: Uri, context: Context): Result<Document> = withContext(Dispatchers.IO) {
        try {
            var fileName = "document_${System.currentTimeMillis()}.pdf"
            var fileSize = 0L

            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (cursor.moveToFirst()) {
                    if (nameIndex != -1) {
                        cursor.getString(nameIndex)?.let { fileName = it }
                    }
                    if (sizeIndex != -1) {
                        fileSize = cursor.getLong(sizeIndex)
                    }
                }
            }

            val docId = "doc_${UUID.randomUUID().toString().take(8)}"
            val path = uri.toString()

            var pageCount = 1
            try {
                context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                    PdfRenderer(pfd).use { renderer ->
                        pageCount = renderer.pageCount
                    }
                }
            } catch (_: Exception) {
            }

            val accentColor = accentColors[(path.hashCode() and 0x7FFFFFFF) % accentColors.size]
            val displayName = fileName.removeSuffix(".pdf").replace("_", " ")

            val entity = DocumentEntity(
                id = docId,
                fileName = fileName,
                displayName = displayName,
                localPath = path,
                fileSize = fileSize,
                mimeType = "application/pdf",
                pageCount = pageCount,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
                lastOpenedAt = System.currentTimeMillis(),
                lastOpenedPage = 0,
                isFavorite = false,
                isDeleted = false,
                syncStatus = "SYNCED",
                remoteStorageRef = null,
                accentColorHex = accentColor
            )

            documentDao.insertDocument(entity)
            Result.success(DocumentMappers.toDomain(entity))
        } catch (e: Exception) {
            Result.failure(e)
        }
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
        documentDao.softDeleteDocument(id, System.currentTimeMillis())
    }

    suspend fun restoreDocument(id: String) {
        documentDao.restoreDocument(id, System.currentTimeMillis())
    }

    /**
     * Permanently deletes document from database AND actually deletes the physical file
     * from the device storage.
     */
    suspend fun permanentlyDeleteDocument(id: String, context: Context? = null) = withContext(Dispatchers.IO) {
        val doc = documentDao.getDocumentByIdDirect(id)
        if (doc != null) {
            try {
                if (doc.localPath.startsWith("content://") && context != null) {
                    context.contentResolver.delete(Uri.parse(doc.localPath), null, null)
                } else {
                    val file = File(doc.localPath)
                    if (file.exists()) {
                        file.delete()
                    }
                }
            } catch (e: Exception) {
                Log.w(tag, "Could not delete physical file for ${doc.localPath}: ${e.message}")
            }
            documentDao.permanentlyDeleteDocument(id)
        }
    }
}
