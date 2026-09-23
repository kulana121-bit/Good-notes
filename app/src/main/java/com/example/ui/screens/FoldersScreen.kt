package com.example.ui.screens

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Folder
import com.example.data.model.Note
import com.example.ui.components.EmptyState
import com.example.ui.components.NoteCard
import com.example.ui.theme.NoteBlue
import com.example.ui.theme.NoteCoral
import com.example.ui.theme.NoteGreen
import com.example.ui.theme.NoteLavender
import com.example.ui.theme.NoteWarmCream
import com.example.ui.theme.NoteYellow
import com.example.ui.theme.OutfitFontFamily

@Composable
fun FoldersScreen(
    folders: List<Folder>,
    notes: List<Note>,
    onNoteClick: (Note) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onCreateFolder: (String, Color) -> Unit = { _, _ -> },
    onRenameFolder: (String, String) -> Unit = { _, _ -> },
    onDeleteFolder: (Folder) -> Unit = {},
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedFolder by remember { mutableStateOf<Folder?>(null) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var folderToRename by remember { mutableStateOf<Folder?>(null) }
    var folderToDelete by remember { mutableStateOf<Folder?>(null) }

    val filteredNotes = remember(selectedFolder, notes) {
        if (selectedFolder == null) {
            emptyList()
        } else {
            notes.filter { it.folder.equals(selectedFolder?.name, ignoreCase = true) }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                IconButton(
                    onClick = {
                        if (selectedFolder != null) {
                            selectedFolder = null
                        } else {
                            onBack()
                        }
                    },
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .testTag("folders_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Column {
                    Text(
                        text = selectedFolder?.name ?: "Folders",
                        style = MaterialTheme.typography.displayMedium.copy(
                            fontFamily = OutfitFontFamily,
                            fontSize = 30.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    )
                    Text(
                        text = if (selectedFolder != null) "${filteredNotes.size} notes" else "${folders.size} notebooks",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = OutfitFontFamily,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                        )
                    )
                }
            }

            if (selectedFolder == null) {
                // New Folder Button
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.primary)
                        .clickable { showCreateDialog = true }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                        .testTag("new_folder_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add Folder",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "New",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = OutfitFontFamily,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        )
                    }
                }
            } else {
                // Folder options in detail view
                var menuExpanded by remember { mutableStateOf(false) }
                Box {
                    IconButton(
                        onClick = { menuExpanded = true },
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .testTag("folder_menu_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.MoreVert,
                            contentDescription = "Folder options",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Rename Folder", fontFamily = OutfitFontFamily) },
                            leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                folderToRename = selectedFolder
                            }
                        )
                        if (!selectedFolder?.name.equals("Personal", ignoreCase = true)) {
                            DropdownMenuItem(
                                text = { Text("Delete Folder", fontFamily = OutfitFontFamily, color = NoteCoral) },
                                leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null, tint = NoteCoral) },
                                onClick = {
                                    menuExpanded = false
                                    folderToDelete = selectedFolder
                                }
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (selectedFolder == null) {
            // Folders Grid / List
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                items(folders, key = { it.id }) { folder ->
                    val folderNoteCount = notes.count { it.folder.equals(folder.name, ignoreCase = true) }
                    FolderCard(
                        folder = folder.copy(noteCount = folderNoteCount),
                        onClick = { selectedFolder = folder },
                        onRename = { folderToRename = folder },
                        onDelete = { folderToDelete = folder }
                    )
                }
            }
        } else {
            // Notes inside selected folder
            if (filteredNotes.isEmpty()) {
                EmptyState(
                    icon = Icons.Outlined.Folder,
                    title = "Empty Folder",
                    subtitle = "No notes currently saved in ${selectedFolder?.name}."
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(filteredNotes, key = { it.id }) { note ->
                        NoteCard(
                            note = note,
                            onClick = { onNoteClick(note) },
                            onToggleFavorite = onToggleFavorite,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }

    // Create Folder Dialog
    if (showCreateDialog) {
        var folderNameInput by remember { mutableStateOf("") }
        var selectedColor by remember { mutableStateOf(NoteCoral) }
        val colorOptions = listOf(NoteCoral, NoteYellow, NoteBlue, NoteGreen, NoteLavender, NoteWarmCream)

        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = {
                Text("New Notebook Folder", fontFamily = OutfitFontFamily, fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    OutlinedTextField(
                        value = folderNameInput,
                        onValueChange = { folderNameInput = it },
                        label = { Text("Folder Name", fontFamily = OutfitFontFamily) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("create_folder_input")
                    )

                    Text(
                        text = "Folder Color",
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = OutfitFontFamily)
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        colorOptions.forEach { col ->
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(col)
                                    .border(
                                        width = if (selectedColor == col) 2.5.dp else 0.dp,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        shape = CircleShape
                                    )
                                    .clickable { selectedColor = col }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (folderNameInput.isNotBlank()) {
                            onCreateFolder(folderNameInput.trim(), selectedColor)
                            showCreateDialog = false
                        }
                    },
                    modifier = Modifier.testTag("confirm_create_folder_button")
                ) {
                    Text("Create", fontFamily = OutfitFontFamily)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showCreateDialog = false }) {
                    Text("Cancel", fontFamily = OutfitFontFamily)
                }
            }
        )
    }

    // Rename Folder Dialog
    folderToRename?.let { folder ->
        var renameInput by remember { mutableStateOf(folder.name) }

        AlertDialog(
            onDismissRequest = { folderToRename = null },
            title = {
                Text("Rename Folder", fontFamily = OutfitFontFamily, fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "All notes in \"${folder.name}\" will be updated to the new name.",
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = OutfitFontFamily)
                    )
                    OutlinedTextField(
                        value = renameInput,
                        onValueChange = { renameInput = it },
                        label = { Text("New Name", fontFamily = OutfitFontFamily) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("rename_folder_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (renameInput.isNotBlank() && !renameInput.equals(folder.name, ignoreCase = true)) {
                            onRenameFolder(folder.name, renameInput.trim())
                            if (selectedFolder?.id == folder.id) {
                                selectedFolder = selectedFolder?.copy(name = renameInput.trim())
                            }
                            folderToRename = null
                        }
                    },
                    modifier = Modifier.testTag("confirm_rename_folder_button")
                ) {
                    Text("Rename", fontFamily = OutfitFontFamily)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { folderToRename = null }) {
                    Text("Cancel", fontFamily = OutfitFontFamily)
                }
            }
        )
    }

    // Delete Folder Dialog
    folderToDelete?.let { folder ->
        AlertDialog(
            onDismissRequest = { folderToDelete = null },
            title = {
                Text("Delete Folder?", fontFamily = OutfitFontFamily, fontWeight = FontWeight.Bold)
            },
            text = {
                Text(
                    text = "Notes in \"${folder.name}\" will NOT be deleted. They will be safely moved to \"Personal\".",
                    fontFamily = OutfitFontFamily
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteFolder(folder)
                        if (selectedFolder?.id == folder.id) {
                            selectedFolder = null
                        }
                        folderToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NoteCoral),
                    modifier = Modifier.testTag("confirm_delete_folder_button")
                ) {
                    Text("Delete Folder", color = Color.White, fontFamily = OutfitFontFamily)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { folderToDelete = null }) {
                    Text("Cancel", fontFamily = OutfitFontFamily)
                }
            }
        )
    }
}

@Composable
fun FolderCard(
    folder: Folder,
    onClick: () -> Unit,
    onRename: () -> Unit = {},
    onDelete: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(24.dp)
    var menuExpanded by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .shadow(4.dp, shape)
            .clip(shape)
            .background(folder.color)
            .clickable(onClick = onClick)
            .padding(22.dp)
            .testTag("folder_card_${folder.name.lowercase()}")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(Color(0x22000000)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Folder,
                        contentDescription = null,
                        tint = Color(0xFF141414),
                        modifier = Modifier.size(24.dp)
                    )
                }

                Column {
                    Text(
                        text = folder.name,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontFamily = OutfitFontFamily,
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF141414)
                        )
                    )
                    Text(
                        text = "${folder.noteCount} Notes",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = OutfitFontFamily,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0x99141414)
                        )
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0x22000000))
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = "View",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = OutfitFontFamily,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF141414)
                        )
                    )
                }

                Box {
                    IconButton(
                        onClick = { menuExpanded = true },
                        modifier = Modifier
                            .size(32.dp)
                            .testTag("folder_card_menu_${folder.name.lowercase()}")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.MoreVert,
                            contentDescription = "Options",
                            tint = Color(0xFF141414),
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Rename", fontFamily = OutfitFontFamily) },
                            leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onRename()
                            }
                        )
                        if (!folder.name.equals("Personal", ignoreCase = true)) {
                            DropdownMenuItem(
                                text = { Text("Delete", fontFamily = OutfitFontFamily, color = NoteCoral) },
                                leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null, tint = NoteCoral) },
                                onClick = {
                                    menuExpanded = false
                                    onDelete()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
