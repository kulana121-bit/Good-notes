package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.FormatAlignLeft
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.model.ChecklistItem
import com.example.data.model.Note
import com.example.ui.components.SketchPadModal
import com.example.ui.theme.MotionTokens
import com.example.ui.theme.NoteCoral
import com.example.ui.theme.NoteLavender
import com.example.ui.theme.NoteWarmCream
import com.example.ui.theme.NoteYellow
import com.example.ui.theme.OutfitFontFamily
import com.example.ui.theme.performTap
import com.example.ui.theme.rememberReducedMotion
import com.example.ui.viewmodel.SaveStatus
import com.example.util.AudioPlayerManager
import com.example.util.AudioRecorderManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

@Composable
fun NoteEditorScreen(
    note: Note,
    saveStatus: SaveStatus = SaveStatus.SAVED,
    onBack: () -> Unit,
    onSave: (Note) -> Unit,
    onContentChange: ((Note) -> Unit)? = null,
    onToggleFavorite: (String) -> Unit,
    onDeleteNote: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var title by remember(note.id) { mutableStateOf(note.title) }
    var body by remember(note.id) { mutableStateOf(note.body) }
    var checklist by remember(note.id) { mutableStateOf(note.checklist) }
    var attachedImageUri by remember(note.id) { mutableStateOf(note.imageUri) }
    var attachedAudioUri by remember(note.id) { mutableStateOf(note.audioUri) }
    var attachedAudioDurationMs by remember(note.id) { mutableStateOf(note.audioDurationMs) }

    var newChecklistInput by remember { mutableStateOf("") }
    var showNewChecklistField by remember { mutableStateOf(false) }

    // Text formatting / selection state
    var isTextSelectionActive by remember { mutableStateOf(false) }
    var selectedFontSize by remember { mutableStateOf(16) }
    var isSketchModalOpen by remember { mutableStateOf(false) }

    // Audio Player & Recorder
    val audioPlayer = remember { AudioPlayerManager(context) }
    val isPlaying by audioPlayer.isPlaying.collectAsState()
    val playPositionMs by audioPlayer.currentPositionMs.collectAsState()
    val audioDurationMs by audioPlayer.durationMs.collectAsState()

    val audioRecorder = remember { AudioRecorderManager() }
    var isRecordingActive by remember { mutableStateOf(false) }
    var recordingDurationSec by remember { mutableIntStateOf(0) }
    var isRecordingModalOpen by remember { mutableStateOf(false) }

    var isDeletedLocally by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    fun persist(
        t: String = title,
        b: String = body,
        c: List<ChecklistItem> = checklist,
        img: String? = attachedImageUri,
        aud: String? = attachedAudioUri,
        audDur: Long = attachedAudioDurationMs
    ) {
        if (isDeletedLocally) return
        val updated = note.copy(
            title = if (t.isBlank()) "Untitled Note" else t,
            body = b,
            checklist = c,
            imageUri = img,
            audioUri = aud,
            audioDurationMs = audDur,
            updatedAtText = "Just now"
        )
        onContentChange?.invoke(updated) ?: onSave(updated)
    }

    // Photo picker launcher
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            val uriStr = uri.toString()
            attachedImageUri = uriStr
            persist(img = uriStr)
            coroutineScope.launch {
                snackbarHostState.showSnackbar("Image attached to note")
            }
        }
    }

    // Mic permission launcher
    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            isRecordingModalOpen = true
            val res = audioRecorder.startRecording(context)
            if (res.isSuccess) {
                isRecordingActive = true
            }
        } else {
            coroutineScope.launch {
                snackbarHostState.showSnackbar("Microphone permission required")
            }
        }
    }

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
            audioPlayer.release()
            if (isRecordingActive) {
                audioRecorder.cancelRecording()
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(NoteWarmCream)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // Top App Bar
            EditorTopBar(
                isFavorite = note.isFavorite,
                saveStatus = saveStatus,
                sharedWith = note.sharedWith,
                onBack = {
                    if (!isDeletedLocally) {
                        // Check if entirely blank
                        if (title.isBlank() && body.isBlank() && checklist.isEmpty() && attachedImageUri.isNullOrBlank() && attachedAudioUri.isNullOrBlank()) {
                            onDeleteNote?.invoke(note.id)
                        } else {
                            persist()
                        }
                    }
                    onBack()
                },
                onDelete = {
                    isDeletedLocally = true
                    onDeleteNote?.invoke(note.id)
                },
                onShare = {
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar("Note content ready to share")
                    }
                },
                onToggleFavorite = {
                    onToggleFavorite(note.id)
                }
            )

            // Scrollable Digital Paper Body
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 8.dp)
            ) {
                // Large expressive Title Field
                BasicTextField(
                    value = title,
                    onValueChange = {
                        title = it
                        persist(t = it)
                    },
                    textStyle = TextStyle(
                        fontFamily = OutfitFontFamily,
                        fontSize = 30.sp,
                        lineHeight = 36.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF141414)
                    ),
                    cursorBrush = SolidColor(Color(0xFF141414)),
                    decorationBox = { innerTextField ->
                        Box {
                            if (title.isEmpty()) {
                                Text(
                                    text = "Note Title...",
                                    style = TextStyle(
                                        fontFamily = OutfitFontFamily,
                                        fontSize = 30.sp,
                                        lineHeight = 36.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0x55141414)
                                    )
                                )
                            }
                            innerTextField()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("note_title_input")
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Attached Image / Sketch Section
                if (!attachedImageUri.isNullOrBlank()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp)
                            .clip(RoundedCornerShape(20.dp))
                    ) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(attachedImageUri)
                                .crossfade(true)
                                .build(),
                            contentDescription = "Attached photo or sketch",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )

                        IconButton(
                            onClick = {
                                attachedImageUri = null
                                persist(img = null)
                            },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(8.dp)
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color(0x99000000))
                        ) {
                            Icon(
                                Icons.Outlined.Close,
                                contentDescription = "Remove attachment",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                }

                // Attached Audio / Voice Note Playback Section
                if (!attachedAudioUri.isNullOrBlank()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(NoteLavender.copy(alpha = 0.25f))
                            .padding(14.dp)
                    ) {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    IconButton(
                                        onClick = {
                                            attachedAudioUri?.let { uri ->
                                                audioPlayer.playOrToggle(uri)
                                            }
                                        },
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(NoteLavender)
                                    ) {
                                        Icon(
                                            imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                            contentDescription = "Play/Pause audio",
                                            tint = Color.White,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }

                                    Column {
                                        Text(
                                            text = "Voice Recording",
                                            style = TextStyle(
                                                fontFamily = OutfitFontFamily,
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF141414)
                                            )
                                        )
                                        val totalMs = if (audioDurationMs > 0) audioDurationMs.toLong() else attachedAudioDurationMs
                                        val durSec = (totalMs / 1000L).coerceAtLeast(1L)
                                        val curSec = (playPositionMs.toLong() / 1000L).coerceAtLeast(0L)
                                        Text(
                                            text = "$curSec s / $durSec s",
                                            style = TextStyle(
                                                fontFamily = OutfitFontFamily,
                                                fontSize = 12.sp,
                                                color = Color(0x99141414)
                                            )
                                        )
                                    }
                                }

                                IconButton(
                                    onClick = {
                                        audioPlayer.stop()
                                        attachedAudioUri = null
                                        attachedAudioDurationMs = 0L
                                        persist(aud = null, audDur = 0L)
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        Icons.Outlined.DeleteOutline,
                                        contentDescription = "Delete voice memo",
                                        tint = Color(0xFF141414)
                                    )
                                }
                            }

                            if (isPlaying || playPositionMs > 0) {
                                val totalDur = if (audioDurationMs > 0) audioDurationMs.toFloat() else attachedAudioDurationMs.toFloat().coerceAtLeast(1000f)
                                Slider(
                                    value = playPositionMs.toFloat().coerceIn(0f, totalDur),
                                    onValueChange = { audioPlayer.seekTo(it.toInt()) },
                                    valueRange = 0f..totalDur,
                                    colors = SliderDefaults.colors(
                                        thumbColor = NoteLavender,
                                        activeTrackColor = NoteLavender
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                }

                // Editable Body Text Field
                BasicTextField(
                    value = body,
                    onValueChange = {
                        body = it
                        persist(b = it)
                    },
                    textStyle = TextStyle(
                        fontFamily = OutfitFontFamily,
                        fontSize = selectedFontSize.sp,
                        lineHeight = (selectedFontSize + 7).sp,
                        color = Color(0xFF181818)
                    ),
                    cursorBrush = SolidColor(Color(0xFF141414)),
                    decorationBox = { innerTextField ->
                        Box {
                            if (body.isEmpty()) {
                                Text(
                                    text = "Start typing on digital paper...",
                                    style = TextStyle(
                                        fontFamily = OutfitFontFamily,
                                        fontSize = 15.sp,
                                        color = Color(0x66141414)
                                    )
                                )
                            }
                            innerTextField()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("note_body_input")
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Checklist Section
                if (checklist.isNotEmpty() || showNewChecklistField) {
                    Text(
                        text = "Checklist",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontFamily = OutfitFontFamily,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF141414)
                        )
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        checklist.forEach { item ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        checklist = checklist.map {
                                            if (it.id == item.id) it.copy(isCompleted = !it.isCompleted) else it
                                        }
                                        persist()
                                    }
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(22.dp)
                                        .clip(CircleShape)
                                        .background(if (item.isCompleted) Color(0xFF141414) else Color.Transparent)
                                        .border(2.dp, Color(0xFF141414), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (item.isCompleted) {
                                        Icon(
                                            imageVector = Icons.Outlined.Check,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(13.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Text(
                                    text = item.text,
                                    style = TextStyle(
                                        fontFamily = OutfitFontFamily,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Normal,
                                        color = if (item.isCompleted) Color(0x66141414) else Color(0xFF141414),
                                        textDecoration = if (item.isCompleted) TextDecoration.LineThrough else TextDecoration.None
                                    ),
                                    modifier = Modifier.weight(1f)
                                )

                                IconButton(
                                    onClick = {
                                        checklist = checklist.filter { it.id != item.id }
                                        persist()
                                    },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Close,
                                        contentDescription = "Delete item",
                                        tint = Color(0x88141414),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }

                        if (showNewChecklistField) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                BasicTextField(
                                    value = newChecklistInput,
                                    onValueChange = { newChecklistInput = it },
                                    textStyle = TextStyle(
                                        fontFamily = OutfitFontFamily,
                                        fontSize = 15.sp,
                                        color = Color(0xFF141414)
                                    ),
                                    cursorBrush = SolidColor(Color(0xFF141414)),
                                    modifier = Modifier
                                        .weight(1f)
                                        .background(Color(0x11000000), RoundedCornerShape(8.dp))
                                        .padding(8.dp),
                                    decorationBox = { inner ->
                                        if (newChecklistInput.isEmpty()) {
                                            Text(
                                                "New checklist item...",
                                                style = TextStyle(fontFamily = OutfitFontFamily, color = Color(0x66141414))
                                            )
                                        }
                                        inner()
                                    }
                                )

                                Spacer(modifier = Modifier.width(8.dp))

                                IconButton(
                                    onClick = {
                                        if (newChecklistInput.isNotBlank()) {
                                            checklist = checklist + ChecklistItem(
                                                id = UUID.randomUUID().toString().take(6),
                                                text = newChecklistInput
                                            )
                                            newChecklistInput = ""
                                            showNewChecklistField = false
                                            persist()
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = "Confirm",
                                        tint = Color(0xFF141414)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(120.dp))
            }
        }

        // Bottom Editor Accessories (Toolbars)
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .imePadding()
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Text Formatting Keyboard Bar
            AnimatedVisibility(
                visible = isTextSelectionActive,
                enter = fadeIn() + slideInVertically { it },
                exit = fadeOut() + slideOutVertically { it }
            ) {
                DarkFormattingAccessoryBar(
                    selectedSize = selectedFontSize,
                    onSelectSize = { selectedFontSize = it }
                )
            }

            // Primary Floating Editor Toolbar (+, Camera, Pen/Sketch, Checklist, Mic)
            FloatingEditorToolbar(
                onAddContent = {
                    showNewChecklistField = true
                },
                onCamera = {
                    photoPickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                onDraw = {
                    isSketchModalOpen = true
                },
                onChecklist = {
                    showNewChecklistField = true
                },
                onMic = {
                    val hasPerm = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.RECORD_AUDIO
                    ) == PackageManager.PERMISSION_GRANTED
                    if (hasPerm) {
                        isRecordingModalOpen = true
                        val res = audioRecorder.startRecording(context)
                        if (res.isSuccess) {
                            isRecordingActive = true
                        }
                    } else {
                        micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                },
                onFormat = {
                    isTextSelectionActive = !isTextSelectionActive
                }
            )
        }

        // Interactive Sketch Modal
        if (isSketchModalOpen) {
            SketchPadModal(
                onDismiss = { isSketchModalOpen = false },
                onSaveSketch = { sketchPath ->
                    attachedImageUri = sketchPath
                    persist(img = sketchPath)
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar("Sketch attached to note!")
                    }
                }
            )
        }

        // Voice Recording Modal inside Note Editor
        AnimatedVisibility(
            visible = isRecordingModalOpen,
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it },
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
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
                    Text(
                        text = if (isRecordingActive) "Recording Voice Note..." else "Recording Finished",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontFamily = OutfitFontFamily,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    )

                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(NoteCoral),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isRecordingActive) Icons.Filled.GraphicEq else Icons.Filled.Mic,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
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
                                    isRecordingActive = false
                                    val audioDir = File(context.filesDir, "voice_notes")
                                    val latestFile = audioDir.listFiles()?.maxByOrNull { it.lastModified() }
                                    if (latestFile != null) {
                                        attachedAudioUri = latestFile.absolutePath
                                        attachedAudioDurationMs = dur
                                        persist(aud = latestFile.absolutePath, audDur = dur)
                                    }
                                    isRecordingModalOpen = false
                                },
                                shape = RoundedCornerShape(20.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = NoteCoral),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Filled.Stop, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Stop & Attach", fontFamily = OutfitFontFamily, fontWeight = FontWeight.Bold)
                            }
                        } else {
                            OutlinedButton(
                                onClick = {
                                    audioRecorder.cancelRecording()
                                    isRecordingModalOpen = false
                                },
                                shape = RoundedCornerShape(20.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Discard", fontFamily = OutfitFontFamily)
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
                .padding(bottom = 80.dp)
        )
    }
}

@Composable
private fun EditorTopBar(
    isFavorite: Boolean,
    saveStatus: SaveStatus,
    sharedWith: List<String>,
    onBack: () -> Unit,
    onDelete: () -> Unit,
    onShare: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    val haptics = LocalHapticFeedback.current
    val isReducedMotion = rememberReducedMotion()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val backInteraction = remember { MutableInteractionSource() }
            val isBackPressed by backInteraction.collectIsPressedAsState()
            val backScale by animateFloatAsState(
                targetValue = if (isBackPressed && !isReducedMotion) 0.88f else 1.0f,
                animationSpec = MotionTokens.subtlePressSpring(),
                label = "back_btn_scale"
            )

            Box(
                modifier = Modifier
                    .scale(backScale)
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(Color(0x18000000))
                    .clickable(
                        interactionSource = backInteraction,
                        indication = null,
                        onClick = {
                            haptics.performTap()
                            onBack()
                        }
                    )
                    .testTag("editor_back_button"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = "Back",
                    tint = Color(0xFF161616),
                    modifier = Modifier.size(20.dp)
                )
            }

            Text(
                text = saveStatus.label,
                style = TextStyle(
                    fontFamily = OutfitFontFamily,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (saveStatus == SaveStatus.SAVING) Color(0xFFE08B3E) else Color(0x66141414)
                ),
                modifier = Modifier.testTag("editor_save_status")
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val deleteInteraction = remember { MutableInteractionSource() }
            val isDeletePressed by deleteInteraction.collectIsPressedAsState()
            val deleteScale by animateFloatAsState(
                targetValue = if (isDeletePressed && !isReducedMotion) 0.88f else 1.0f,
                animationSpec = MotionTokens.subtlePressSpring(),
                label = "delete_btn_scale"
            )

            Box(
                modifier = Modifier
                    .scale(deleteScale)
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(Color(0x18000000))
                    .clickable(
                        interactionSource = deleteInteraction,
                        indication = null,
                        onClick = {
                            haptics.performTap()
                            onDelete()
                        }
                    )
                    .testTag("editor_delete_button"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = "Move to Trash",
                    tint = Color(0xFF141414),
                    modifier = Modifier.size(18.dp)
                )
            }

            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(Color(0x18000000))
                    .clickable(onClick = {
                        haptics.performTap()
                        onShare()
                    }),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.IosShare,
                    contentDescription = "Share",
                    tint = Color(0xFF141414),
                    modifier = Modifier.size(18.dp)
                )
            }

            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(Color(0x18000000))
                    .clickable(onClick = {
                        haptics.performTap()
                        onToggleFavorite()
                    })
                    .testTag("editor_favorite_button"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                    contentDescription = "Favorite",
                    tint = if (isFavorite) Color(0xFFE11D48) else Color(0xFF141414),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun FloatingEditorToolbar(
    onAddContent: () -> Unit,
    onCamera: () -> Unit,
    onDraw: () -> Unit,
    onChecklist: () -> Unit,
    onMic: () -> Unit,
    onFormat: () -> Unit
) {
    val haptics = LocalHapticFeedback.current
    val isReducedMotion = rememberReducedMotion()
    val addInteraction = remember { MutableInteractionSource() }
    val isAddPressed by addInteraction.collectIsPressedAsState()
    val addScale by animateFloatAsState(
        targetValue = if (isAddPressed && !isReducedMotion) 0.88f else 1.0f,
        animationSpec = MotionTokens.subtlePressSpring(),
        label = "toolbar_add_scale"
    )

    Row(
        modifier = Modifier
            .shadow(
                elevation = 10.dp,
                shape = RoundedCornerShape(32.dp),
                ambientColor = Color(0x33000000)
            )
            .clip(RoundedCornerShape(32.dp))
            .background(Color(0xE6E8DEC0))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .scale(addScale)
                .size(42.dp)
                .clip(CircleShape)
                .background(Color(0xFF141414))
                .clickable(
                    interactionSource = addInteraction,
                    indication = null,
                    onClick = {
                        haptics.performTap()
                        onAddContent()
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Add Content",
                tint = Color.White,
                modifier = Modifier.size(22.dp)
            )
        }

        EditorToolIcon(
            icon = Icons.Outlined.Image,
            contentDescription = "Attach Photo",
            onClick = onCamera
        )

        EditorToolIcon(
            icon = Icons.Filled.Mic,
            contentDescription = "Record Voice Note",
            onClick = onMic
        )

        EditorToolIcon(
            icon = Icons.Outlined.Edit,
            contentDescription = "Draw Sketch",
            onClick = onDraw
        )

        EditorToolIcon(
            icon = Icons.Outlined.Checklist,
            contentDescription = "Checklist",
            onClick = onChecklist
        )

        EditorToolIcon(
            icon = Icons.AutoMirrored.Outlined.FormatAlignLeft,
            contentDescription = "Format",
            onClick = onFormat
        )
    }
}

@Composable
private fun EditorToolIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit
) {
    val haptics = LocalHapticFeedback.current
    val isReducedMotion = rememberReducedMotion()
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed && !isReducedMotion) 0.86f else 1.0f,
        animationSpec = MotionTokens.subtlePressSpring(),
        label = "tool_icon_scale"
    )

    Box(
        modifier = Modifier
            .scale(scale)
            .size(38.dp)
            .clip(CircleShape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {
                    haptics.performTap()
                    onClick()
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = Color(0xFF2C2C2C),
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun DarkFormattingAccessoryBar(
    selectedSize: Int,
    onSelectSize: (Int) -> Unit
) {
    Row(
        modifier = Modifier
            .shadow(12.dp, RoundedCornerShape(24.dp))
            .clip(RoundedCornerShape(24.dp))
            .background(Color(0xFF1A1A1D))
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Aa",
            style = TextStyle(
                fontFamily = OutfitFontFamily,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        )

        Box(
            modifier = Modifier
                .size(18.dp)
                .border(1.5.dp, Color.White, CircleShape)
        )

        Icon(
            imageVector = Icons.AutoMirrored.Outlined.FormatAlignLeft,
            contentDescription = "Align",
            tint = Color(0xFFBBBBBB),
            modifier = Modifier.size(18.dp)
        )

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf(14, 16, 18).forEach { size ->
                val isCurrent = size == selectedSize
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { onSelectSize(size) }
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "$size",
                        style = TextStyle(
                            fontFamily = OutfitFontFamily,
                            fontSize = 14.sp,
                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                            color = if (isCurrent) NoteYellow else Color(0xFF888888)
                        )
                    )
                }
            }
        }
    }
}
