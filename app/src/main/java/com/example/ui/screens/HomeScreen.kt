package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.NoteAdd
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.data.model.Document
import com.example.data.model.Note
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import com.example.ui.components.EmptyState
import com.example.ui.components.FilterPillRow
import com.example.ui.components.FloatingActionCapsule
import com.example.ui.components.NoteCard
import com.example.ui.components.NotesHeader
import com.example.ui.theme.NoteCoral
import com.example.ui.theme.NoteLavender
import com.example.ui.theme.OutfitFontFamily
import com.example.ui.viewmodel.NotesFilter
import com.example.util.AudioRecorderManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun HomeScreen(
    notes: List<Note>,
    activeFilter: NotesFilter,
    onFilterSelected: (NotesFilter) -> Unit,
    onNoteClick: (Note) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onToggleChecklistItem: (String, String) -> Unit,
    onDeleteNote: (String) -> Unit,
    onMenuClick: () -> Unit,
    onSearchClick: () -> Unit,
    onNewNoteClick: () -> Unit,
    onSaveVoiceNote: (File, Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var isVoiceRecordingDialogVisible by remember { mutableStateOf(false) }
    val audioRecorder = remember { AudioRecorderManager() }
    var isRecordingActive by remember { mutableStateOf(false) }
    var recordingDurationSec by remember { mutableIntStateOf(0) }
    var recordedFile by remember { mutableStateOf<File?>(null) }
    var recordedDurationMs by remember { mutableLongStateOf(0L) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            isVoiceRecordingDialogVisible = true
            val result = audioRecorder.startRecording(context)
            if (result.isSuccess) {
                isRecordingActive = true
                recordedFile = result.getOrNull()
            } else {
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("Failed to start voice recorder: ${result.exceptionOrNull()?.message}")
                }
            }
        } else {
            coroutineScope.launch {
                snackbarHostState.showSnackbar("Microphone permission required for voice notes")
            }
        }
    }

    // Timer coroutine when recording
    LaunchedEffect(isRecordingActive) {
        if (isRecordingActive) {
            recordingDurationSec = 0
            while (isActive && isRecordingActive) {
                delay(1000)
                recordingDurationSec++
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            if (isRecordingActive) {
                audioRecorder.cancelRecording()
            }
        }
    }

    // Filter notes based on active filter
    val filteredNotes = remember(notes, activeFilter) {
        when (activeFilter) {
            NotesFilter.ALL -> notes
            NotesFilter.IMPORTANT -> notes.filter { it.isImportant || it.isFavorite }
            NotesFilter.TODO -> notes.filter { it.isTodo || it.checklist.isNotEmpty() }
            NotesFilter.RECENT -> notes.take(5)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("home_screen_list"),
            contentPadding = PaddingValues(bottom = 110.dp)
        ) {
            // Header with status bars padding
            item {
                Spacer(modifier = Modifier.statusBarsPadding())
                NotesHeader(
                    title = "My\nNotes",
                    onMenuClick = onMenuClick,
                    onSearchClick = onSearchClick
                )
            }

            // Horizontal Filter Pills Row
            item {
                FilterPillRow(
                    selectedFilter = activeFilter,
                    onFilterSelected = onFilterSelected,
                    totalNotesCount = notes.size
                )
                Spacer(modifier = Modifier.height(14.dp))
            }

            // Recent Documents
            if (documents.isNotEmpty() && activeFilter == NotesFilter.ALL) {
                item {
                    Column {
                        Text(
                            text = "Recent PDFs",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontFamily = OutfitFontFamily,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            ),
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                        )
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(documents.take(5), key = { it.id }) { doc ->
                                Box(
                                    modifier = Modifier
                                        .width(160.dp)
                                        .shadow(2.dp, RoundedCornerShape(16.dp))
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(doc.accentColor.copy(alpha = 0.3f))
                                        .clickable { onDocumentClick(doc) }
                                        .padding(16.dp)
                                ) {
                                    Column(
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(40.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(doc.accentColor),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = androidx.compose.material.icons.Icons.Outlined.PictureAsPdf,
                                                contentDescription = "PDF",
                                                tint = Color(0xFF141414),
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                        Text(
                                            text = doc.displayName,
                                            style = MaterialTheme.typography.titleSmall.copy(
                                                fontFamily = OutfitFontFamily,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            ),
                                            maxLines = 2
                                        )
                                        Text(
                                            text = "${doc.pageCount} ${if (doc.pageCount == 1) "page" else "pages"} • ${doc.lastOpenedAtFormatted}",
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                fontFamily = OutfitFontFamily,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        )
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }
            }

            // Bento / Masonry Note Cards
            if (filteredNotes.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Outlined.NoteAdd,
                        title = "No notes found",
                        subtitle = "Tap + to write a note or tap 🎙️ to record a voice note.",
                        actionLabel = "Create Note",
                        onActionClick = onNewNoteClick
                    )
                }
            } else {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        filteredNotes.chunked(2).forEach { chunk ->
                            if (chunk.size == 2) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    NoteCard(
                                        note = chunk[0],
                                        onClick = { onNoteClick(chunk[0]) },
                                        onToggleFavorite = onToggleFavorite,
                                        onToggleChecklistItem = onToggleChecklistItem,
                                        onDeleteNote = onDeleteNote,
                                        modifier = Modifier.weight(1f)
                                    )
                                    NoteCard(
                                        note = chunk[1],
                                        onClick = { onNoteClick(chunk[1]) },
                                        onToggleFavorite = onToggleFavorite,
                                        onToggleChecklistItem = onToggleChecklistItem,
                                        onDeleteNote = onDeleteNote,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            } else if (chunk.isNotEmpty()) {
                                NoteCard(
                                    note = chunk[0],
                                    onClick = { onNoteClick(chunk[0]) },
                                    onToggleFavorite = onToggleFavorite,
                                    onToggleChecklistItem = onToggleChecklistItem,
                                    onDeleteNote = onDeleteNote,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
            }
        }

        // Floating Action Capsule (+ and Microphone)
        FloatingActionCapsule(
            onAddClick = onNewNoteClick,
            onMicClick = {
                val hasPermission = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.RECORD_AUDIO
                ) == PackageManager.PERMISSION_GRANTED

                if (hasPermission) {
                    isVoiceRecordingDialogVisible = true
                    val result = audioRecorder.startRecording(context)
                    if (result.isSuccess) {
                        isRecordingActive = true
                        recordedFile = result.getOrNull()
                    } else {
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("Error opening recorder: ${result.exceptionOrNull()?.message}")
                        }
                    }
                } else {
                    permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            },
            modifier = Modifier.align(Alignment.BottomCenter)
        )

        // Voice Recording Bottom Sheet / Dialog
        AnimatedVisibility(
            visible = isVoiceRecordingDialogVisible,
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it },
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            val infiniteTransition = rememberInfiniteTransition(label = "pulse")
            val pulseScale by infiniteTransition.animateFloat(
                initialValue = 1f,
                targetValue = 1.25f,
                animationSpec = infiniteRepeatable(
                    animation = tween(800, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "mic_pulse"
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .shadow(16.dp, RoundedCornerShape(32.dp))
                    .clip(RoundedCornerShape(32.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(24.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isRecordingActive) "Recording Voice Note..." else "Voice Note Recorded",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontFamily = OutfitFontFamily,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        )

                        IconButton(
                            onClick = {
                                if (isRecordingActive) {
                                    audioRecorder.cancelRecording()
                                    isRecordingActive = false
                                }
                                isVoiceRecordingDialogVisible = false
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Outlined.Close,
                                contentDescription = "Close",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Pulsing animated microphone circle
                    Box(
                        modifier = Modifier
                            .size(90.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isRecordingActive) {
                            Box(
                                modifier = Modifier
                                    .size(80.dp)
                                    .scale(pulseScale)
                                    .clip(CircleShape)
                                    .background(NoteCoral.copy(alpha = 0.25f))
                            )
                        }

                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(if (isRecordingActive) NoteCoral else NoteLavender),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isRecordingActive) Icons.Filled.GraphicEq else Icons.Filled.Mic,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }

                    val mins = recordingDurationSec / 60
                    val secs = recordingDurationSec % 60
                    Text(
                        text = String.format("%02d:%02d", mins, secs),
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontFamily = OutfitFontFamily,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (isRecordingActive) {
                            Button(
                                onClick = {
                                    val dur = audioRecorder.stopRecording()
                                    recordedDurationMs = dur
                                    isRecordingActive = false
                                },
                                shape = RoundedCornerShape(20.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = NoteCoral),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Filled.Stop, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Stop Recording", fontFamily = OutfitFontFamily, fontWeight = FontWeight.Bold)
                            }
                        } else {
                            OutlinedButton(
                                onClick = {
                                    audioRecorder.cancelRecording()
                                    isVoiceRecordingDialogVisible = false
                                },
                                shape = RoundedCornerShape(20.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Discard", fontFamily = OutfitFontFamily)
                            }

                            Button(
                                onClick = {
                                    recordedFile?.let { file ->
                                        onSaveVoiceNote(file, recordedDurationMs)
                                        coroutineScope.launch {
                                            snackbarHostState.showSnackbar("Voice note saved!")
                                        }
                                    }
                                    isVoiceRecordingDialogVisible = false
                                },
                                shape = RoundedCornerShape(20.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Save Voice Note", fontFamily = OutfitFontFamily, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 90.dp)
        )
    }
}
