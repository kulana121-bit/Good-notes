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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.ColorLens
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Logout
import androidx.compose.material.icons.outlined.Nightlight
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.backup.RestoreResultSummary
import com.example.data.remote.auth.UserSummary
import com.example.data.sync.SyncReport
import com.example.data.sync.SyncState
import com.example.ui.theme.NoteCoral
import com.example.ui.theme.NoteMint
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
    onSignInWithGoogle: ((Result<UserSummary>) -> Unit) -> Unit = {},
    onSignInWithEmail: (email: String, pass: String, (Result<UserSummary>) -> Unit) -> Unit = { _, _, _ -> },
    onSignUpWithEmail: (email: String, pass: String, name: String, (Result<UserSummary>) -> Unit) -> Unit = { _, _, _, _ -> },
    onSignInAnonymously: ((Result<UserSummary>) -> Unit) -> Unit = {},
    onSignOut: () -> Unit = {},
    onSyncNow: () -> Unit = {},
    onExportBackup: (Uri, (Result<Int>) -> Unit) -> Unit = { _, _ -> },
    onRestoreBackup: (Uri, (Result<RestoreResultSummary>) -> Unit) -> Unit = { _, _ -> },
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var isAuthDialogOpen by remember { mutableStateOf(false) }

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

                Spacer(modifier = Modifier.width(16.dp))

                Text(
                    text = "Settings",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontFamily = OutfitFontFamily,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                )
            }

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
                                            text = currentUser.email ?: if (currentUser.isAnonymous) "Guest Account" else "Cloud Account",
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
                                        text = "Sign in to synchronize your notes & drawings across devices automatically.",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontFamily = OutfitFontFamily,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    )
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                Button(
                                    onClick = { isAuthDialogOpen = true },
                                    shape = RoundedCornerShape(16.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary
                                    ),
                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
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

                        // Sync Status row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                val syncLabel = when (syncState) {
                                    SyncState.SYNCING -> "Syncing with Cloud..."
                                    SyncState.SYNCED -> "All changes synced"
                                    SyncState.OFFLINE -> "Working Offline"
                                    SyncState.SIGN_IN_REQUIRED -> "Sign in to enable sync"
                                    SyncState.ERROR -> "Sync attention needed"
                                }
                                Text(
                                    text = syncLabel,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontFamily = OutfitFontFamily,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                )
                                val lastSyncFormatted = if (lastSyncTimestamp > 0) {
                                    SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(Date(lastSyncTimestamp))
                                } else "Not synced yet"
                                Text(
                                    text = "Last: $lastSyncFormatted",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = OutfitFontFamily,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                )
                            }

                            OutlinedButton(
                                onClick = onSyncNow,
                                enabled = syncState != SyncState.SYNCING,
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                if (syncState == SyncState.SYNCING) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Outlined.Sync,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Sync Now", fontFamily = OutfitFontFamily, fontSize = 13.sp)
                                }
                            }
                        }
                    }
                }

                // Appearance & Reading Section
                SettingsSection(title = "Appearance & Reading", icon = Icons.Outlined.ColorLens) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        // Dark mode switch
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onToggleDarkMode(!isDarkMode) },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
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
                                        text = "Dark Theme",
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontFamily = OutfitFontFamily,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    )
                                    Text(
                                        text = if (isDarkMode) "Deep matte charcoal aesthetic" else "Warm paper editorial vibe",
                                        style = MaterialTheme.typography.labelSmall.copy(
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
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = MaterialTheme.colorScheme.primary
                                )
                            )
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 12.dp),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                        )

                        // Auto-Save Switch
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onToggleAutoSave(!isAutoSave) },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.EditNote,
                                    contentDescription = null,
                                    tint = NoteMint,
                                    modifier = Modifier.size(22.dp)
                                )
                                Column {
                                    Text(
                                        text = "Instant Auto-Save",
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontFamily = OutfitFontFamily,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    )
                                    Text(
                                        text = "Persist every keystroke in background",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontFamily = OutfitFontFamily,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    )
                                }
                            }

                            Switch(
                                checked = isAutoSave,
                                onCheckedChange = onToggleAutoSave,
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = MaterialTheme.colorScheme.primary
                                )
                            )
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 12.dp),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                        )

                        // Base Typography Size
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Base Note Font Size",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontFamily = OutfitFontFamily,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                )
                                Text(
                                    text = "${baseTextSize.toInt()} sp",
                                    style = MaterialTheme.typography.bodyMedium.copy(
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
                    }
                }

                // Backup & Migration Section
                SettingsSection(title = "Local Backup & Export", icon = Icons.Outlined.FileUpload) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Button(
                                onClick = {
                                    val dateStr = SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date())
                                    exportBackupLauncher.launch("notes_vault_backup_$dateStr.zip")
                                },
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurface),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Outlined.FileUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Export ZIP", fontFamily = OutfitFontFamily, fontSize = 13.sp)
                            }

                            Button(
                                onClick = { restoreBackupLauncher.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) },
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurface),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Outlined.FileDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Restore ZIP", fontFamily = OutfitFontFamily, fontSize = 13.sp)
                            }
                        }
                    }
                }

                // App Info & Version
                SettingsSection(title = "About", icon = Icons.Outlined.Info) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Notes & PDF Vault",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontFamily = OutfitFontFamily,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        )
                        Text(
                            text = "Version 1.2.0 • Editorial Design & Local PDF Storage",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = OutfitFontFamily,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(40.dp))
            }
        }

        // Comprehensive Authentication Dialog
        if (isAuthDialogOpen) {
            AuthModalDialog(
                onDismiss = { isAuthDialogOpen = false },
                onGoogleSignIn = {
                    onSignInWithGoogle { result ->
                        coroutineScope.launch {
                            if (result.isSuccess) {
                                isAuthDialogOpen = false
                                snackbarHostState.showSnackbar("Welcome, ${result.getOrNull()?.displayName ?: "User"}!")
                            } else {
                                snackbarHostState.showSnackbar("Google Sign-In: ${result.exceptionOrNull()?.localizedMessage ?: "Failed"}")
                            }
                        }
                    }
                },
                onEmailSignIn = { email, pass, onComplete ->
                    onSignInWithEmail(email, pass) { res ->
                        onComplete(res)
                        if (res.isSuccess) {
                            isAuthDialogOpen = false
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar("Signed in as ${res.getOrNull()?.displayName}!")
                            }
                        }
                    }
                },
                onEmailSignUp = { email, pass, name, onComplete ->
                    onSignUpWithEmail(email, pass, name) { res ->
                        onComplete(res)
                        if (res.isSuccess) {
                            isAuthDialogOpen = false
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar("Account created! Welcome, ${res.getOrNull()?.displayName}!")
                            }
                        }
                    }
                },
                onAnonymousSignIn = { onComplete ->
                    onSignInAnonymously { res ->
                        onComplete(res)
                        if (res.isSuccess) {
                            isAuthDialogOpen = false
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar("Signed in as Guest with Cloud Sync enabled!")
                            }
                        }
                    }
                }
            )
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp)
        )
    }
}

