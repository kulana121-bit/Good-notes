package com.example.ui.viewmodel

enum class NotesFilter(val label: String) {
    ALL("All"),
    IMPORTANT("Important"),
    TODO("To-do"),
    RECENT("Recent")
}

enum class NavDestination(val title: String) {
    HOME("My Notes"),
    ALL_NOTES("All Notes"),
    FOLDERS("Folders"),
    FAVORITES("Favorites"),
    DOCUMENTS("Documents"),
    SETTINGS("Settings"),
    TRASH("Trash"),
    NOTE_EDITOR("Note Editor"),
    PDF_READER("Document Reader")
}

enum class SaveStatus(val label: String) {
    SAVED("Saved"),
    SAVING("Saving...")
}
