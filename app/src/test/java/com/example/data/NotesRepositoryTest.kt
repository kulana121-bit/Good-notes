package com.example.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.NotesDatabase
import com.example.data.model.ChecklistItem
import com.example.data.model.Note
import com.example.data.model.VisualCardType
import com.example.data.repository.NotesRepository
import com.example.ui.theme.NoteBlue
import com.example.ui.theme.NoteCoral
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
class NotesRepositoryTest {

    private lateinit var database: NotesDatabase
    private lateinit var repository: NotesRepository

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, NotesDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = NotesRepository(database)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun testInsertAndRetrieveNote() = runBlocking {
        val note = Note(
            id = "test_note_1",
            title = "Test Title",
            body = "This is a test note body",
            folder = "Personal",
            cardType = VisualCardType.CORAL_TASK,
            checklist = listOf(ChecklistItem(id = "c1", text = "Task 1", isCompleted = false))
        )

        repository.saveNote(note)

        val retrieved = repository.getNoteByIdDirect("test_note_1")
        assertNotNull(retrieved)
        assertEquals("Test Title", retrieved?.title)
        assertEquals("This is a test note body", retrieved?.body)
        assertEquals("Personal", retrieved?.folder)
        assertEquals(1, retrieved?.checklist?.size)
        assertEquals("Task 1", retrieved?.checklist?.first()?.text)
    }

    @Test
    fun testSoftDeleteAndRestore() = runBlocking {
        val note = Note(
            id = "note_trash_test",
            title = "To be trashed",
            body = "Soft delete test",
            folder = "Personal"
        )
        repository.saveNote(note)

        // Verify it appears in active notes
        var activeNotes = repository.allNotes.first()
        assertTrue(activeNotes.any { it.id == "note_trash_test" })

        // Soft delete
        repository.softDeleteNote("note_trash_test")

        // Should NOT appear in active notes
        activeNotes = repository.allNotes.first()
        assertFalse(activeNotes.any { it.id == "note_trash_test" })

        // Should appear in trash
        var trashNotes = repository.trashNotes.first()
        assertTrue(trashNotes.any { it.id == "note_trash_test" })

        // Restore note
        repository.restoreNote("note_trash_test")

        // Should be back in active notes
        activeNotes = repository.allNotes.first()
        assertTrue(activeNotes.any { it.id == "note_trash_test" })

        trashNotes = repository.trashNotes.first()
        assertFalse(trashNotes.any { it.id == "note_trash_test" })
    }

    @Test
    fun testPermanentDeleteAndEmptyTrash() = runBlocking {
        val note1 = Note(id = "trash_1", title = "Trash 1", body = "Body 1")
        val note2 = Note(id = "trash_2", title = "Trash 2", body = "Body 2")
        repository.saveNote(note1)
        repository.saveNote(note2)

        repository.softDeleteNote("trash_1")
        repository.softDeleteNote("trash_2")

        var trashList = repository.trashNotes.first()
        assertEquals(2, trashList.size)

        // Permanently delete one note
        repository.permanentlyDeleteNote("trash_1")
        trashList = repository.trashNotes.first()
        assertEquals(1, trashList.size)
        assertEquals("trash_2", trashList.first().id)

        // Empty trash
        repository.emptyTrash()
        trashList = repository.trashNotes.first()
        assertTrue(trashList.isEmpty())
    }

    @Test
    fun testToggleFavorite() = runBlocking {
        val note = Note(id = "fav_test", title = "Favorite note", body = "Body", isFavorite = false)
        repository.saveNote(note)

        repository.toggleFavorite("fav_test")
        var retrieved = repository.getNoteByIdDirect("fav_test")
        assertTrue(retrieved?.isFavorite == true)

        val favorites = repository.favoriteNotes.first()
        assertTrue(favorites.any { it.id == "fav_test" })

        repository.toggleFavorite("fav_test")
        retrieved = repository.getNoteByIdDirect("fav_test")
        assertFalse(retrieved?.isFavorite == true)
    }

    @Test
    fun testFolderRenamingPreservesAllNotesInTransaction() = runBlocking {
        // Create folder Study
        repository.createFolder("Study", NoteBlue)

        // Create 2 notes inside Study
        val noteA = Note(id = "study_1", title = "Biology Cells", body = "Mitosis", folder = "Study")
        val noteB = Note(id = "study_2", title = "Genetics", body = "DNA", folder = "Study")
        repository.saveNote(noteA)
        repository.saveNote(noteB)

        // Rename folder Study -> Biology
        val success = repository.renameFolder("Study", "Biology")
        assertTrue(success)

        // Verify folder name updated
        val folders = repository.folders.first()
        assertTrue(folders.any { it.name == "Biology" })
        assertFalse(folders.any { it.name == "Study" })

        // Verify BOTH notes have folder = "Biology", NEVER reset to Personal or default
        val updatedNoteA = repository.getNoteByIdDirect("study_1")
        val updatedNoteB = repository.getNoteByIdDirect("study_2")

        assertEquals("Biology", updatedNoteA?.folder)
        assertEquals("Biology", updatedNoteB?.folder)
    }

    @Test
    fun testSafeFolderDeletionPreservesNotes() = runBlocking {
        // Create folder Project
        repository.createFolder("Project", NoteCoral)

        // Create note inside Project
        val note = Note(id = "proj_note", title = "Specs", body = "Architecture", folder = "Project")
        repository.saveNote(note)

        val folder = repository.folders.first().first { it.name == "Project" }

        // Delete folder
        repository.deleteFolder(folderId = folder.id, folderName = "Project", fallbackFolder = "Personal")

        // Folder is gone
        val folders = repository.folders.first()
        assertFalse(folders.any { it.name == "Project" })

        // Note is SAFE - moved to fallback Personal, NOT deleted!
        val preservedNote = repository.getNoteByIdDirect("proj_note")
        assertNotNull(preservedNote)
        assertEquals("Personal", preservedNote?.folder)
        assertFalse(preservedNote?.isDeleted == true)
    }

    @Test
    fun testSearchNotes() = runBlocking {
        val note1 = Note(id = "s1", title = "Quantum Physics", body = "Schrodinger wave equation")
        val note2 = Note(id = "s2", title = "Grocery List", body = "Milk, Eggs, Bread")
        repository.saveNote(note1)
        repository.saveNote(note2)

        val physicsResults = repository.searchNotes("Quantum").first()
        assertEquals(1, physicsResults.size)
        assertEquals("s1", physicsResults.first().id)

        val bodySearchResults = repository.searchNotes("Milk").first()
        assertEquals(1, bodySearchResults.size)
        assertEquals("s2", bodySearchResults.first().id)
    }

    @Test
    fun testSettingsPersistence() = runBlocking {
        repository.setDarkMode(true)
        assertTrue(repository.isDarkMode().first())

        repository.setDarkMode(false)
        assertFalse(repository.isDarkMode().first())

        repository.setAutoSave(true)
        assertTrue(repository.isAutoSave().first())

        repository.setDefaultSortOrder("Alphabetical (A-Z)")
        assertEquals("Alphabetical (A-Z)", repository.getDefaultSortOrder().first())
    }
}