@Composable
private fun AuthModalDialog(
    onDismiss: () -> Unit,
    onGoogleSignIn: () -> Unit,
    onEmailSignIn: (email: String, pass: String, (Result<UserSummary>) -> Unit) -> Unit,
    onEmailSignUp: (email: String, pass: String, name: String, (Result<UserSummary>) -> Unit) -> Unit,
    onAnonymousSignIn: ((Result<UserSummary>) -> Unit) -> Unit
) {
    var isSignUpMode by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (isSignUpMode) "Create Cloud Account" else "Sign In to Cloud Sync",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontFamily = OutfitFontFamily,
                    fontWeight = FontWeight.Bold
                )
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Google One-Tap Quick Button
                Button(
                    onClick = onGoogleSignIn,
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF141414),
                        contentColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Sign In with Google One-Tap", fontFamily = OutfitFontFamily, fontWeight = FontWeight.Bold)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
                    Text(" or with email ", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
                }

                if (isSignUpMode) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it; errorMessage = null },
                        label = { Text("Display Name") },
                        leadingIcon = { Icon(Icons.Outlined.Person, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it; errorMessage = null },
                    label = { Text("Email Address") },
                    leadingIcon = { Icon(Icons.Outlined.Email, contentDescription = null) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it; errorMessage = null },
                    label = { Text("Password") },
                    leadingIcon = { Icon(Icons.Outlined.Lock, contentDescription = null) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (errorMessage != null) {
                    Text(
                        text = errorMessage ?: "",
                        style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.error)
                    )
                }

                // Guest Cloud Option
                TextButton(
                    onClick = {
                        isSubmitting = true
                        onAnonymousSignIn { res ->
                            isSubmitting = false
                            if (res.isFailure) {
                                errorMessage = res.exceptionOrNull()?.localizedMessage ?: "Guest login failed"
                            }
                        }
                    },
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Text("Continue as Guest (No password required)", style = MaterialTheme.typography.labelMedium)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (email.isBlank() || password.isBlank()) {
                        errorMessage = "Please enter email and password"
                        return@Button
                    }
                    isSubmitting = true
                    if (isSignUpMode) {
                        onEmailSignUp(email, password, name) { res ->
                            isSubmitting = false
                            if (res.isFailure) {
                                errorMessage = res.exceptionOrNull()?.localizedMessage ?: "Sign up failed"
                            }
                        }
                    } else {
                        onEmailSignIn(email, password) { res ->
                            isSubmitting = false
                            if (res.isFailure) {
                                errorMessage = res.exceptionOrNull()?.localizedMessage ?: "Sign in failed"
                            }
                        }
                    }
                },
                enabled = !isSubmitting,
                shape = RoundedCornerShape(12.dp)
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                } else {
                    Text(if (isSignUpMode) "Register" else "Log In", fontFamily = OutfitFontFamily, fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = { isSignUpMode = !isSignUpMode; errorMessage = null }) {
                Text(if (isSignUpMode) "Already have an account? Log In" else "Create an Account")
            }
        }
    )
}

@Composable
private fun SettingsSection(
    title: String,
    icon: ImageVector,
    content: @Composable () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(bottom = 8.dp)
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
                    color = MaterialTheme.colorScheme.primary
                )
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surface)
        ) {
            content()
        }
    }
}
