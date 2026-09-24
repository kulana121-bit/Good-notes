package com.example.util

import android.content.Context
import java.io.File

/**
 * Security utilities for file path sanitization, Zip Slip prevention,
 * and input validation to protect app-private storage.
 */
object SecurityUtils {

    private val SAFE_CHARACTERS_REGEX = Regex("[^a-zA-Z0-9._-]")
    private val SAFE_ID_REGEX = Regex("[^a-zA-Z0-9_-]")

    /**
     * Sanitizes a file name, removing path traversal sequences ("..", "/", "\\")
     * and characters outside alphanumeric, dot, underscore, and hyphen.
     */
    fun sanitizeFileName(rawName: String?, fallbackName: String = "document.pdf"): String {
        if (rawName.isNullOrBlank()) return fallbackName

        // Strip path traversal and directories
        val baseName = File(rawName).name

        // Replace non-whitelisted characters with underscore
        var cleaned = baseName.replace(SAFE_CHARACTERS_REGEX, "_")

        // Strip multiple consecutive dots to avoid hidden directory traversal
        while (cleaned.contains("..")) {
            cleaned = cleaned.replace("..", "_")
        }

        // Remove leading dots or underscores that might cause hidden files
        cleaned = cleaned.trimStart('.', '_')

        // Ensure length limit
        if (cleaned.length > 120) {
            val extension = cleaned.substringAfterLast(".", "")
            val nameWithoutExt = cleaned.substringBeforeLast(".")
            cleaned = if (extension.isNotBlank() && extension != cleaned) {
                "${nameWithoutExt.take(100)}.$extension"
            } else {
                cleaned.take(120)
            }
        }

        return cleaned.ifBlank { fallbackName }
    }

    /**
     * Sanitizes an entity or document identifier to ensure no directory traversal characters exist.
     */
    fun sanitizeId(rawId: String?): String {
        if (rawId.isNullOrBlank()) return ""
        return rawId.replace(SAFE_ID_REGEX, "")
    }

    /**
     * Verifies that the canonical path of [childFile] is strictly inside [parentDir].
     * Prevents Zip Slip and arbitrary path traversal attacks.
     */
    fun isPathContained(parentDir: File, childFile: File): Boolean {
        val parentCanonical = parentDir.canonicalPath + File.separator
        val childCanonical = childFile.canonicalPath
        return childCanonical.startsWith(parentCanonical) || childCanonical == parentDir.canonicalPath
    }

    /**
     * Returns a safely validated [File] inside the app's private "documents" directory.
     * Throws [SecurityException] if a path traversal attempt is detected.
     */
    fun getSafeDocumentFile(context: Context, docId: String, fileName: String): File {
        val docsDir = File(context.filesDir, "documents").apply { if (!exists()) mkdirs() }
        val safeDocId = sanitizeId(docId)
        val safeFileName = sanitizeFileName(fileName)

        val targetFile = File(docsDir, "${safeDocId}_$safeFileName")
        if (!isPathContained(docsDir, targetFile)) {
            throw SecurityException("Path traversal detected for file: $fileName")
        }
        return targetFile
    }
}
