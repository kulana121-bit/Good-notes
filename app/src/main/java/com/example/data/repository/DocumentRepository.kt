package com.example.data.repository

import android.content.Context
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import androidx.compose.ui.graphics.Color
import com.example.data.local.dao.DocumentDao
import com.example.data.local.converters.DocumentMappers
import com.example.data.local.entity.DocumentEntity
import com.example.data.model.Document
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

class DocumentRepository(private val documentDao: DocumentDao) {

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

            val sanitizedFileName = fileName.replace("[^a-zA-Z0-9._-]".toRegex(), "_")
            val docsDir = File(context.filesDir, "documents").apply { mkdirs() }
            val docId = "doc_${UUID.randomUUID().toString().take(8)}"
            val destinationFile = File(docsDir, "${docId}_$sanitizedFileName")

            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destinationFile).use { output ->
                    input.copyTo(output)
                }
            } ?: return@withContext Result.failure(Exception("Unable to read selected PDF file"))

            val actualSize = if (destinationFile.exists()) destinationFile.length() else fileSize

            // Inspect page count via PdfRenderer safely
            var pageCount = 1
            try {
                ParcelFileDescriptor.open(destinationFile, ParcelFileDescriptor.MODE_READ_ONLY)?.use { pfd ->
                    PdfRenderer(pfd).use { renderer ->
                        pageCount = renderer.pageCount
                    }
                }
            } catch (_: Exception) {
                // Keep default 1 if password protected or rendering probe fails
            }

            val accentColor = accentColors[(destinationFile.name.hashCode() and 0x7FFFFFFF) % accentColors.size]
            val displayName = fileName.removeSuffix(".pdf").replace("_", " ")

            val entity = DocumentEntity(
                id = docId,
                fileName = fileName,
                displayName = displayName,
                localPath = destinationFile.absolutePath,
                fileSize = actualSize,
                mimeType = "application/pdf",
                pageCount = pageCount,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
                lastOpenedAt = System.currentTimeMillis(),
                lastOpenedPage = 0,
                isFavorite = false,
                isDeleted = false,
                syncStatus = "PENDING",
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

    suspend fun permanentlyDeleteDocument(id: String) = withContext(Dispatchers.IO) {
        val doc = documentDao.getDocumentByIdDirect(id)
        if (doc != null) {
            val file = File(doc.localPath)
            if (file.exists()) {
                file.delete()
            }
            documentDao.permanentlyDeleteDocument(id)
        }
    }
}
