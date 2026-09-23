package com.example

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.components.NavigationSheet
import com.example.ui.screens.DocumentsScreen
import com.example.ui.screens.FavoritesScreen
import com.example.ui.screens.FoldersScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.NoteEditorScreen
import com.example.ui.screens.PdfReaderScreen
import com.example.ui.screens.SearchScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.TrashScreen
import com.example.ui.theme.MotionTokens
import com.example.ui.theme.NotesTheme
import com.example.ui.theme.rememberReducedMotion
import com.example.ui.viewmodel.NavDestination
import com.example.ui.viewmodel.NotesFilter
import com.example.ui.viewmodel.NotesViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val app = application
            val viewModel: NotesViewModel = viewModel(factory = NotesViewModel.factory(app))
            val isDarkMode by viewModel.isDarkMode.collectAsState()

            NotesTheme(darkTheme = isDarkMode) {
                NotesApp(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun NotesApp(
    viewModel: NotesViewModel = viewModel(
        factory = NotesViewModel.factory(
            LocalContext.current.applicationContext as Application
        )
    )
) {
    val isReducedMotion = rememberReducedMotion()
    val notes by viewModel.notes.collectAsState()
    val favoriteNotes by viewModel.favoriteNotes.collectAsState()
    val trashNotes by viewModel.trashNotes.collectAsState()
    val folders by viewModel.folders.collectAsState()
    val activeFilter by viewModel.activeFilter.collectAsState()
    val currentDestination by viewModel.currentDestination.collectAsState()
    val selectedNote by viewModel.selectedNote.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val isNavigationSheetOpen by viewModel.isNavigationSheetOpen.collectAsState()
    val saveStatus by viewModel.saveStatus.collectAsState()

    val activeNotesCount by viewModel.activeNotesCount.collectAsState()
    val favoritesCount by viewModel.favoritesCount.collectAsState()
    val trashCount by viewModel.trashCount.collectAsState()

    val documents by viewModel.documents.collectAsState()
    val activeDocumentsCount by viewModel.activeDocumentsCount.collectAsState()
    val selectedDocument by viewModel.selectedDocument.collectAsState()

    val currentUser by viewModel.currentUser.collectAsState()
    val syncState by viewModel.syncState.collectAsState()
    val lastSyncTimestamp by viewModel.lastSyncTimestamp.collectAsState()
    val lastSyncReport by viewModel.lastSyncReport.collectAsState()

    val isDarkMode by viewModel.isDarkMode.collectAsState()
    val isAutoSave by viewModel.isAutoSave.collectAsState()
    val selectedFont by viewModel.selectedFont.collectAsState()
    val baseTextSize by viewModel.baseTextSize.collectAsState()
    val defaultSortOrder by viewModel.defaultSortOrder.collectAsState()

    var isSearchActive by remember { mutableStateOf(false) }

    // Intercept hardware back button
    BackHandler(enabled = isSearchActive || currentDestination != NavDestination.HOME) {
        if (isSearchActive) {
            isSearchActive = false
        } else {
            viewModel.navigateBack()
        }
    }

    Scaffold(modifier = Modifier.fillMaxSize()) { _ ->
        if (isSearchActive) {
            SearchScreen(
                query = searchQuery,
                onQueryChange = { viewModel.setSearchQuery(it) },
                notes = if (searchQuery.isBlank()) notes else searchResults,
                onNoteClick = { note ->
                    isSearchActive = false
                    viewModel.openNoteEditor(note)
                },
                onToggleFavorite = { viewModel.toggleFavorite(it) },
                onBack = { isSearchActive = false }
            )
        } else {
            AnimatedContent(
                targetState = currentDestination,
                transitionSpec = {
                    if (isReducedMotion) {
                        fadeIn(tween(150)) togetherWith fadeOut(tween(150))
                    } else if (targetState == NavDestination.NOTE_EDITOR || targetState == NavDestination.PDF_READER) {
                        (fadeIn(animationSpec = MotionTokens.standardTween()) +
                                slideInVertically(animationSpec = MotionTokens.gentleSpring()) { it / 14 } +
                                scaleIn(initialScale = 0.96f, animationSpec = MotionTokens.gentleSpring()))
                            .togetherWith(
                                fadeOut(animationSpec = MotionTokens.fastTween()) +
                                        scaleOut(targetScale = 0.97f, animationSpec = MotionTokens.fastTween())
                            )
                    } else if (initialState == NavDestination.NOTE_EDITOR || initialState == NavDestination.PDF_READER) {
                        (fadeIn(animationSpec = MotionTokens.standardTween()) +
                                scaleIn(initialScale = 0.97f, animationSpec = MotionTokens.gentleSpring()))
                            .togetherWith(
                                fadeOut(animationSpec = MotionTokens.fastTween()) +
                                        slideOutVertically(animationSpec = MotionTokens.fastTween()) { it / 14 } +
                                        scaleOut(targetScale = 0.96f, animationSpec = MotionTokens.fastTween())
                            )
                    } else {
                        (fadeIn(animationSpec = MotionTokens.standardTween()) +
                                scaleIn(initialScale = 0.98f, animationSpec = MotionTokens.gentleSpring()))
                            .togetherWith(
                                fadeOut(animationSpec = MotionTokens.fastTween()) +
                                        scaleOut(targetScale = 0.98f, animationSpec = MotionTokens.fastTween())
                            )
                    }
                },
                label = "screen_transition"
            ) { destination ->
                when (destination) {
                    NavDestination.HOME, NavDestination.ALL_NOTES -> {
                        HomeScreen(
                            notes = notes,
                            activeFilter = if (destination == NavDestination.ALL_NOTES) NotesFilter.ALL else activeFilter,
                            onFilterSelected = { viewModel.selectFilter(it) },
                            onNoteClick = { viewModel.openNoteEditor(it) },
                            onToggleFavorite = { viewModel.toggleFavorite(it) },
                            onToggleChecklistItem = { noteId, itemId ->
                                viewModel.toggleChecklistItem(noteId, itemId)
                            },
                            onDeleteNote = { noteId ->
                                viewModel.softDeleteNote(noteId)
                            },
                            onMenuClick = { viewModel.toggleNavigationSheet(true) },
                            onSearchClick = { isSearchActive = true },
                            onNewNoteClick = { viewModel.openNoteEditor(null) },
                            onSaveVoiceNote = { audioFile, durationMs ->
                                viewModel.createVoiceNote(audioFile, durationMs)
                            }
                        )
                    }

                    NavDestination.NOTE_EDITOR -> {
                        selectedNote?.let { note ->
                            NoteEditorScreen(
                                note = note,
                                saveStatus = saveStatus,
                                onBack = { viewModel.navigateBack() },
                                onSave = { viewModel.saveNote(it) },
                                onContentChange = { viewModel.onNoteContentChanged(it) },
                                onToggleFavorite = { viewModel.toggleFavorite(it) },
                                onDeleteNote = {
                                    viewModel.softDeleteNote(it)
                                    viewModel.navigateBack()
                                }
                            )
                        } ?: run {
                            viewModel.navigateTo(NavDestination.HOME)
                        }
                    }

                    NavDestination.FOLDERS -> {
                        FoldersScreen(
                            folders = folders,
                            notes = notes,
                            onNoteClick = { viewModel.openNoteEditor(it) },
                            onToggleFavorite = { viewModel.toggleFavorite(it) },
                            onCreateFolder = { name, color -> viewModel.createFolder(name, color) },
                            onRenameFolder = { oldName, newName -> viewModel.renameFolder(oldName, newName) },
                            onDeleteFolder = { folder -> viewModel.deleteFolder(folder) },
                            onBack = { viewModel.navigateBack() }
                        )
                    }

                    NavDestination.FAVORITES -> {
                        FavoritesScreen(
                            notes = favoriteNotes,
                            onNoteClick = { viewModel.openNoteEditor(it) },
                            onToggleFavorite = { viewModel.toggleFavorite(it) },
                            onBack = { viewModel.navigateBack() }
                        )
                    }

                    NavDestination.TRASH -> {
                        TrashScreen(
                            notes = trashNotes,
                            onRestoreNote = { viewModel.restoreNote(it) },
                            onPermanentlyDeleteNote = { viewModel.permanentlyDeleteNote(it) },
                            onEmptyTrash = { viewModel.emptyTrash() },
                            onBack = { viewModel.navigateBack() }
                        )
                    }

                    NavDestination.DOCUMENTS -> {
                        DocumentsScreen(
                            documents = documents,
                            onImportDocument = { uri -> viewModel.importDocument(uri) },
                            onScanDeviceDocuments = { cb -> viewModel.scanDeviceDocuments(cb) },
                            onDocumentClick = { doc -> viewModel.openDocument(doc) },
                            onToggleFavorite = { id -> viewModel.toggleDocumentFavorite(id) },
                            onDeleteDocument = { id -> viewModel.softDeleteDocument(id) },
                            onPermanentlyDeleteDocument = { id -> viewModel.permanentlyDeleteDocument(id) },
                            onBack = { viewModel.navigateBack() }
                        )
                    }

                    NavDestination.PDF_READER -> {
                        selectedDocument?.let { doc ->
                            PdfReaderScreen(
                                document = doc,
                                onBack = { viewModel.navigateBack() },
                                onPageChanged = { page -> viewModel.updateDocumentPage(doc.id, page) },
                                onToggleFavorite = { id -> viewModel.toggleDocumentFavorite(id) }
                            )
                        } ?: run {
                            viewModel.navigateTo(NavDestination.DOCUMENTS)
                        }
                    }

                    NavDestination.SETTINGS -> {
                        SettingsScreen(
                            isDarkMode = isDarkMode,
                            onToggleDarkMode = { viewModel.setDarkMode(it) },
                            isAutoSave = isAutoSave,
                            onToggleAutoSave = { viewModel.setAutoSave(it) },
                            selectedFont = selectedFont,
                            onSelectFont = { viewModel.setSelectedFont(it) },
                            baseTextSize = baseTextSize,
                            onChangeBaseTextSize = { viewModel.setBaseTextSize(it) },
                            selectedSortOrder = defaultSortOrder,
                            onSelectSortOrder = { viewModel.setDefaultSortOrder(it) },
                            currentUser = currentUser,
                            syncState = syncState,
                            lastSyncTimestamp = lastSyncTimestamp,
                            lastSyncReport = lastSyncReport,
                            onSignInWithGoogle = { cb -> viewModel.signInWithGoogle(onResult = cb) },
                            onSignInWithEmail = { email, pass, cb -> viewModel.signInWithEmail(email, pass, cb) },
                            onSignUpWithEmail = { email, pass, name, cb -> viewModel.signUpWithEmail(email, pass, name, cb) },
                            onSignInAnonymously = { cb -> viewModel.signInAnonymously(cb) },
                            onSignOut = { viewModel.signOut() },
                            onSyncNow = { viewModel.syncNow() },
                            onExportBackup = { uri, cb -> viewModel.exportBackup(uri, cb) },
                            onRestoreBackup = { uri, cb -> viewModel.restoreBackup(uri, cb) },
                            onBack = { viewModel.navigateBack() }
                        )
                    }
                }
            }
        }

        // Custom rounded Navigation Sheet
        if (isNavigationSheetOpen) {
            NavigationSheet(
                currentDestination = currentDestination,
                notesCount = activeNotesCount,
                favoritesCount = favoritesCount,
                foldersCount = folders.size,
                documentsCount = activeDocumentsCount,
                trashCount = trashCount,
                isDarkMode = isDarkMode,
                onSelectDestination = { destination ->
                    viewModel.navigateTo(destination)
                },
                onToggleDarkMode = { viewModel.setDarkMode(it) },
                onDismiss = { viewModel.toggleNavigationSheet(false) }
            )
        }
    }
}

// Retained for screenshot tests
@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(text = "Hello $name!", modifier = modifier)
}

