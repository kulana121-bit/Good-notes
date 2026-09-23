package com.example.data.model

val InitialNotes = listOf(
    Note(
        id = "note_1",
        title = "Plan for The Day",
        cardType = VisualCardType.CORAL_TASK,
        folder = "Personal",
        updatedAtText = "Today",
        isFavorite = false,
        isTodo = true,
        isImportant = true,
        checklist = listOf(
            ChecklistItem("c1", "Buy food", isCompleted = true),
            ChecklistItem("c2", "GYM", isCompleted = false),
            ChecklistItem("c3", "Invest", isCompleted = false)
        )
    ),
    Note(
        id = "note_2",
        title = "Image Notes",
        body = "Curated visual references, palette exploration, and portrait aesthetics.",
        cardType = VisualCardType.YELLOW_MEDIA,
        folder = "Ideas",
        updatedAtText = "Update 2h ago",
        isFavorite = false,
        isTodo = false,
        isImportant = false
    ),
    Note(
        id = "note_3",
        title = "My Lectures",
        body = "Comprehensive course notes, interactive lecture slides, and weekly assignments.",
        cardType = VisualCardType.CREAM_LECTURE,
        folder = "Study",
        updatedAtText = "3d ago",
        isFavorite = false,
        isTodo = false,
        isImportant = false,
        noteCountText = "5 Notes"
    ),
    Note(
        id = "note_4",
        title = "Design Sprint Lecture",
        body = "Design Sprint is a way to quickly ideate, prototype, and validate a product idea in a week instead of waiting for months to launch a full-fledged product.\n\nDesign Sprint Phases:\n1. Understand & Empathize\n2. Define Problem Space\n3. Diverge & Sketch\n4. Decide & Storyboard\n5. Prototype & Test",
        cardType = VisualCardType.CREAM_LECTURE,
        folder = "Study",
        updatedAtText = "Yesterday",
        isFavorite = true,
        isTodo = false,
        isImportant = true,
        sharedWith = listOf("Ron", "Jack", "Sarah"),
        tags = listOf("Design", "Sprint", "Lecture")
    ),
    Note(
        id = "note_5",
        title = "Biology Revision",
        body = "Cell respiration, ATP synthesis pathways, enzymatic catalysts, and cellular mitosis.",
        cardType = VisualCardType.GREEN_NOTE,
        folder = "Study",
        updatedAtText = "4d ago",
        isFavorite = false,
        isTodo = false,
        isImportant = false,
        tags = listOf("Bio", "Revision")
    ),
    Note(
        id = "note_6",
        title = "Physics Formulas",
        body = "E = mc², Maxwell-Faraday equations, Navier-Stokes, and rotational kinetic energy.",
        cardType = VisualCardType.BLUE_NOTE,
        folder = "Study",
        updatedAtText = "1w ago",
        isFavorite = true,
        isTodo = false,
        isImportant = true,
        tags = listOf("Physics", "Formulas")
    ),
    Note(
        id = "note_7",
        title = "Quick Ideas",
        body = "Tactile paper UI interactions, physical page curls, ambient notebook haptics.",
        cardType = VisualCardType.LAVENDER_NOTE,
        folder = "Ideas",
        updatedAtText = "5m ago",
        isFavorite = false,
        isTodo = false,
        isImportant = false,
        tags = listOf("UI/UX", "Creative")
    ),
    Note(
        id = "note_8",
        title = "Important Tasks",
        cardType = VisualCardType.CORAL_TASK,
        folder = "Work",
        updatedAtText = "Today",
        isFavorite = true,
        isTodo = true,
        isImportant = true,
        checklist = listOf(
            ChecklistItem("t1", "Prepare sprint retrospective deck", isCompleted = true),
            ChecklistItem("t2", "Review architectural guidelines", isCompleted = false),
            ChecklistItem("t3", "Ship v1.0 design preview", isCompleted = false)
        )
    )
)
