package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ChecklistItem
import com.example.data.model.Note
import com.example.ui.theme.MotionTokens
import com.example.ui.theme.NoteWarmCream
import com.example.ui.theme.NoteYellow
import com.example.ui.theme.OutfitFontFamily
import com.example.ui.theme.performTap
import com.example.ui.theme.rememberReducedMotion
import com.example.ui.viewmodel.SaveStatus
import kotlinx.coroutines.launch
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
    var title by remember(note.id) { mutableStateOf(note.title) }
    var body by remember(note.id) { mutableStateOf(note.body) }
    var checklist by remember(note.id) { mutableStateOf(note.checklist) }
    var newChecklistInput by remember { mutableStateOf("") }
    var showNewChecklistField by remember { mutableStateOf(false) }

    // Text formatting / selection demonstration state
    var isTextSelectionActive by remember { mutableStateOf(false) }
    var selectedFontSize by remember { mutableStateOf(16) }
    var isDoodleActive by remember { mutableStateOf(true) }

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    fun persist(t: String = title, b: String = body, c: List<ChecklistItem> = checklist) {
        val updated = note.copy(
            title = if (t.isBlank()) "Untitled Note" else t,
            body = b,
            checklist = c,
            updatedAtText = "Just now"
        )
        onContentChange?.invoke(updated) ?: onSave(updated)
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
            // Top App Bar: Back, Save Status, "Shared to" avatars, Delete, Share, Favorite
            EditorTopBar(
                isFavorite = note.isFavorite,
                saveStatus = saveStatus,
                sharedWith = note.sharedWith,
                onBack = {
                    persist()
                    onBack()
                },
                onDelete = {
                    onDeleteNote?.invoke(note.id)
                },
                onShare = {
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar("Sharing note link copied to clipboard")
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
                    .padding(horizontal = 28.dp, vertical = 8.dp)
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
                        fontSize = 32.sp,
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
                                        fontSize = 32.sp,
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

                Spacer(modifier = Modifier.height(16.dp))

                // Interactive Text Highlight Box (matches screenshot 4: "Design Sprint is a way...")
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isTextSelectionActive) NoteYellow else Color.Transparent)
                        .clickable { isTextSelectionActive = !isTextSelectionActive }
                        .padding(if (isTextSelectionActive) 8.dp else 0.dp)
                ) {
                    Text(
                        text = if (body.isNotEmpty()) body.take(150) else "Design Sprint is a way to quickly ideate, prototype, and validate a product idea in a week instead of waiting for months to launch a full-fledged product.",
                        style = TextStyle(
                            fontFamily = OutfitFontFamily,
                            fontSize = selectedFontSize.sp,
                            lineHeight = (selectedFontSize + 7).sp,
                            color = Color(0xFF141414),
                            fontWeight = FontWeight.Normal
                        )
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

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
                                    text = "Tap here to continue writing on digital paper...",
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

                Spacer(modifier = Modifier.height(24.dp))

                // Hand-Drawn Phase Diagram Section (matching reference screenshot 2 & 3)
                if (isDoodleActive) {
                    Text(
                        text = "Design Sprint Phases:",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontFamily = OutfitFontFamily,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF141414)
                        )
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Hand-drawn rounded pill "Empathize"
                        Box(
                            modifier = Modifier
                                .border(1.5.dp, Color(0xFF2C2C2C), RoundedCornerShape(24.dp))
                                .padding(horizontal = 18.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = "Empathize",
                                style = TextStyle(
                                    fontFamily = OutfitFontFamily,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color(0xFF141414)
                                )
                            )
                        }

                        // Tactile doodle heart & curved arrow Canvas
                        Canvas(modifier = Modifier.size(54.dp, 36.dp)) {
                            // Little hand-drawn heart
                            val heartPath = Path().apply {
                                moveTo(size.width * 0.25f, size.height * 0.4f)
                                cubicTo(size.width * 0.1f, size.height * 0.1f, size.width * 0.4f, size.height * 0.1f, size.width * 0.5f, size.height * 0.4f)
                                cubicTo(size.width * 0.6f, size.height * 0.1f, size.width * 0.9f, size.height * 0.1f, size.width * 0.75f, size.height * 0.4f)
                                lineTo(size.width * 0.5f, size.height * 0.8f)
                                close()
                            }
                            drawPath(
                                path = heartPath,
                                color = Color(0xFF333333),
                                style = Stroke(width = 1.5.dp.toPx())
                            )
                        }
                    }
                }

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
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Text(
                                    text = item.text,
                                    style = TextStyle(
                                        fontFamily = OutfitFontFamily,
                                        fontSize = 15.sp,
                                        textDecoration = if (item.isCompleted) TextDecoration.LineThrough else TextDecoration.None,
                                        color = if (item.isCompleted) Color(0x88141414) else Color(0xFF141414)
                                    )
                                )
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
                                    decorationBox = { inner ->
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .background(Color(0x11000000), RoundedCornerShape(8.dp))
                                                .padding(horizontal = 12.dp, vertical = 8.dp)
                                        ) {
                                            if (newChecklistInput.isEmpty()) {
                                                Text("Add new task...", color = Color(0x66141414))
                                            }
                                            inner()
                                        }
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

        // Floating Context Selection Bar (Cut, Copy, Share) inspired by screenshot 4
        AnimatedVisibility(
            visible = isTextSelectionActive,
            enter = fadeIn() + slideInVertically { it / 2 },
            exit = fadeOut() + slideOutVertically { it / 2 },
            modifier = Modifier
                .align(Alignment.Center)
                .padding(bottom = 120.dp)
        ) {
            FloatingSelectionToolbar(
                onCut = {
                    coroutineScope.launch { snackbarHostState.showSnackbar("Text cut to clipboard") }
                },
                onCopy = {
                    coroutineScope.launch { snackbarHostState.showSnackbar("Text copied") }
                },
                onShare = {
                    coroutineScope.launch { snackbarHostState.showSnackbar("Sharing selection") }
                }
            )
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
            // Text Formatting Keyboard Bar (inspired by screenshot 4)
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

            // Primary Floating Editor Toolbar (inspired by screenshots 2 & 3: +, Camera, Pen, Checklist)
            FloatingEditorToolbar(
                onAddContent = {
                    showNewChecklistField = true
                },
                onCamera = {
                    coroutineScope.launch { snackbarHostState.showSnackbar("Image scanner ready") }
                },
                onDraw = {
                    isDoodleActive = !isDoodleActive
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar(if (isDoodleActive) "Doodle layer visible" else "Doodle layer hidden")
                    }
                },
                onChecklist = {
                    showNewChecklistField = true
                },
                onFormat = {
                    isTextSelectionActive = !isTextSelectionActive
                }
            )
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
        // Circle Back Button + Subtle Save Status Indicator
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

            // Subtle save state
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

        // "Shared to" + Avatars + Delete Button + Share Button + Favorite
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (sharedWith.isNotEmpty()) {
                Text(
                    text = "Shared to",
                    style = TextStyle(
                        fontFamily = OutfitFontFamily,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xAA141414)
                    )
                )

                // Overlapping avatar cluster (matching screenshot 2 & 3)
                Row(horizontalArrangement = Arrangement.spacedBy((-8).dp)) {
                    sharedWith.take(3).forEachIndexed { index, name ->
                        val avatarColor = when (index % 3) {
                            0 -> Color(0xFF4A3E3D)
                            1 -> Color(0xFFD4A373)
                            else -> Color(0xFF2A6F97)
                        }
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .border(1.5.dp, NoteWarmCream, CircleShape)
                                .background(avatarColor),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = name.take(1),
                                style = TextStyle(
                                    fontFamily = OutfitFontFamily,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            )
                        }
                    }
                }
            }

            // Move to Trash (Soft Delete) icon in round button
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
                    contentDescription = "Delete note",
                    tint = Color(0xFF141414),
                    modifier = Modifier.size(18.dp)
                )
            }

            // Share icon in round button
            val shareInteraction = remember { MutableInteractionSource() }
            val isSharePressed by shareInteraction.collectIsPressedAsState()
            val shareScale by animateFloatAsState(
                targetValue = if (isSharePressed && !isReducedMotion) 0.88f else 1.0f,
                animationSpec = MotionTokens.subtlePressSpring(),
                label = "share_btn_scale"
            )

            Box(
                modifier = Modifier
                    .scale(shareScale)
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(Color(0x18000000))
                    .clickable(
                        interactionSource = shareInteraction,
                        indication = null,
                        onClick = {
                            haptics.performTap()
                            onShare()
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.IosShare,
                    contentDescription = "Share",
                    tint = Color(0xFF141414),
                    modifier = Modifier.size(18.dp)
                )
            }

            // Favorite toggle
            val favInteraction = remember { MutableInteractionSource() }
            val isFavPressed by favInteraction.collectIsPressedAsState()
            val favScale by animateFloatAsState(
                targetValue = if (isFavPressed && !isReducedMotion) 0.85f else if (isFavorite && !isReducedMotion) 1.05f else 1.0f,
                animationSpec = MotionTokens.bouncySpring(),
                label = "fav_btn_scale"
            )

            Box(
                modifier = Modifier
                    .scale(favScale)
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(Color(0x18000000))
                    .clickable(
                        interactionSource = favInteraction,
                        indication = null,
                        onClick = {
                            haptics.performTap()
                            onToggleFavorite()
                        }
                    )
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

// Floating Primary Editor Toolbar (screenshot 2 & 3: (+), Camera, Draw, Checklist)
@Composable
private fun FloatingEditorToolbar(
    onAddContent: () -> Unit,
    onCamera: () -> Unit,
    onDraw: () -> Unit,
    onChecklist: () -> Unit,
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
        // (+) Dominant dark circular button
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

        // Camera / Image
        EditorToolIcon(
            icon = Icons.Outlined.Image,
            contentDescription = "Image",
            onClick = onCamera
        )

        // Pen / Drawing
        EditorToolIcon(
            icon = Icons.Outlined.Edit,
            contentDescription = "Drawing",
            onClick = onDraw
        )

        // Checklist
        EditorToolIcon(
            icon = Icons.Outlined.Checklist,
            contentDescription = "Checklist",
            onClick = onChecklist
        )

        // Format toggle
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

// Contextual Cut, Copy, Share floating toolbar (screenshot 4)
@Composable
private fun FloatingSelectionToolbar(
    onCut: () -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit
) {
    Row(
        modifier = Modifier
            .shadow(12.dp, RoundedCornerShape(20.dp))
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xCCEFE2BD))
            .border(1.dp, Color(0x33000000), RoundedCornerShape(20.dp))
            .padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onCut, modifier = Modifier.size(32.dp)) {
            Icon(
                imageVector = Icons.Outlined.ContentCut,
                contentDescription = "Cut",
                tint = Color(0xFF141414),
                modifier = Modifier.size(18.dp)
            )
        }

        IconButton(onClick = onCopy, modifier = Modifier.size(32.dp)) {
            Icon(
                imageVector = Icons.Outlined.ContentCopy,
                contentDescription = "Copy",
                tint = Color(0xFF141414),
                modifier = Modifier.size(18.dp)
            )
        }

        IconButton(onClick = onShare, modifier = Modifier.size(32.dp)) {
            Icon(
                imageVector = Icons.Outlined.Share,
                contentDescription = "Share",
                tint = Color(0xFF141414),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

// Dark Keyboard / Formatting Accessory Bar (screenshot 4)
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

        // Circle style badge
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

        // Font Sizes: 14, 16, 18 (16 highlighted in yellow)
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
