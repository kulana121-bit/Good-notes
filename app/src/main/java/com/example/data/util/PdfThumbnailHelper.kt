package com.example.data.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object PdfThumbnailHelper {
    private const val TAG = "PdfThumbnailHelper"
    private const val MAX_THUMBNAIL_WIDTH = 280

    suspend fun generateThumbnail(context: Context, docId: String, file: File): String? = withContext(Dispatchers.IO) {
        if (!file.exists() || !file.canRead() || file.length() == 0L) {
            return@withContext null
        }

        var pfd: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null
        var page: PdfRenderer.Page? = null
        var bitmap: Bitmap? = null

        try {
            pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            renderer = PdfRenderer(pfd)
            if (renderer.pageCount == 0) return@withContext null

            page = renderer.openPage(0)

            val width = page.width
            val height = page.height
            val scale = (MAX_THUMBNAIL_WIDTH.toFloat() / width.toFloat()).coerceAtMost(1.0f)
            val targetWidth = (width * scale).toInt().coerceAtLeast(100)
            val targetHeight = (height * scale).toInt().coerceAtLeast(140)

            bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
            // Draw clean white background first so transparent PDFs render cleanly
            bitmap.eraseColor(Color.WHITE)

            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

            val thumbDir = File(context.cacheDir, "thumbnails").apply { if (!exists()) mkdirs() }
            val thumbFile = File(thumbDir, "${docId}.jpg")

            FileOutputStream(thumbFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
            }

            Log.d(TAG, "Generated thumbnail for $docId: ${thumbFile.absolutePath}")
            thumbFile.absolutePath
        } catch (e: Exception) {
            Log.w(TAG, "Thumbnail generation skipped for $docId: ${e.message}")
            null
        } finally {
            try { page?.close() } catch (_: Exception) {}
            try { renderer?.close() } catch (_: Exception) {}
            try { pfd?.close() } catch (_: Exception) {}
            try {
                if (bitmap != null && !bitmap.isRecycled) {
                    bitmap.recycle()
                }
            } catch (_: Exception) {}
        }
    }

    suspend fun generateThumbnailFromUri(context: Context, docId: String, uri: Uri): String? = withContext(Dispatchers.IO) {
        var pfd: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null
        var page: PdfRenderer.Page? = null
        var bitmap: Bitmap? = null

        try {
            pfd = context.contentResolver.openFileDescriptor(uri, "r") ?: return@withContext null
            renderer = PdfRenderer(pfd)
            if (renderer.pageCount == 0) return@withContext null

            page = renderer.openPage(0)

            val width = page.width
            val height = page.height
            val scale = (MAX_THUMBNAIL_WIDTH.toFloat() / width.toFloat()).coerceAtMost(1.0f)
            val targetWidth = (width * scale).toInt().coerceAtLeast(100)
            val targetHeight = (height * scale).toInt().coerceAtLeast(140)

            bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(Color.WHITE)

            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

            val thumbDir = File(context.cacheDir, "thumbnails").apply { if (!exists()) mkdirs() }
            val thumbFile = File(thumbDir, "${docId}.jpg")

            FileOutputStream(thumbFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
            }

            thumbFile.absolutePath
        } catch (e: Exception) {
            Log.w(TAG, "Thumbnail generation from URI skipped for $docId: ${e.message}")
            null
        } finally {
            try { page?.close() } catch (_: Exception) {}
            try { renderer?.close() } catch (_: Exception) {}
            try { pfd?.close() } catch (_: Exception) {}
            try {
                if (bitmap != null && !bitmap.isRecycled) {
                    bitmap.recycle()
                }
            } catch (_: Exception) {}
        }
    }
}
