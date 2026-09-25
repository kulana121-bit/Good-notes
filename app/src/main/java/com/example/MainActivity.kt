package com.example

import android.app.Application
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.theme.OutfitFontFamily
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.components.NavigationSheet
import com.example.ui.screens.DocumentsScreen
import com.example.ui.screens.FavoritesScreen
import com.example.ui.screens.FoldersScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.NoteEditorScreen
import com.example.ui.screens.OnboardingScreen
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
    val pendingOperationsCount by viewModel.pendingOperationsCount.collectAsState()

    val isDarkMode by viewModel.isDarkMode.collectAsState()
    val isAutoSave by viewModel.isAutoSave.collectAsState()
    val selectedFont by viewModel.selectedFont.collectAsState()
    val baseTextSize by viewModel.baseTextSize.collectAsState()
    val defaultSortOrder by viewModel.defaultSortOrder.collectAsState()
    val context = LocalContext.current

    val driveAuthState by viewModel.driveAuthState.collectAsState()
    val driveStorageInfo by viewModel.driveStorageInfo.collectAsState()
    val isDriveTesting by viewModel.isDriveTesting.collectAsState()
    val lastDriveTestReport by viewModel.lastDriveTestReport.collectAsState()

    val isAutoCloudBackup by viewModel.isAutoCloudBackup.collectAsState()
    val isCloudBackupWifiOnly by viewModel.isCloudBackupWifiOnly.collectAsState()
    val isCloudBackupIncludeDocs by viewModel.isCloudBackupIncludeDocs.collectAsState()
    val lastCloudBackupTimestamp by viewModel.lastCloudBackupTimestamp.collectAsState()
    val cloudBackupProgress by viewModel.cloudBackupProgress.collectAsState()
    val cloudRestoreProgress by viewModel.cloudRestoreProgress.collectAsState()
    val availableCloudBackup by viewModel.availableCloudBackup.collectAsState()
    val isOnboardingCompleted by viewModel.isOnboardingCompleted.collectAsState()

    val driveAuthLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        viewModel.handleDriveAuthResult(result.data) { authRes ->
            if (authRes.isSuccess) {
                Log.i("MainActivity", "Google Drive authorization successful")
            } else {
                Log.w("MainActivity", "Google Drive authorization failed: ${authRes.exceptionOrNull()?.message}")
            }
        }
    }

    val switchDriveAuthLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        viewModel.handleDriveAuthResult(result.data) { authRes ->
            if (authRes.isSuccess) {
                Log.i("MainActivity", "Google Drive account switch successful")
            } else {
                Log.w("MainActivity", "Google Drive account switch failed: ${authRes.exceptionOrNull()?.message}")
            }
        }
    }

    var isSearchActive by remember { mutableStateOf(false) }

    // Intercept hardware back button
    BackHandler(enabled = (isOnboardingCompleted != false) && (isSearchActive || currentDestination != NavDestination.HOME)) {
        if (isSearchActive) {
            isSearchActive = false
        } else {
            viewModel.navigateBack()
        }
    }

    if (isOnboardingCompleted == false) {
        OnboardingScreen(
            onComplete = { viewModel.completeOnboarding() }
        )
    } else {
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
                            documents = documents,
                            activeFilter = if (destination == NavDestination.ALL_NOTES) NotesFilter.ALL else activeFilter,
                            onFilterSelected = { viewModel.selectFilter(it) },
                            onNoteClick = { viewModel.openNoteEditor(it) },
                            onDocumentClick = { doc -> viewModel.openDocument(doc) },
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
                            },
                            syncState = syncState,
                            lastSyncTimestamp = lastSyncTimestamp,
                            onSyncClick = { viewModel.syncNow() }
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
                            onScanFolder = { treeUri, cb -> viewModel.scanFolder(treeUri, cb) },
                            onDocumentClick = { doc -> viewModel.openDocument(doc) },
                            onToggleFavorite = { id -> viewModel.toggleDocumentFavorite(id) },
                            onDeleteDocument = { id -> viewModel.softDeleteDocument(id) },
                            onPermanentlyDeleteDocument = { id -> viewModel.permanentlyDeleteDocument(id) },
                            onDownloadDocument = { doc -> viewModel.downloadDocument(doc) },
                            onBack = { viewModel.navigateBack() }
                        )
                    }

                    NavDestination.PDF_READER -> {
                        selectedDocument?.let { doc ->
                            PdfReaderScreen(
                                document = doc,
                                onBack = { viewModel.navigateBack() },
                                onPageChanged = { page -> viewModel.updateDocumentPage(doc.id, page) },
                                onToggleFavorite = { id -> viewModel.toggleDocumentFavorite(id) },
                                onDownloadDocument = { d -> viewModel.downloadDocument(d) }
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
                            pendingOperationsCount = pendingOperationsCount,
                            onSignInWithGoogle = { cb -> viewModel.signInWithGoogle(activityContext = context, onResult = cb) },
                            onSwitchGoogleAccount = { cb -> viewModel.switchGoogleAccount(activityContext = context, onResult = cb) },
                            onSignInWithEmail = { email, pass, cb -> viewModel.signInWithEmail(email, pass, cb) },
                            onSignUpWithEmail = { email, pass, name, cb -> viewModel.signUpWithEmail(email, pass, name, cb) },
                            onSignInAnonymously = { cb -> viewModel.signInAnonymously(cb) },
                            onSignOut = { viewModel.signOut() },
                            onSyncNow = { viewModel.syncNow() },
                            onExportBackup = { uri, cb -> viewModel.exportBackup(uri, cb) },
                            onRestoreBackup = { uri, cb -> viewModel.restoreBackup(uri, cb) },
                            driveAuthState = driveAuthState,
                            driveStorageInfo = driveStorageInfo,
                            isDriveTesting = isDriveTesting,
                            lastDriveTestReport = lastDriveTestReport,
                            onConnectDrive = {
                                try {
                                    driveAuthLauncher.launch(viewModel.getDriveAuthorizationIntent())
                                } catch (e: Exception) {
                                    Log.e("MainActivity", "Failed to launch Drive authorization intent", e)
                                }
                            },
                            onSwitchDriveAccount = {
                                try {
                                    switchDriveAuthLauncher.launch(viewModel.getSwitchDriveAccountIntent())
                                } catch (e: Exception) {
                                    Log.e("MainActivity", "Failed to launch Drive switch account intent", e)
                                }
                            },
                            onDisconnectDrive = { viewModel.disconnectDrive() },
                            onTestDriveConnection = { cb -> viewModel.testDriveConnection(cb) },
                            onRefreshDrive = { viewModel.refreshDriveStatus() },
                            isAutoCloudBackup = isAutoCloudBackup,
                            onToggleAutoCloudBackup = { viewModel.setAutoCloudBackup(it) },
                            isCloudBackupWifiOnly = isCloudBackupWifiOnly,
                            onToggleCloudBackupWifiOnly = { viewModel.setCloudBackupWifiOnly(it) },
                            isCloudBackupIncludeDocs = isCloudBackupIncludeDocs,
                            onToggleCloudBackupIncludeDocs = { viewModel.setCloudBackupIncludeDocs(it) },
                            lastCloudBackupTimestamp = lastCloudBackupTimestamp,
                            cloudBackupProgress = cloudBackupProgress,
                            cloudRestoreProgress = cloudRestoreProgress,
                            onBackupToCloudNow = { cb -> viewModel.backupToCloudNow(cb) },
                            onRestoreFromCloudNow = { cb -> viewModel.restoreFromCloudNow(cb) },
                            onBack = { viewModel.navigateBack() }
                        )
                    }
                }
            }
        }

        // Automatic Restore Offer for Fresh Install / Empty Database
        if (availableCloudBackup != null) {
            AlertDialog(
                onDismissRequest = { viewModel.dismissCloudBackupPrompt() },
                title = {
                    Text(
                        text = "Cloud backup found",
                        fontFamily = OutfitFontFamily,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Text(
                        text = "Your NOTES data (${availableCloudBackup?.notes?.size ?: 0} notes, ${availableCloudBackup?.folders?.size ?: 0} folders) is available from your Google Drive backup. Would you like to restore it now?",
                        fontFamily = OutfitFontFamily
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.restoreFromCloudNow()
                        },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Restore Notes", fontFamily = OutfitFontFamily)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.dismissCloudBackupPrompt() }) {
                        Text("Not Now", fontFamily = OutfitFontFamily)
                    }
                },
                shape = RoundedCornerShape(20.dp)
            )
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
}

// Retained for screenshot tests
@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(text = "Hello $name!", modifier = modifier)
}

