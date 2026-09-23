package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = NoteYellow,
    onPrimary = NoteDark,
    primaryContainer = NoteDarkCard,
    onPrimaryContainer = NoteYellow,
    secondary = NoteCoral,
    onSecondary = Color.White,
    tertiary = NoteBlue,
    onTertiary = Color.White,
    background = NoteDark,
    onBackground = NoteTextLight,
    surface = NoteDarkSurface,
    onSurface = NoteTextLight,
    surfaceVariant = NoteDarkCard,
    onSurfaceVariant = NoteTextLightMuted,
    outline = NoteDarkBorder,
    outlineVariant = NoteDarkBorder.copy(alpha = 0.5f)
)

private val LightColorScheme = lightColorScheme(
    primary = NoteDark,
    onPrimary = Color.White,
    primaryContainer = NoteWarmCream,
    onPrimaryContainer = NoteTextDark,
    secondary = NoteCoral,
    onSecondary = Color.White,
    tertiary = NoteBlue,
    onTertiary = Color.White,
    background = NotePaperBackground,
    onBackground = NoteTextDark,
    surface = NotePaperSurface,
    onSurface = NoteTextDark,
    surfaceVariant = NoteWarmCream,
    onSurfaceVariant = NoteTextDarkMuted,
    outline = NoteLightBorder,
    outlineVariant = NoteLightBorder.copy(alpha = 0.5f)
)

@Composable
fun NotesTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = NotesTypography,
        content = content
    )
}

// Backward-compatible alias for unit tests
@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    NotesTheme(darkTheme = darkTheme, content = content)
}
