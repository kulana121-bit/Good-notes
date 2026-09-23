package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.backup.NotesBackupDto
import com.example.data.local.NotesDatabase
import com.example.data.local.converters.DocumentMappers
import com.example.data.local.entity.DocumentEntity
import com.example.data.local.entity.FolderEntity
import com.example.data.local.entity.NoteEntity
import com.example.data.local.entity.SettingEntity
import com.example.data.model.ChecklistItem
import com.example.data.model.Note
import com.example.data.model.VisualCardType
import com.example.data.repository.DocumentRepository
import com.example.data.repository.NotesRepository
import com.example.ui.theme.NoteLavender
import com.squareup.moshi.Moshi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class NotesComprehensiveCujTest {

    private lateinit var database: NotesDatabase
    private lateinit var notesRepository: NotesRepository
    private lateinit var documentRepository: DocumentRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, NotesDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        notesRepository = NotesRepository(database)
        documentRepository = DocumentRepository(database.documentDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun testInitialSeedOccursOnlyOnce() = runBlocking {
        // First initialization
        notesRepository.seedInitialDataIfNeeded()
        val initialFolderCount = notesRepository.folders.first().size
        assertTrue("Expected seeded folders to be > 0", initialFolderCount > 0)

        // Second initialization call must be idempotent
        notesRepository.seedInitialDataIfNeeded()
        val secondFolderCount = notesRepository.folders.first().size
        assertEquals(initialFolderCount, secondFolderCount)
    }

    @Test
    fun testCompleteNoteJourney() = runBlocking {
        // 1. Create note
        val noteId = "cuj_note_1"
        val note = Note(
            id = noteId,
            title = "Sprint Planning",
            body = "1. Finalize release\n2. Ship APK",
            folder = "Work",
            cardType = VisualCardType.CORAL_TASK,
            checklist = listOf(
                ChecklistItem("c1", "Review code", isCompleted = true),
                ChecklistItem("c2", "Run tests", isCompleted = false)
            ),
            isFavorite = false,
            isImportant = true
        )
        notesRepository.saveNote(note)

        // 2. Retrieve & Verify
        val saved = notesRepository.getNoteByIdDirect(noteId)
        assertNotNull(saved)
        assertEquals("Sprint Planning", saved?.title)
        assertEquals(2, saved?.checklist?.size)

        // 3. Mark Favorite
        notesRepository.toggleFavorite(noteId)
        var updated = notesRepository.getNoteByIdDirect(noteId)
        assertTrue(updated?.isFavorite == true)

        // 4. Soft Delete -> Move to Trash
        notesRepository.softDeleteNote(noteId)
        val active = notesRepository.allNotes.first()
        assertFalse(active.any { it.id == noteId })
        val trash = notesRepository.trashNotes.first()
        assertTrue(trash.any { it.id == noteId })

        // 5. Restore from Trash
        notesRepository.restoreNote(noteId)
        val restoredActive = notesRepository.allNotes.first()
        assertTrue(restoredActive.any { it.id == noteId })

        // 6. Permanently Delete
        notesRepository.permanentlyDeleteNote(noteId)
        val finalActive = notesRepository.allNotes.first()
        val finalTrash = notesRepository.trashNotes.first()
        assertFalse(finalActive.any { it.id == noteId })
        assertFalse(finalTrash.any { it.id == noteId })
    }

    @Test
    fun testFolderManagementSafety() = runBlocking {
        // Create custom folder
        val created = notesRepository.createFolder("Architecture", NoteLavender)
        assertTrue(created)

        // Create duplicate folder name -> rejected
        val duplicate = notesRepository.createFolder("Architecture", NoteLavender)
        assertFalse(duplicate)

        // Put note in Architecture
        val note = Note(id = "arch_1", title = "Clean Arch", body = "MVVM + Room", folder = "Architecture")
        notesRepository.saveNote(note)

        // Rename folder Architecture -> System Design
        val renamed = notesRepository.renameFolder("Architecture", "System Design")
        assertTrue(renamed)

        // Note in Room DB must be updated to System Design automatically
        val noteAfterRename = notesRepository.getNoteByIdDirect("arch_1")
        assertEquals("System Design", noteAfterRename?.folder)

        // Delete folder -> notes safely re-parented to Personal
        val folderEntity = database.folderDao().getFolderByName("System Design")
        assertNotNull(folderEntity)
        notesRepository.deleteFolder(folderEntity!!.id, "System Design", fallbackFolder = "Personal")

        val noteAfterDelete = notesRepository.getNoteByIdDirect("arch_1")
        assertNotNull(noteAfterDelete)
        assertEquals("Personal", noteAfterDelete?.folder)
        assertFalse(noteAfterDelete?.isDeleted == true)
    }

    @Test
    fun testDocumentManagement() = runBlocking {
        val docEntity = DocumentEntity(
            id = "doc_test_101",
            fileName = "Architecture_Spec.pdf",
            displayName = "Architecture Spec",
            localPath = "/mock/path/spec.pdf",
            fileSize = 1024L * 50,
            mimeType = "application/pdf",
            pageCount = 12,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            lastOpenedAt = System.currentTimeMillis(),
            lastOpenedPage = 3,
            isFavorite = false,
            isDeleted = false,
            syncStatus = "SYNCED",
            remoteStorageRef = null,
            accentColorHex = 0xFFFEEA9F
        )

        documentRepository.saveDocumentDirect(DocumentMappers.toDomain(docEntity))

        val retrieved = documentRepository.getDocumentByIdDirect("doc_test_101")
        assertNotNull(retrieved)
        assertEquals("Architecture Spec", retrieved?.displayName)
        assertEquals(12, retrieved?.pageCount)

        // Toggle Favorite
        documentRepository.toggleFavorite("doc_test_101")
        val favs = documentRepository.favoriteDocuments.first()
        assertTrue(favs.any { it.id == "doc_test_101" })

        // Soft Delete
        documentRepository.softDeleteDocument("doc_test_101")
        val activeDocs = documentRepository.activeDocuments.first()
        assertFalse(activeDocs.any { it.id == "doc_test_101" })

        // Restore
        documentRepository.restoreDocument("doc_test_101")
        val restoredDocs = documentRepository.activeDocuments.first()
        assertTrue(restoredDocs.any { it.id == "doc_test_101" })
    }

    @Test
    fun testBackupPayloadSerialization() {
        val moshi = Moshi.Builder().build()
        val adapter = moshi.adapter(NotesBackupDto::class.java)

        val payload = NotesBackupDto(
            formatVersion = 1,
            exportedAt = System.currentTimeMillis(),
            appIdentifier = "NOTES_BACKUP",
            notes = listOf(
                NoteEntity(
                    id = "b_note_1",
                    title = "Backup Note",
                    content = "Restorable content",
                    cardType = "CREAM_LECTURE",
                    folder = "Study",
                    isFavorite = true,
                    isDeleted = false,
                    createdAt = 1000L,
                    updatedAt = 2000L,
                    isTodo = false,
                    isImportant = true,
                    checklistJson = "[]",
                    sharedWithJson = "[]",
                    tagsJson = "[\"Physics\"]",
                    noteCountText = null
                )
            ),
            folders = listOf(
                FolderEntity(
                    id = "b_folder_1",
                    name = "Study",
                    colorHex = 0xFF7CC4FA,
                    createdAt = 1000L
                )
            ),
            documents = emptyList(),
            settings = listOf(SettingEntity("dark_mode", "true"))
        )

        val json = adapter.toJson(payload)
        assertNotNull(json)
        assertTrue(json.contains("Backup Note"))
        assertTrue(json.contains("Physics"))

        val deserialized = adapter.fromJson(json)
        assertNotNull(deserialized)
        assertEquals(1, deserialized?.notes?.size)
        assertEquals("Backup Note", deserialized?.notes?.first()?.title)
        assertEquals("Study", deserialized?.folders?.first()?.name)
    }
}
