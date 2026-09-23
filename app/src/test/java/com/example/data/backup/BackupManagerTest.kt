package com.example.data.backup

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.NotesDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
class BackupManagerTest {

    private lateinit var context: Context
    private lateinit var database: NotesDatabase
    private lateinit var backupManager: BackupManager
    private lateinit var tempFile: File

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(
            context,
            NotesDatabase::class.java
        ).allowMainThreadQueries().build()
        backupManager = BackupManager(database)
    }

    @After
    fun teardown() {
        database.close()
        if (::tempFile.isInitialized && tempFile.exists()) {
            tempFile.delete()
        }
    }

    @Test
    fun `restoreBackup throws SecurityException when zip slip is attempted`() = runBlocking {
        // Create a malicious zip file
        val byteOut = ByteArrayOutputStream()
        ZipOutputStream(byteOut).use { zos ->
            // Add backup.json
            zos.putNextEntry(ZipEntry("backup.json"))
            zos.write("""
                {
                    "formatVersion": 1,
                    "exportedAt": 123456789,
                    "appIdentifier": "NOTES_BACKUP",
                    "notes": [],
                    "folders": [],
                    "settings": [],
                    "documents": []
                }
            """.trimIndent().toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            // Add malicious entry trying to escape documents/ dir
            // Use an absolute path or enough ../ to escape
            zos.putNextEntry(ZipEntry("documents/../../../../../../malicious.txt"))
            zos.write("Malicious content".toByteArray())
            zos.closeEntry()
        }

        val maliciousZipBytes = byteOut.toByteArray()

        // Write it to a temporary file since Robolectric Context might not support ContentResolver streams properly
        // Actually, we can use a custom ContentProvider or just mock the file input stream by returning it from a test URI.
        // But since we can't easily mock `context.contentResolver` without Mockito/MockK, we can just write a file
        // and create an `android.net.Uri` from file path using `Uri.fromFile()`.

        tempFile = File(context.cacheDir, "malicious_backup.zip")
        FileOutputStream(tempFile).use { fos ->
            fos.write(maliciousZipBytes)
        }

        val uri = Uri.fromFile(tempFile)

        val result = backupManager.restoreBackup(context, uri)

        assertTrue(result.isFailure)
        val exception = result.exceptionOrNull()
        assertTrue(exception is SecurityException)
        assertTrue(exception?.message?.contains("Zip Slip vulnerability detected") == true)
    }
}
