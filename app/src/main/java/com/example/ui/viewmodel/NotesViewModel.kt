package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.backup.BackupManager
import com.example.data.backup.CloudBackupDto
import com.example.data.backup.CloudBackupManager
import com.example.data.backup.CloudBackupProgress
import com.example.data.backup.CloudRestoreReport
import com.example.data.backup.RestoreResultSummary
import com.example.data.local.NotesDatabase
import com.example.data.migration.DocumentMigrationState
import com.example.data.model.ChecklistItem
import com.example.data.model.Document
import com.example.data.model.Folder
import com.example.data.model.Note
import com.example.data.model.VisualCardType
import com.example.data.remote.auth.FirebaseAuthService
import com.example.data.remote.auth.UserSummary
import com.example.data.remote.drive.DriveAuthState
import com.example.data.remote.drive.DriveStorageInfo
import com.example.data.remote.drive.DriveTestReport
import com.example.data.remote.drive.GoogleDriveAuthManager
import com.example.data.remote.drive.GoogleDriveService
import com.example.data.repository.DocumentRepository
import com.example.data.repository.NotesRepository
import com.example.data.sync.NotesCloudSyncWorker
import com.example.data.sync.NotesSyncWorker
import com.example.data.sync.SyncManager
import com.example.data.sync.SyncReport
import com.example.data.sync.SyncState
import com.example.util.AudioPlayerManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class NotesViewModel(
    application: Application,
    private val repository: NotesRepository = NotesRepository(NotesDatabase.getInstance(application), application),
    private val documentRepository: DocumentRepository = DocumentRepository(NotesDatabase.getInstance(application).documentDao()),
    private val backupManager: BackupManager = BackupManager(NotesDatabase.getInstance(application)),
    private val authService: FirebaseAuthService = FirebaseAuthService(application),
    private val syncManager: SyncManager = SyncManager(application, NotesDatabase.getInstance(application), authService),
    private val driveAuthManager: GoogleDriveAuthManager = GoogleDriveAuthManager(application),
    private val driveService: GoogleDriveService = GoogleDriveService(application, driveAuthManager),
    private val cloudBackupManager: CloudBackupManager = CloudBackupManager(application, NotesDatabase.getInstance(application), authService, driveService)
) : AndroidViewModel(application) {

    val audioPlayer: AudioPlayerManager = AudioPlayerManager(application)

    // Current User & Active UID for strict Account Isolation
    val currentUser: StateFlow<UserSummary?> = authService.currentUser
    val currentUserId: StateFlow<String> = currentUser.map { it?.uid ?: "" }
        .stateIn(viewModelScope, SharingStarted.Eagerly, authService.getCurrentUserId() ?: "")

    val pendingOperationsCount: StateFlow<Int> = currentUserId.flatMapLatest { uid ->
        repository.getPendingOperationsCount(uid)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    // Reactive single source of truth from Room Database isolated by current user UID
    private val rawNotes: StateFlow<List<Note>> = currentUserId.flatMapLatest { uid ->
        repository.getActiveNotes(uid)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val favoriteNotes: StateFlow<List<Note>> = currentUserId.flatMapLatest { uid ->
        repository.getFavoriteNotes(uid)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val trashNotes: StateFlow<List<Note>> = currentUserId.flatMapLatest { uid ->
        repository.getTrashNotes(uid)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val folders: StateFlow<List<Folder>> = currentUserId.flatMapLatest { uid ->
        repository.getFolders(uid)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val isOnboardingCompleted: StateFlow<Boolean?> = repository.isOnboardingCompleted()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val activeNotesCount: StateFlow<Int> = currentUserId.flatMapLatest { uid ->
        repository.getActiveNotesCount(uid)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val favoritesCount: StateFlow<Int> = currentUserId.flatMapLatest { uid ->
        repository.getFavoritesCount(uid)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val trashCount: StateFlow<Int> = currentUserId.flatMapLatest { uid ->
        repository.getTrashCount(uid)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    // Document management flows isolated by current user UID
    val documents: StateFlow<List<Document>> = currentUserId.flatMapLatest { uid ->
        documentRepository.getActiveDocuments(uid)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val favoriteDocuments: StateFlow<List<Document>> = currentUserId.flatMapLatest { uid ->
        documentRepository.getFavoriteDocuments(uid)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val trashDocuments: StateFlow<List<Document>> = currentUserId.flatMapLatest { uid ->
        documentRepository.getTrashDocuments(uid)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeDocumentsCount: StateFlow<Int> = currentUserId.flatMapLatest { uid ->
        documentRepository.getActiveDocumentsCount(uid)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val totalDocumentsSize: StateFlow<Long> = currentUserId.flatMapLatest { uid ->
        documentRepository.getTotalDocumentsSize(uid)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    private val _selectedDocument = MutableStateFlow<Document?>(null)
    val selectedDocument: StateFlow<Document?> = _selectedDocument.asStateFlow()

    // Auth & Sync flows
    val syncState: StateFlow<SyncState> = syncManager.syncState
    val lastSyncTimestamp: StateFlow<Long> = syncManager.lastSyncTimestamp
    val lastSyncReport: StateFlow<SyncReport?> = syncManager.lastSyncReport
    val documentMigrationState: StateFlow<DocumentMigrationState> = syncManager.migrationManager.migrationState

    // Instant theme state
    private val _isDarkModeState = MutableStateFlow(true)
    val isDarkMode: StateFlow<Boolean> = _isDarkModeState.asStateFlow()

    val isAutoSave: StateFlow<Boolean> = repository.isAutoSave()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val selectedFont: StateFlow<String> = repository.getSelectedFont()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "Outfit (Editorial)")

    val baseTextSize: StateFlow<Float> = repository.getBaseTextSize()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 16f)

    val defaultSortOrder: StateFlow<String> = repository.getDefaultSortOrder()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "Recently Modified")

    // Sorted notes
    val notes: StateFlow<List<Note>> = combine(rawNotes, defaultSortOrder) { list, sortOrder ->
        when (sortOrder) {
            "Alphabetical (A-Z)" -> list.sortedBy { it.title.lowercase() }
            "Important First" -> list.sortedWith(
                compareByDescending<Note> { it.isImportant || it.isFavorite }
                    .thenByDescending { it.updatedAt }
            )
            else -> list.sortedByDescending { it.updatedAt }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Filter & Navigation
    private val _activeFilter = MutableStateFlow(NotesFilter.ALL)
    val activeFilter: StateFlow<NotesFilter> = _activeFilter.asStateFlow()

    private val _currentDestination = MutableStateFlow(NavDestination.HOME)
    val currentDestination: StateFlow<NavDestination> = _currentDestination.asStateFlow()

    private val _navigationBackStack = MutableStateFlow<List<NavDestination>>(listOf(NavDestination.HOME))

    private val _selectedNote = MutableStateFlow<Note?>(null)
    val selectedNote: StateFlow<Note?> = _selectedNote.asStateFlow()

    private val _selectedFolder = MutableStateFlow<Folder?>(null)
    val selectedFolder: StateFlow<Folder?> = _selectedFolder.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val searchResults: StateFlow<List<Note>> = combine(_searchQuery, currentUserId) { query, uid ->
        query to uid
    }.flatMapLatest { (query, uid) ->
        repository.searchNotes(uid, query)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val searchDocumentResults: StateFlow<List<Document>> = combine(_searchQuery, currentUserId) { query, uid ->
        query to uid
    }.flatMapLatest { (query, uid) ->
        documentRepository.searchDocuments(uid, query)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isNavigationSheetOpen = MutableStateFlow(false)
    val isNavigationSheetOpen: StateFlow<Boolean> = _isNavigationSheetOpen.asStateFlow()

    // Editor state
    private val _saveStatus = MutableStateFlow(SaveStatus.SAVED)
    val saveStatus: StateFlow<SaveStatus> = _saveStatus.asStateFlow()

    private var autosaveJob: Job? = null

    private val _isHighlightActive = MutableStateFlow(false)
    val isHighlightActive: StateFlow<Boolean> = _isHighlightActive.asStateFlow()

    // Google Drive Integration & Account Mismatch States
    private val _driveAuthState = MutableStateFlow<DriveAuthState>(DriveAuthState.Disconnected)
    val driveAuthState: StateFlow<DriveAuthState> = _driveAuthState.asStateFlow()

    private val _driveStorageInfo = MutableStateFlow<DriveStorageInfo?>(null)
    val driveStorageInfo: StateFlow<DriveStorageInfo?> = _driveStorageInfo.asStateFlow()

    private val _isDriveTesting = MutableStateFlow(false)
    val isDriveTesting: StateFlow<Boolean> = _isDriveTesting.asStateFlow()

    private val _lastDriveTestReport = MutableStateFlow<DriveTestReport?>(null)
    val lastDriveTestReport: StateFlow<DriveTestReport?> = _lastDriveTestReport.asStateFlow()

    // Unified Cloud Backup & Restore States
    val cloudBackupProgress: StateFlow<CloudBackupProgress?> = cloudBackupManager.backupProgress
    val cloudRestoreProgress: StateFlow<CloudBackupProgress?> = cloudBackupManager.restoreProgress

    val isAutoCloudBackup: StateFlow<Boolean> = repository.isAutoCloudBackup()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val isCloudBackupWifiOnly: StateFlow<Boolean> = repository.isCloudBackupWifiOnly()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val isCloudBackupIncludeDocs: StateFlow<Boolean> = repository.isCloudBackupIncludeDocs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val lastCloudBackupTimestamp: StateFlow<Long> = repository.getLastCloudBackupTimestamp()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    val lastCloudBackupStatus: StateFlow<String> = repository.getLastCloudBackupStatus()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "Never")

    private val _availableCloudBackup = MutableStateFlow<CloudBackupDto?>(null)
    val availableCloudBackup: StateFlow<CloudBackupDto?> = _availableCloudBackup.asStateFlow()

    init {
        // Observe current user changes to initialize folders and verify drive status
        viewModelScope.launch {
            currentUserId.collect { uid ->
                try {
                    repository.seedInitialDataIfNeeded(uid)
                } catch (t: Throwable) {
                    Log.w("NotesViewModel", "seedInitialDataIfNeeded warning: ${t.message}")
                }
                refreshDriveStatus()
            }
        }

        viewModelScope.launch {
            try {
                repository.isDarkMode().collect { persistedMode ->
                    _isDarkModeState.value = persistedMode
                }
            } catch (t: Throwable) {
                Log.w("NotesViewModel", "DarkMode flow warning: ${t.message}")
            }
        }

        try {
            NotesSyncWorker.schedulePeriodicSync(application)
        } catch (t: Throwable) {
            Log.w("NotesViewModel", "WorkManager schedule warning: ${t.message}")
        }
    }

    fun completeOnboarding() {
        viewModelScope.launch {
            repository.setOnboardingCompleted()
        }
    }

    fun selectFilter(filter: NotesFilter) {
        _activeFilter.value = filter
    }

    fun navigateTo(destination: NavDestination) {
        if (_currentDestination.value != destination) {
            _navigationBackStack.update { it + destination }
            _currentDestination.value = destination
        }
        _isNavigationSheetOpen.value = false
    }

    fun navigateBack(): Boolean {
        if (_currentDestination.value == NavDestination.NOTE_EDITOR) {
            flushPendingSave()
        }

        if (_navigationBackStack.value.size > 1) {
            val updated = _navigationBackStack.value.dropLast(1)
            _navigationBackStack.value = updated
            _currentDestination.value = updated.last()
            return true
        }
        return false
    }

    fun openNoteEditor(note: Note?) {
        val uid = currentUserId.value
        if (note != null) {
            _selectedNote.value = note
            _saveStatus.value = SaveStatus.SAVED
            navigateTo(NavDestination.NOTE_EDITOR)
        } else {
            val newNote = Note(
                id = "note_${UUID.randomUUID().toString().take(8)}",
                title = "",
                body = "",
                cardType = VisualCardType.CREAM_LECTURE,
                folder = _selectedFolder.value?.name ?: "Personal",
                updatedAtText = "Just now",
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
            viewModelScope.launch {
                repository.saveNote(newNote, uid)
            }
            _selectedNote.value = newNote
            _saveStatus.value = SaveStatus.SAVED
            navigateTo(NavDestination.NOTE_EDITOR)
        }
    }

    fun onNoteContentChanged(updatedNote: Note) {
        _selectedNote.value = updatedNote
        _saveStatus.value = SaveStatus.SAVING

        autosaveJob?.cancel()
        autosaveJob = viewModelScope.launch {
            delay(500)
            repository.saveNote(updatedNote, currentUserId.value)
            _saveStatus.value = SaveStatus.SAVED
        }
    }

    fun saveNote(updatedNote: Note) {
        autosaveJob?.cancel()
        _selectedNote.value = updatedNote
        viewModelScope.launch {
            _saveStatus.value = SaveStatus.SAVING
            repository.saveNote(updatedNote, currentUserId.value)
            _saveStatus.value = SaveStatus.SAVED
        }
    }

    fun flushPendingSave() {
        autosaveJob?.cancel()
        val note = _selectedNote.value ?: return
        viewModelScope.launch {
            repository.saveNote(note, currentUserId.value)
            _saveStatus.value = SaveStatus.SAVED
        }
    }

    fun toggleFavorite(noteId: String) {
        viewModelScope.launch {
            repository.toggleFavorite(noteId, currentUserId.value)
        }
        if (_selectedNote.value?.id == noteId) {
            _selectedNote.update { it?.copy(isFavorite = !(it.isFavorite)) }
        }
    }

    fun softDeleteNote(noteId: String) {
        autosaveJob?.cancel()
        autosaveJob = null
        if (_selectedNote.value?.id == noteId) {
            _selectedNote.value = null
            navigateBack()
        }
        viewModelScope.launch {
            repository.softDeleteNote(noteId, currentUserId.value)
        }
    }

    fun restoreNote(noteId: String) {
        viewModelScope.launch {
            repository.restoreNote(noteId, currentUserId.value)
        }
    }

    fun permanentlyDeleteNote(noteId: String) {
        autosaveJob?.cancel()
        if (_selectedNote.value?.id == noteId) {
            _selectedNote.value = null
        }
        viewModelScope.launch {
            repository.permanentlyDeleteNote(noteId, currentUserId.value)
        }
    }

    fun emptyTrash() {
        autosaveJob?.cancel()
        viewModelScope.launch {
            repository.emptyTrash(currentUserId.value)
        }
    }

    fun toggleChecklistItem(noteId: String, itemId: String) {
        val currentNote = if (_selectedNote.value?.id == noteId) {
            _selectedNote.value
        } else {
            notes.value.firstOrNull { it.id == noteId }
        } ?: return

        val updatedChecklist = currentNote.checklist.map { item ->
            if (item.id == itemId) item.copy(isCompleted = !item.isCompleted) else item
        }
        val updatedNote = currentNote.copy(checklist = updatedChecklist)
        saveNote(updatedNote)
    }

    fun addChecklistItem(noteId: String, text: String) {
        if (text.isBlank()) return
        val currentNote = if (_selectedNote.value?.id == noteId) {
            _selectedNote.value
        } else {
            notes.value.firstOrNull { it.id == noteId }
        } ?: return

        val newItem = ChecklistItem(id = UUID.randomUUID().toString().take(6), text = text.trim())
        val updatedNote = currentNote.copy(checklist = currentNote.checklist + newItem)
        saveNote(updatedNote)
    }

    fun moveNoteToFolder(noteId: String, folderName: String) {
        viewModelScope.launch {
            repository.moveNoteToFolder(noteId, folderName, currentUserId.value)
        }
    }

    fun createFolder(name: String, color: Color, onResult: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            val success = repository.createFolder(name, color, currentUserId.value)
            onResult(success)
        }
    }

    fun renameFolder(oldName: String, newName: String, onResult: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            val success = repository.renameFolder(oldName, newName, currentUserId.value)
            onResult(success)
        }
    }

    fun deleteFolder(folder: Folder) {
        viewModelScope.launch {
            repository.deleteFolder(folder.id, folder.name, fallbackFolder = "Personal", userId = currentUserId.value)
            if (_selectedFolder.value?.id == folder.id) {
                _selectedFolder.value = null
            }
        }
    }

    // Document handling with modern SAF & User Isolation
    fun importDocument(uri: Uri, onResult: (Result<Document>) -> Unit = {}) {
        viewModelScope.launch {
            val result = documentRepository.importPdf(uri, getApplication(), currentUserId.value)
            if (result.isSuccess) {
                syncManager.syncNow(currentUserId.value)
            }
            onResult(result)
        }
    }

    fun scanFolder(treeUri: Uri, onResult: (Int) -> Unit = {}) {
        viewModelScope.launch {
            val result = documentRepository.scanFolderViaTreeUri(getApplication(), treeUri, currentUserId.value)
            val count = result.getOrDefault(0)
            if (count > 0) {
                syncManager.syncNow(currentUserId.value)
            }
            onResult(count)
        }
    }

    fun downloadDocument(document: Document, onComplete: (Result<Document>) -> Unit = {}) {
        viewModelScope.launch {
            val res = documentRepository.downloadDocumentFromDrive(document.id, getApplication())
            if (res.isSuccess) {
                val updated = res.getOrThrow()
                if (_selectedDocument.value?.id == document.id) {
                    _selectedDocument.value = updated
                }
            }
            onComplete(res)
        }
    }

    fun openDocument(document: Document) {
        _selectedDocument.value = document
        navigateTo(NavDestination.PDF_READER)
    }

    fun updateDocumentPage(docId: String, page: Int) {
        viewModelScope.launch {
            documentRepository.updateLastOpened(docId, page)
        }
    }

    fun toggleDocumentFavorite(docId: String) {
        viewModelScope.launch {
            documentRepository.toggleFavorite(docId)
        }
        if (_selectedDocument.value?.id == docId) {
            _selectedDocument.update { it?.copy(isFavorite = !(it.isFavorite)) }
        }
    }

    fun softDeleteDocument(docId: String) {
        viewModelScope.launch {
            documentRepository.softDeleteDocument(docId)
            if (_selectedDocument.value?.id == docId) {
                navigateBack()
            }
        }
    }

    fun restoreDocument(docId: String) {
        viewModelScope.launch {
            documentRepository.restoreDocument(docId)
        }
    }

    fun createVoiceNote(audioFile: File, durationMs: Long, customTitle: String? = null) {
        val timeLabel = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(Date())
        val title = customTitle ?: "Voice Note $timeLabel"
        val durationSec = (durationMs / 1000).coerceAtLeast(1)
        val note = Note(
            id = "note_${UUID.randomUUID().toString().take(8)}",
            title = title,
            body = "Voice recording ($durationSec sec)",
            cardType = VisualCardType.LAVENDER_NOTE,
            folder = "Personal",
            updatedAtText = "Just now",
            audioUri = audioFile.absolutePath,
            audioDurationMs = durationMs,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        saveNote(note)
    }

    fun permanentlyDeleteDocument(docId: String) {
        viewModelScope.launch {
            documentRepository.permanentlyDeleteDocument(docId, getApplication())
        }
    }

    override fun onCleared() {
        super.onCleared()
        audioPlayer.release()
    }

    // Backup & Restore
    fun exportBackup(destinationUri: Uri, onResult: (Result<Int>) -> Unit) {
        viewModelScope.launch {
            val result = backupManager.exportBackup(getApplication(), destinationUri)
            onResult(result)
        }
    }

    fun restoreBackup(sourceUri: Uri, onResult: (Result<RestoreResultSummary>) -> Unit) {
        viewModelScope.launch {
            val result = backupManager.restoreBackup(getApplication(), sourceUri)
            onResult(result)
        }
    }

    // Cloud Auth & Safe Account Switching
    fun signInWithGoogle(activityContext: Context? = null, webClientId: String? = null, onResult: (Result<UserSummary>) -> Unit) {
        viewModelScope.launch {
            NotesCloudSyncWorker.cancelSync(getApplication())
            val result = authService.signInWithGoogle(activityContext, webClientId)
            if (result.isSuccess) {
                val user = result.getOrThrow()
                refreshDriveStatus()
                syncManager.syncNow(user.uid)
            }
            onResult(result)
        }
    }

    fun switchGoogleAccount(activityContext: Context? = null, webClientId: String? = null, onResult: (Result<UserSummary>) -> Unit) {
        viewModelScope.launch {
            NotesCloudSyncWorker.cancelSync(getApplication())
            val result = authService.switchGoogleAccount(activityContext, webClientId)
            if (result.isSuccess) {
                val user = result.getOrThrow()
                refreshDriveStatus()
                syncManager.syncNow(user.uid)
            }
            onResult(result)
        }
    }

    fun signInWithEmail(email: String, pass: String, onResult: (Result<UserSummary>) -> Unit) {
        viewModelScope.launch {
            NotesCloudSyncWorker.cancelSync(getApplication())
            val result = authService.signInWithEmail(email, pass)
            if (result.isSuccess) {
                val user = result.getOrThrow()
                refreshDriveStatus()
                syncManager.syncNow(user.uid)
            }
            onResult(result)
        }
    }

    fun signUpWithEmail(email: String, pass: String, name: String, onResult: (Result<UserSummary>) -> Unit) {
        viewModelScope.launch {
            NotesCloudSyncWorker.cancelSync(getApplication())
            val result = authService.signUpWithEmail(email, pass, name)
            if (result.isSuccess) {
                val user = result.getOrThrow()
                refreshDriveStatus()
                syncManager.syncNow(user.uid)
            }
            onResult(result)
        }
    }

    fun signInAnonymously(onResult: (Result<UserSummary>) -> Unit) {
        viewModelScope.launch {
            NotesCloudSyncWorker.cancelSync(getApplication())
            val result = authService.signInAnonymously()
            if (result.isSuccess) {
                val user = result.getOrThrow()
                refreshDriveStatus()
                syncManager.syncNow(user.uid)
            }
            onResult(result)
        }
    }

    fun signOut(onResult: (Result<Unit>) -> Unit = {}) {
        viewModelScope.launch {
            NotesCloudSyncWorker.cancelSync(getApplication())
            driveAuthManager.disconnect()
            driveService.clearCache()
            _driveAuthState.value = DriveAuthState.Disconnected
            _driveStorageInfo.value = null
            _lastDriveTestReport.value = null

            val result = authService.signOut()
            onResult(result)
        }
    }

    fun syncNow(onResult: (Result<SyncReport>) -> Unit = {}) {
        viewModelScope.launch {
            val result = syncManager.syncNow(currentUserId.value)
            onResult(result)
        }
    }

    fun refreshDriveStatus() {
        viewModelScope.launch {
            val expectedEmail = authService.currentUser.value?.email
            val authState = driveService.checkAuthorization(expectedEmail = expectedEmail)
            _driveAuthState.value = authState
            if (authState is DriveAuthState.Connected) {
                val quotaResult = driveService.getStorageQuota()
                _driveStorageInfo.value = quotaResult.getOrNull()
            } else {
                _driveStorageInfo.value = null
            }
        }
    }

    fun getDriveAuthorizationIntent(): Intent {
        val preferredEmail = authService.currentUser.value?.email
        return driveAuthManager.getAuthorizationIntent(preferredEmail)
    }

    fun getSwitchDriveAccountIntent(): Intent {
        val preferredEmail = authService.currentUser.value?.email
        return driveAuthManager.getSwitchAccountIntent(preferredEmail)
    }

    fun handleDriveAuthResult(data: Intent?, onResult: (Result<DriveAuthState.Connected>) -> Unit) {
        viewModelScope.launch {
            val result = driveAuthManager.handleAuthorizationResult(data)
            if (result.isSuccess) {
                refreshDriveStatus()
            } else {
                _driveAuthState.value = DriveAuthState.Error(result.exceptionOrNull()?.localizedMessage ?: "Authorization failed")
            }
            onResult(result)
        }
    }

    fun disconnectDrive(onResult: (Result<Unit>) -> Unit = {}) {
        viewModelScope.launch {
            val result = driveAuthManager.disconnect()
            driveService.clearCache()
            _driveAuthState.value = DriveAuthState.Disconnected
            _driveStorageInfo.value = null
            _lastDriveTestReport.value = null
            onResult(result)
        }
    }

    fun testDriveConnection(onResult: (Result<DriveTestReport>) -> Unit) {
        viewModelScope.launch {
            _isDriveTesting.value = true
            val result = driveService.testDriveIntegration()
            _isDriveTesting.value = false
            _lastDriveTestReport.value = result.getOrNull()
            if (result.isSuccess) {
                refreshDriveStatus()
            }
            onResult(result)
        }
    }

    fun setAutoCloudBackup(enabled: Boolean) {
        viewModelScope.launch { repository.setAutoCloudBackup(enabled) }
    }

    fun setCloudBackupWifiOnly(enabled: Boolean) {
        viewModelScope.launch { repository.setCloudBackupWifiOnly(enabled) }
    }

    fun setCloudBackupIncludeDocs(enabled: Boolean) {
        viewModelScope.launch { repository.setCloudBackupIncludeDocs(enabled) }
    }

    fun backupToCloudNow(onComplete: (Result<CloudBackupDto>) -> Unit = {}) {
        viewModelScope.launch {
            val includeDocs = repository.isCloudBackupIncludeDocs().firstOrNull() ?: true
            val result = cloudBackupManager.backupToCloud(includeDocuments = includeDocs)
            if (result.isSuccess) {
                refreshDriveStatus()
            }
            onComplete(result)
        }
    }

    fun restoreFromCloudNow(onComplete: (Result<CloudRestoreReport>) -> Unit = {}) {
        viewModelScope.launch {
            val result = cloudBackupManager.restoreFromCloud()
            if (result.isSuccess) {
                _availableCloudBackup.value = null
                refreshDriveStatus()
            }
            onComplete(result)
        }
    }

    fun checkForAvailableCloudBackup() {
        viewModelScope.launch {
            val count = activeNotesCount.value
            if (count == 0 && driveService.checkAuthorization(authService.currentUser.value?.email) is DriveAuthState.Connected) {
                val backupRes = cloudBackupManager.checkForCloudBackup()
                if (backupRes.isSuccess) {
                    _availableCloudBackup.value = backupRes.getOrNull()
                }
            }
        }
    }

    fun dismissCloudBackupPrompt() {
        _availableCloudBackup.value = null
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun toggleNavigationSheet(isOpen: Boolean) {
        _isNavigationSheetOpen.value = isOpen
    }

    fun setDarkMode(enabled: Boolean) {
        _isDarkModeState.value = enabled
        viewModelScope.launch {
            repository.setDarkMode(enabled)
        }
    }

    fun setAutoSave(enabled: Boolean) {
        viewModelScope.launch {
            repository.setAutoSave(enabled)
        }
    }

    fun setSelectedFont(font: String) {
        viewModelScope.launch {
            repository.setSelectedFont(font)
        }
    }

    fun setBaseTextSize(size: Float) {
        viewModelScope.launch {
            repository.setBaseTextSize(size)
        }
    }

    fun setDefaultSortOrder(order: String) {
        viewModelScope.launch {
            repository.setDefaultSortOrder(order)
        }
    }

    fun selectFolder(folder: Folder?) {
        _selectedFolder.value = folder
    }

    fun toggleHighlight() {
        _isHighlightActive.value = !_isHighlightActive.value
    }

    companion object {
        fun factory(application: Application): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val db = NotesDatabase.getInstance(application)
                val repository = NotesRepository(db, application)
                val docRepository = DocumentRepository(db.documentDao())
                val backupManager = BackupManager(db)
                val authService = FirebaseAuthService(application)
                val syncManager = SyncManager(application, db, authService)
                val driveAuthManager = GoogleDriveAuthManager(application)
                val driveService = GoogleDriveService(application, driveAuthManager)
                val cloudBackupManager = CloudBackupManager(application, db, authService, driveService)
                return NotesViewModel(
                    application = application,
                    repository = repository,
                    documentRepository = docRepository,
                    backupManager = backupManager,
                    authService = authService,
                    syncManager = syncManager,
                    driveAuthManager = driveAuthManager,
                    driveService = driveService,
                    cloudBackupManager = cloudBackupManager
                ) as T
            }
        }
    }
}
