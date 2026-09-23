package com.example.data.local.converters

import androidx.compose.ui.graphics.Color
import com.example.data.local.entity.FolderEntity
import com.example.data.local.entity.NoteEntity
import com.example.data.model.ChecklistItem
import com.example.data.model.Folder
import com.example.data.model.Note
import com.example.data.model.VisualCardType
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object NoteMappers {
    private val moshi: Moshi = Moshi.Builder().build()

    private val checklistType = Types.newParameterizedType(List::class.java, ChecklistItem::class.java)
    private val checklistAdapter = moshi.adapter<List<ChecklistItem>>(checklistType)

    private val stringListType = Types.newParameterizedType(List::class.java, String::class.java)
    private val stringListAdapter = moshi.adapter<List<String>>(stringListType)

    fun toEntity(note: Note): NoteEntity {
        return NoteEntity(
            id = note.id,
            title = note.title,
            content = note.body,
            folder = note.folder,
            cardType = note.cardType.name,
            isFavorite = note.isFavorite,
            isDeleted = note.isDeleted,
            createdAt = note.createdAt,
            updatedAt = note.updatedAt,
            isTodo = note.isTodo,
            isImportant = note.isImportant,
            checklistJson = checklistAdapter.toJson(note.checklist),
            sharedWithJson = stringListAdapter.toJson(note.sharedWith),
            tagsJson = stringListAdapter.toJson(note.tags),
            noteCountText = note.noteCountText,
            imageUri = note.imageUri,
            audioUri = note.audioUri,
            audioDurationMs = note.audioDurationMs
        )
    }

    fun toDomain(entity: NoteEntity): Note {
        val checklist = try {
            checklistAdapter.fromJson(entity.checklistJson) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }

        val sharedWith = try {
            stringListAdapter.fromJson(entity.sharedWithJson) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }

        val tags = try {
            stringListAdapter.fromJson(entity.tagsJson) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }

        val cardType = try {
            VisualCardType.valueOf(entity.cardType)
        } catch (_: Exception) {
            VisualCardType.CREAM_LECTURE
        }

        return Note(
            id = entity.id,
            title = entity.title,
            body = entity.content,
            cardType = cardType,
            folder = entity.folder,
            updatedAtText = formatTimestamp(entity.updatedAt),
            isFavorite = entity.isFavorite,
            isTodo = entity.isTodo,
            isImportant = entity.isImportant,
            checklist = checklist,
            noteCountText = entity.noteCountText,
            sharedWith = sharedWith,
            tags = tags,
            isDeleted = entity.isDeleted,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt,
            imageUri = entity.imageUri,
            audioUri = entity.audioUri,
            audioDurationMs = entity.audioDurationMs
        )
    }

    fun toFolderEntity(folder: Folder): FolderEntity {
        return FolderEntity(
            id = folder.id,
            name = folder.name,
            colorHex = folder.color.value.toLong(),
            createdAt = System.currentTimeMillis()
        )
    }

    fun toDomainFolder(entity: FolderEntity, noteCount: Int): Folder {
        return Folder(
            id = entity.id,
            name = entity.name,
            noteCount = noteCount,
            color = Color(entity.colorHex.toULong())
        )
    }

    fun formatTimestamp(timestamp: Long): String {
        val now = System.currentTimeMillis()
        val diff = now - timestamp
        return when {
            diff < 60 * 1000 -> "Just now"
            diff < 60 * 60 * 1000 -> "${diff / (60 * 1000)}m ago"
            diff < 24 * 60 * 60 * 1000 -> "${diff / (60 * 60 * 1000)}h ago"
            diff < 48 * 60 * 60 * 1000 -> "Yesterday"
            diff < 7 * 24 * 60 * 60 * 1000 -> "${diff / (24 * 60 * 60 * 1000)}d ago"
            else -> SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(timestamp))
        }
    }
}
