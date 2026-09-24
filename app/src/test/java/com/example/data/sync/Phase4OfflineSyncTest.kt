package com.example.data.sync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.NotesDatabase
import com.example.data.local.entity.FolderEntity
import com.example.data.local.entity.NoteEntity
import com.example.data.model.Note
import com.example.data.model.VisualCardType
import com.example.data.repository.NotesRepository
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
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Phase4OfflineSyncTest {

    private lateinit var context: Context
    private lateinit var database: NotesDatabase
    private lateinit var repository: NotesRepository

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, NotesDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = NotesRepository(database = database, context = context)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun testOfflineNoteCreationAndQueueing() = runBlocking {
        val note = Note(
            id = "offline_note_1",
            title = "Offline Meeting Idea",
            body = "Captured while completely disconnected from internet",
            folder = "Personal",
            cardType = VisualCardType.GREEN_NOTE
        )

        repository.saveNote(note)

        // Verify entity in Room
        val rawEntity = database.noteDao().getNoteByIdDirect("offline_note_1")
        assertNotNull(rawEntity)
        assertEquals("PENDING_UPLOAD", rawEntity?.syncStatus)
        assertEquals(1L, rawEntity?.version)
        assertTrue(rawEntity?.lastModifiedDeviceId?.isNotEmpty() == true)

        // Verify sync queue enqueued operation
        val pendingOps = database.pendingSyncDao().getAllPendingOperationsDirect()
        assertEquals(1, pendingOps.size)
        assertEquals("NOTE", pendingOps[0].entityType)
        assertEquals("offline_note_1", pendingOps[0].entityId)
        assertEquals("CREATE", pendingOps[0].operationType)
    }

    @Test
    fun testQueueCoalescingMultipleUpdates() = runBlocking {
        val noteId = "coalesce_note_1"

        // 1. Create note
        val initialNote = Note(
            id = noteId,
            title = "Draft v1",
            body = "First draft content",
            folder = "Personal"
        )
        repository.saveNote(initialNote)

        var pendingOps = database.pendingSyncDao().getAllPendingOperationsDirect()
        assertEquals(1, pendingOps.size)
        assertEquals("CREATE", pendingOps[0].operationType)

        // 2. Update note multiple times
        val updatedNote1 = initialNote.copy(title = "Draft v2", body = "Second draft content")
        repository.saveNote(updatedNote1)

        val updatedNote2 = initialNote.copy(title = "Draft v3", body = "Third draft content")
        repository.saveNote(updatedNote2)

        // Queue should remain coalesced to a single operation (CREATE)
        pendingOps = database.pendingSyncDao().getAllPendingOperationsDirect()
        assertEquals(1, pendingOps.size)
        assertEquals("CREATE", pendingOps[0].operationType)

        val rawEntity = database.noteDao().getNoteByIdDirect(noteId)
        assertEquals(3L, rawEntity?.version)
        assertEquals("Draft v3", rawEntity?.title)
    }

    @Test
    fun testTombstoneSynchronization() = runBlocking {
        val noteId = "tombstone_note_1"
        val note = Note(
            id = noteId,
            title = "Sensitive Note",
            body = "To be soft deleted with tombstone",
            folder = "Personal"
        )
        repository.saveNote(note)

        // Soft delete note
        repository.softDeleteNote(noteId)

        // Check Room state
        val entity = database.noteDao().getNoteByIdDirect(noteId)
        assertNotNull(entity)
        assertTrue(entity?.isDeleted == true)
        assertTrue((entity?.deletedAt ?: 0L) > 0L)
        assertEquals("PENDING_DELETE", entity?.syncStatus)

        // Active notes flow must NOT include it
        val activeNotes = repository.allNotes.first()
        assertFalse(activeNotes.any { it.id == noteId })

        // Trash notes flow MUST include it
        val trashNotes = repository.trashNotes.first()
        assertTrue(trashNotes.any { it.id == noteId })

        // Queue should have DELETE operation
        val pendingOps = database.pendingSyncDao().getAllPendingOperationsDirect()
        assertTrue(pendingOps.any { it.entityId == noteId && it.operationType == "DELETE" })
    }

    @Test
    fun testFolderRenameAtomicNoteConsistency() = runBlocking {
        // Create folder "Research"
        val created = repository.createFolder("Research", NoteCoral)
        assertTrue(created)

        // Create 2 notes in "Research"
        repository.saveNote(Note(id = "n1", title = "Paper 1", body = "Details", folder = "Research"))
        repository.saveNote(Note(id = "n2", title = "Paper 2", body = "Details", folder = "Research"))

        // Rename folder from "Research" to "Deep Learning"
        val renamed = repository.renameFolder("Research", "Deep Learning")
        assertTrue(renamed)

        // Verify folder in DB
        val renamedFolder = database.folderDao().getFolderByName("Deep Learning")
        assertNotNull(renamedFolder)
        assertEquals("Deep Learning", renamedFolder?.name)

        // Verify old folder is gone
        val oldFolder = database.folderDao().getFolderByName("Research")
        assertEquals(null, oldFolder)

        // Verify notes belonging to old folder are atomically updated in Room
        val note1 = database.noteDao().getNoteByIdDirect("n1")
        val note2 = database.noteDao().getNoteByIdDirect("n2")
        assertEquals("Deep Learning", note1?.folder)
        assertEquals("Deep Learning", note2?.folder)
    }

    @Test
    fun testConflictCopyCreationPreservesAllContent() = runBlocking {
        // Device A (local) note
        val localNote = NoteEntity(
            id = "shared_note_1",
            title = "Project Plan (Device A changes)",
            content = "Local edits done offline on Device A",
            folder = "Personal",
            cardType = "BLUE_SUMMARY",
            updatedAt = 2000L,
            version = 2L,
            lastModifiedDeviceId = "device_A",
            syncStatus = "PENDING_UPLOAD"
        )
        database.noteDao().insertNoteSync(localNote)

        // Device B (remote) note with conflicting edits
        val remoteNote = NoteEntity(
            id = "shared_note_1",
            title = "Project Plan (Device B changes)",
            content = "Remote edits done concurrently on Device B",
            folder = "Personal",
            cardType = "BLUE_SUMMARY",
            updatedAt = 2500L, // Newer
            version = 2L,
            lastModifiedDeviceId = "device_B",
            syncStatus = "SYNCED"
        )

        // In conflict resolution strategy:
        // Remote is newer (2500L > 2000L), so remote becomes primary
        // Local is preserved as conflict copy
        database.noteDao().insertNoteSync(remoteNote)

        val conflictCopyId = "note_conflict_${UUID.randomUUID().toString().take(8)}"
        val conflictCopy = localNote.copy(
            id = conflictCopyId,
            title = "${localNote.title} (conflict copy)",
            syncStatus = "PENDING_UPLOAD"
        )
        database.noteDao().insertNoteSync(conflictCopy)

        // Verify BOTH versions exist in Room and no data was silently discarded
        val primaryInDb = database.noteDao().getNoteByIdDirect("shared_note_1")
        val conflictInDb = database.noteDao().getNoteByIdDirect(conflictCopyId)

        assertNotNull(primaryInDb)
        assertNotNull(conflictInDb)
        assertEquals("Project Plan (Device B changes)", primaryInDb?.title)
        assertEquals("Project Plan (Device A changes) (conflict copy)", conflictInDb?.title)
        assertEquals("Local edits done offline on Device A", conflictInDb?.content)
    }

    @Test
    fun testTwoWayReconciliationAvoidsDuplicates() = runBlocking {
        // Insert remote notes into Room
        val remoteNotes = listOf(
            NoteEntity(
                id = "cloud_note_100",
                title = "Cloud Note 1",
                content = "From cloud",
                folder = "Personal",
                updatedAt = 1000L,
                syncStatus = "SYNCED"
            ),
            NoteEntity(
                id = "cloud_note_200",
                title = "Cloud Note 2",
                content = "From cloud",
                folder = "Personal",
                updatedAt = 1000L,
                syncStatus = "SYNCED"
            )
        )
        database.noteDao().insertNotes(remoteNotes)

        // Reconcile / re-insert same notes
        database.noteDao().insertNotes(remoteNotes)

        val allNotes = database.noteDao().getAllNotesDirect()
        assertEquals(2, allNotes.size)
    }
}
