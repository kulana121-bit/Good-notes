package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.NoteAdd
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.model.Note
import com.example.ui.components.EmptyState
import com.example.ui.components.FilterPillRow
import com.example.ui.components.FloatingActionCapsule
import com.example.ui.components.NoteCard
import com.example.ui.components.NotesHeader
import com.example.ui.viewmodel.NotesFilter
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    notes: List<Note>,
    activeFilter: NotesFilter,
    onFilterSelected: (NotesFilter) -> Unit,
    onNoteClick: (Note) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onToggleChecklistItem: (String, String) -> Unit,
    onMenuClick: () -> Unit,
    onSearchClick: () -> Unit,
    onNewNoteClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

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

            // Bento / Masonry Note Cards
            if (filteredNotes.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Outlined.NoteAdd,
                        title = "No notes found",
                        subtitle = "Try selecting a different filter or tap + to create your first note.",
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
                        // Asymmetric Pair: Card 1 (Coral Task) & Card 2 (Yellow Media)
                        val coralNote = filteredNotes.firstOrNull { it.id == "note_1" } ?: filteredNotes.getOrNull(0)
                        val yellowNote = filteredNotes.firstOrNull { it.id == "note_2" } ?: filteredNotes.getOrNull(1)

                        if (coralNote != null || yellowNote != null) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                if (coralNote != null) {
                                    NoteCard(
                                        note = coralNote,
                                        onClick = { onNoteClick(coralNote) },
                                        onToggleFavorite = onToggleFavorite,
                                        onToggleChecklistItem = onToggleChecklistItem,
                                        modifier = Modifier.weight(1.08f)
                                    )
                                }
                                if (yellowNote != null) {
                                    NoteCard(
                                        note = yellowNote,
                                        onClick = { onNoteClick(yellowNote) },
                                        onToggleFavorite = onToggleFavorite,
                                        modifier = Modifier.weight(0.92f)
                                    )
                                }
                            }
                        }

                        // Wide Cream Lecture Card
                        val creamNote = filteredNotes.firstOrNull { it.id == "note_3" } ?: filteredNotes.firstOrNull { it.id == "note_4" }
                        if (creamNote != null && creamNote != coralNote && creamNote != yellowNote) {
                            NoteCard(
                                note = creamNote,
                                onClick = { onNoteClick(creamNote) },
                                onToggleFavorite = onToggleFavorite,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        // Remaining notes in pairs or wide cards
                        val remainingNotes = filteredNotes.filter {
                            it.id != coralNote?.id && it.id != yellowNote?.id && it.id != creamNote?.id
                        }

                        remainingNotes.chunked(2).forEach { chunk ->
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
                                        modifier = Modifier.weight(1f)
                                    )
                                    NoteCard(
                                        note = chunk[1],
                                        onClick = { onNoteClick(chunk[1]) },
                                        onToggleFavorite = onToggleFavorite,
                                        onToggleChecklistItem = onToggleChecklistItem,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            } else if (chunk.isNotEmpty()) {
                                NoteCard(
                                    note = chunk[0],
                                    onClick = { onNoteClick(chunk[0]) },
                                    onToggleFavorite = onToggleFavorite,
                                    onToggleChecklistItem = onToggleChecklistItem,
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
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("Voice notes recording ready in Phase 2")
                }
            },
            modifier = Modifier.align(Alignment.BottomCenter)
        )

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 90.dp)
        )
    }
}
