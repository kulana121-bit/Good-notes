package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.ColorLens
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Logout
import androidx.compose.material.icons.outlined.Nightlight
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.backup.RestoreResultSummary
import com.example.data.remote.auth.UserSummary
import com.example.data.sync.SyncReport
import com.example.data.sync.SyncState
import com.example.ui.theme.NoteMint
import com.example.ui.theme.NoteCoral
import com.example.ui.theme.NoteYellow
import com.example.ui.theme.OutfitFontFamily
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SettingsScreen(
    isDarkMode: Boolean,
    onToggleDarkMode: (Boolean) -> Unit,
    isAutoSave: Boolean = true,
    onToggleAutoSave: (Boolean) -> Unit = {},
    selectedFont: String = "Outfit (Editorial)",
    onSelectFont: (String) -> Unit = {},
    baseTextSize: Float = 16f,
    onChangeBaseTextSize: (Float) -> Unit = {},
    selectedSortOrder: String = "Recently Modified",
    onSelectSortOrder: (String) -> Unit = {},
    currentUser: UserSummary? = null,
    syncState: SyncState = SyncState.SYNCED,
    lastSyncTimestamp: Long = 0L,
    lastSyncReport: SyncReport? = null,
    onSignIn: () -> Unit = {},
    onSignOut: () -> Unit = {},
    onSyncNow: () -> Unit = {},
    onExportBackup: (Uri, (Result<Int>) -> Unit) -> Unit = { _, _ -> },
    onRestoreBackup: (Uri, (Result<RestoreResultSummary>) -> Unit) -> Unit = { _, _ -> },
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val exportBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri: Uri? ->
        if (uri != null) {
            onExportBackup(uri) { result ->
                coroutineScope.launch {
                    if (result.isSuccess) {
                        val count = result.getOrNull() ?: 0
                        snackbarHostState.showSnackbar("Backup exported successfully ($count items packaged)")
                    } else {
                        snackbarHostState.showSnackbar("Export failed: ${result.exceptionOrNull()?.localizedMessage ?: "Unknown error"}")
                    }
                }
            }
        }
    }

    val restoreBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            onRestoreBackup(uri) { result ->
                coroutineScope.launch {
                    if (result.isSuccess) {
                        val summary = result.getOrNull()
                        snackbarHostState.showSnackbar("Restored ${summary?.notesCount ?: 0} notes & ${summary?.documentsCount ?: 0} documents")
                    } else {
                        snackbarHostState.showSnackbar("Restore failed: ${result.exceptionOrNull()?.localizedMessage ?: "Invalid file"}")
                    }
                }
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .testTag("settings_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.size(16.dp))

                Column {
                    Text(
                        text = "Settings",
                        style = MaterialTheme.typography.displayMedium.copy(
                            fontFamily = OutfitFontFamily,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    )
                    Text(
                        text = "Preferences, Cloud Sync & Data",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = OutfitFontFamily,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                // Cloud Sync & Account Section
                SettingsSection(title = "Cloud Sync & Account", icon = Icons.Outlined.CloudSync) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        if (currentUser != null) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primary),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = currentUser.displayName?.firstOrNull()?.uppercase() ?: "U",
                                            style = MaterialTheme.typography.titleMedium.copy(
                                                fontFamily = OutfitFontFamily,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onPrimary
                                            )
                                        )
                                    }

                                    Column {
                                        Text(
                                            text = currentUser.displayName ?: "Authenticated User",
                                            style = MaterialTheme.typography.titleSmall.copy(
                                                fontFamily = OutfitFontFamily,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        )
                                        Text(
                                            text = currentUser.email ?: "Firebase Account",
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                fontFamily = OutfitFontFamily,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        )
                                    }
                                }

                                IconButton(
                                    onClick = onSignOut,
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Logout,
                                        contentDescription = "Sign Out",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Cloud Sync Offline",
                                        style = MaterialTheme.typography.titleSmall.copy(
                                            fontFamily = OutfitFontFamily,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    )
                                    Text(
                                        text = "Sign in to synchronize your notes across devices automatically.",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontFamily = OutfitFontFamily,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    )
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                Button(
                                    onClick = onSignIn,
                                    shape = RoundedCornerShape(16.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary
                                    ),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = "Sign In",
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontFamily = OutfitFontFamily,
                                            fontWeight = FontWeight.Bold
                                        )
                                    )
                                }
                            }
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 14.dp),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                        )

                        // Sync State row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                val syncStatusColor = when (syncState) {
                                    SyncState.SYNCED -> NoteMint
                                    SyncState.SYNCING -> NoteYellow
                                    SyncState.OFFLINE -> Color(0xFF888888)
                                    SyncState.SIGN_IN_REQUIRED -> NoteCoral
                                    SyncState.ERROR -> MaterialTheme.colorScheme.error
                                }

                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(syncStatusColor)
                                )

                                Column {
                                    Text(
                                        text = "Sync: ${syncState.label}",
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontFamily = OutfitFontFamily,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    )
                                    if (lastSyncTimestamp > 0) {
                                        val timeStr = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(lastSyncTimestamp))
                                        Text(
                                            text = "Last synced today at $timeStr",
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                fontFamily = OutfitFontFamily,
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        )
                                    }
                                }
                            }

                            Button(
                                onClick = onSyncNow,
                                enabled = syncState != SyncState.SYNCING,
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.surface,
                                    contentColor = MaterialTheme.colorScheme.onSurface
                                ),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                if (syncState == SyncState.SYNCING) {
                                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                                } else {
                                    Icon(imageVector = Icons.Outlined.Sync, contentDescription = null, modifier = Modifier.size(14.dp))
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Sync Now",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = OutfitFontFamily,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                            }
                        }
                    }
                }

                // Backup & Restore Section
                SettingsSection(title = "Backup & Archive", icon = Icons.Outlined.FileDownload) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Export and import your entire digital notebook library including all notes, folders, custom tags, and attached PDF documents in a self-contained ZIP archive.",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = OutfitFontFamily,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 18.sp
                            )
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = {
                                    val timestamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
                                    exportBackupLauncher.launch("notes_backup_$timestamp.zip")
                                },
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                ),
                                modifier = Modifier.weight(1f)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(imageVector = Icons.Outlined.FileUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Text(
                                        text = "Export ZIP",
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontFamily = OutfitFontFamily,
                                            fontWeight = FontWeight.Bold
                                        )
                                    )
                                }
                            }

                            OutlinedButton(
                                onClick = {
                                    restoreBackupLauncher.launch(arrayOf("application/zip", "application/octet-stream", "*/*"))
                                },
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(imageVector = Icons.Outlined.FileDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Text(
                                        text = "Restore Archive",
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontFamily = OutfitFontFamily,
                                            fontWeight = FontWeight.Bold
                                        )
                                    )
                                }
                            }
                        }
                    }
                }

                // Theme & Appearance Section
                SettingsSection(title = "Appearance", icon = Icons.Outlined.ColorLens) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = if (isDarkMode) Icons.Outlined.Nightlight else Icons.Outlined.WbSunny,
                                contentDescription = null,
                                tint = if (isDarkMode) NoteYellow else NoteCoral,
                                modifier = Modifier.size(22.dp)
                            )
                            Column {
                                Text(
                                    text = "Dark Mode",
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        fontFamily = OutfitFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                )
                                Text(
                                    text = if (isDarkMode) "Near-black editorial palette" else "Warm digital paper tones",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontFamily = OutfitFontFamily,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                )
                            }
                        }

                        Switch(
                            checked = isDarkMode,
                            onCheckedChange = onToggleDarkMode,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = NoteYellow,
                                checkedTrackColor = Color(0xFF222226),
                                uncheckedThumbColor = Color(0xFF141414),
                                uncheckedTrackColor = Color(0xFFE5DECE)
                            )
                        )
                    }
                }

                // Editor Settings
                SettingsSection(title = "Editor", icon = Icons.Outlined.EditNote) {
                    // Font Selector
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Text(
                            text = "Typography",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontFamily = OutfitFontFamily,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf("Outfit (Editorial)", "Serif", "Mono").forEach { fontName ->
                                val isSelected = fontName == selectedFont
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(
                                            if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                                        )
                                        .clickable { onSelectFont(fontName) }
                                        .padding(horizontal = 12.dp, vertical = 8.dp)
                                ) {
                                    Text(
                                        text = fontName,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontFamily = OutfitFontFamily,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                                        )
                                    )
                                }
                            }
                        }
                    }

                    // Text Size Slider
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Base Font Size",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontFamily = OutfitFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            )
                            Text(
                                text = "${baseTextSize.toInt()} sp",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = OutfitFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            )
                        }

                        Slider(
                            value = baseTextSize,
                            onValueChange = onChangeBaseTextSize,
                            valueRange = 14f..22f,
                            steps = 3,
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary
                            )
                        )
                    }

                    // Auto-save toggle
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Auto-save Drafts",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontFamily = OutfitFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            )
                            Text(
                                text = "Continuous state persistence",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = OutfitFontFamily,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                        }

                        Switch(
                            checked = isAutoSave,
                            onCheckedChange = onToggleAutoSave,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = NoteYellow,
                                checkedTrackColor = Color(0xFF222226)
                            )
                        )
                    }
                }

                // Notes Organization
                SettingsSection(title = "Notes & Sorting", icon = Icons.Outlined.Tune) {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Text(
                            text = "Default Sort Order",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontFamily = OutfitFontFamily,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        listOf("Recently Modified", "Alphabetical (A-Z)", "Important First").forEach { sort ->
                            val isSelected = sort == selectedSortOrder
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { onSelectSortOrder(sort) }
                                    .padding(vertical = 8.dp, horizontal = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = sort,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontFamily = OutfitFontFamily,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                )
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Outlined.Check,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // About & Craftsmanship
                SettingsSection(title = "About", icon = Icons.Outlined.Info) {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                        Text(
                            text = "NOTES v2.0.0",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontFamily = OutfitFontFamily,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Crafted with pure Jetpack Compose, edge-to-edge layout, tactile digital paper textures, native PDF rendering, full archive backup/restore, and Firebase Cloud sync.",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = OutfitFontFamily,
                                lineHeight = 18.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(40.dp))
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp)
        )
    }
}

@Composable
private fun SettingsSection(
    title: String,
    icon: ImageVector,
    content: @Composable () -> Unit
) {
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(start = 6.dp, bottom = 8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontFamily = OutfitFontFamily,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column {
                content()
            }
        }
    }
}
