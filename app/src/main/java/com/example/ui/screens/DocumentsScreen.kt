package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.example.data.model.Document
import com.example.ui.components.StaggeredAnimatedItem
import com.example.ui.theme.NoteCoral
import com.example.ui.theme.NoteYellow
import com.example.ui.theme.OutfitFontFamily
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun DocumentsScreen(
    documents: List<Document>,
    onImportDocument: (Uri) -> Unit,
    onScanDeviceDocuments: ((Int) -> Unit) -> Unit,
    onDocumentClick: (Document) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onDeleteDocument: (String) -> Unit,
    onPermanentlyDeleteDocument: (String) -> Unit,
    onDownloadDocument: (Document) -> Unit = {},
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var isScanning by remember { mutableStateOf(false) }
    var documentToDelete by remember { mutableStateOf<Document?>(null) }

    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            onImportDocument(uri)
            coroutineScope.launch {
                snackbarHostState.showSnackbar("PDF imported & scheduled for Google Drive sync")
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.values.any { it }
        if (granted) {
            isScanning = true
            onScanDeviceDocuments { count ->
                isScanning = false
                coroutineScope.launch {
                    snackbarHostState.showSnackbar(
                        if (count > 0) "Found and indexed $count PDF documents from device"
                        else "No new PDF documents discovered on device"
                    )
                }
            }
        } else {
            coroutineScope.launch {
                snackbarHostState.showSnackbar("Storage access permission required to scan device")
            }
        }
    }

    fun triggerDeviceScan() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasPerm = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED
            if (hasPerm) {
                isScanning = true
                onScanDeviceDocuments { count ->
                    isScanning = false
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar(
                            if (count > 0) "Found and indexed $count PDF documents from device"
                            else "No new PDF documents discovered on device"
                        )
                    }
                }
            } else {
                permissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.READ_MEDIA_IMAGES,
                        Manifest.permission.READ_MEDIA_VIDEO
                    )
                )
            }
        } else {
            val hasPerm = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
            if (hasPerm) {
                isScanning = true
                onScanDeviceDocuments { count ->
                    isScanning = false
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar(
                            if (count > 0) "Found and indexed $count PDF documents from device"
                            else "No new PDF documents discovered on device"
                        )
                    }
                }
            } else {
                permissionLauncher.launch(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE))
            }
        }
    }

    // Auto-scan on first entrance if the document library is empty
    LaunchedEffect(Unit) {
        if (documents.isEmpty()) {
            onScanDeviceDocuments {}
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
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .testTag("documents_back_button")
                            .size(44.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }

                    Column {
                        Text(
                            text = "PDF Documents",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontFamily = OutfitFontFamily,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        )
                        Text(
                            text = "${documents.size} ${if (documents.size == 1) "document" else "documents"} • Drive Sync",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = OutfitFontFamily,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                            )
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    // Rescan button
                    IconButton(
                        onClick = {
                            if (!isScanning) {
                                triggerDeviceScan()
                            }
                        },
                        modifier = Modifier.size(40.dp)
                    ) {
                        if (isScanning) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Outlined.Refresh,
                                contentDescription = "Scan Device for PDFs",
                                tint = MaterialTheme.colorScheme.onBackground
                            )
                        }
                    }

                    // Import button
                    Button(
                        onClick = {
                            pdfPickerLauncher.launch(arrayOf("application/pdf"))
                        },
                        shape = RoundedCornerShape(20.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.FileUpload,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Import PDF",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontFamily = OutfitFontFamily,
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Notice about app storage & Google Drive sync
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "📁 Documents are stored in app storage & synced with Google Drive (NOTES/Documents).",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = OutfitFontFamily,
                        fontSize = 11.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (documents.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(88.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.PictureAsPdf,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(44.dp)
                            )
                        }

                        Text(
                            text = "No documents yet",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontFamily = OutfitFontFamily,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        )

                        Text(
                            text = "Import a PDF file or scan your device to read, annotate, and sync directly with Google Drive.",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontFamily = OutfitFontFamily,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                            ),
                            modifier = Modifier.padding(horizontal = 24.dp)
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedButton(
                                onClick = { triggerDeviceScan() },
                                shape = RoundedCornerShape(20.dp)
                            ) {
                                Text("Scan Device", fontFamily = OutfitFontFamily)
                            }

                            Button(
                                onClick = {
                                    pdfPickerLauncher.launch(arrayOf("application/pdf"))
                                },
                                shape = RoundedCornerShape(20.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                )
                            ) {
                                Text(
                                    text = "Choose PDF File",
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        fontFamily = OutfitFontFamily,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                            }
                        }
                    }
                }
            } else {
                // Document List
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    itemsIndexed(documents, key = { _, doc -> doc.id }) { index, doc ->
                        StaggeredAnimatedItem(index = index) {
                            DocumentCardItem(
                                doc = doc,
                                onClick = { onDocumentClick(doc) },
                                onToggleFavorite = { onToggleFavorite(doc.id) },
                                onDownload = { onDownloadDocument(doc) },
                                onDelete = { documentToDelete = doc }
                            )
                        }
                    }
                }
            }
        }

        // Delete confirmation dialog
        documentToDelete?.let { doc ->
            AlertDialog(
                onDismissRequest = { documentToDelete = null },
                title = {
                    Text(
                        text = "Delete PDF Document?",
                        fontFamily = OutfitFontFamily,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Text(
                        text = "Are you sure you want to delete \"${doc.displayName}\"? This will permanently delete the document from local storage and Google Drive.",
                        fontFamily = OutfitFontFamily
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val id = doc.id
                            documentToDelete = null
                            onPermanentlyDeleteDocument(id)
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar("Document permanently deleted")
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = NoteCoral)
                    ) {
                        Text("Delete", color = Color.White, fontFamily = OutfitFontFamily)
                    }
                },
                dismissButton = {
                    OutlinedButton(onClick = { documentToDelete = null }) {
                        Text("Cancel", fontFamily = OutfitFontFamily)
                    }
                }
            )
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
fun DocumentCardItem(
    doc: Document,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onDownload: () -> Unit = {},
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(24.dp)
    var isMenuOpen by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .shadow(4.dp, shape)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.weight(1f)
            ) {
                // Document Thumbnail or Stylish Icon
                val hasThumb = doc.thumbnailPath != null && File(doc.thumbnailPath).exists()
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(doc.accentColor),
                    contentAlignment = Alignment.Center
                ) {
                    if (hasThumb) {
                        AsyncImage(
                            model = File(doc.thumbnailPath!!),
                            contentDescription = doc.displayName,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Outlined.PictureAsPdf,
                            contentDescription = "PDF",
                            tint = Color(0xFF141414),
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = doc.displayName,
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontFamily = OutfitFontFamily,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        maxLines = 1
                    )

                    Spacer(modifier = Modifier.height(3.dp))

                    Text(
                        text = "${doc.fileSizeFormatted} • ${doc.pageCount} ${if (doc.pageCount == 1) "page" else "pages"}",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = OutfitFontFamily,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )

                    Spacer(modifier = Modifier.height(3.dp))

                    // Sync & Drive status badge
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        when {
                            doc.downloadState == "DOWNLOADING" -> {
                                CircularProgressIndicator(modifier = Modifier.size(11.dp), strokeWidth = 1.5.dp)
                                Text(
                                    text = "Downloading...",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontFamily = OutfitFontFamily,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                )
                            }
                            doc.uploadState == "UPLOADING" -> {
                                CircularProgressIndicator(modifier = Modifier.size(11.dp), strokeWidth = 1.5.dp)
                                Text(
                                    text = "Uploading to Drive...",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontFamily = OutfitFontFamily,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                )
                            }
                            doc.isCloudOnly -> {
                                Icon(
                                    imageVector = Icons.Outlined.CloudDownload,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    text = "Available in Drive (Tap to download)",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontFamily = OutfitFontFamily,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                )
                            }
                            doc.uploadState == "FAILED" -> {
                                Icon(
                                    imageVector = Icons.Outlined.ErrorOutline,
                                    contentDescription = null,
                                    tint = NoteCoral,
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    text = "Upload failed",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontFamily = OutfitFontFamily,
                                        fontSize = 11.sp,
                                        color = NoteCoral
                                    )
                                )
                            }
                            doc.driveFileId != null -> {
                                Icon(
                                    imageVector = Icons.Outlined.CloudDone,
                                    contentDescription = null,
                                    tint = Color(0xFF558B2F),
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    text = "Drive Synced",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontFamily = OutfitFontFamily,
                                        fontSize = 11.sp,
                                        color = Color(0xFF558B2F)
                                    )
                                )
                            }
                            else -> {
                                Text(
                                    text = "Local document",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontFamily = OutfitFontFamily,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                    )
                                )
                            }
                        }
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                IconButton(
                    onClick = onToggleFavorite,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = if (doc.isFavorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                        contentDescription = "Favorite",
                        tint = if (doc.isFavorite) NoteYellow else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Box {
                    IconButton(
                        onClick = { isMenuOpen = true },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.MoreVert,
                            contentDescription = "More Options",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = isMenuOpen,
                        onDismissRequest = { isMenuOpen = false }
                    ) {
                        if (doc.isCloudOnly) {
                            DropdownMenuItem(
                                text = { Text("Download to Device", fontFamily = OutfitFontFamily) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Outlined.CloudDownload,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                },
                                onClick = {
                                    isMenuOpen = false
                                    onDownload()
                                }
                            )
                        }

                        DropdownMenuItem(
                            text = { Text("Open Reader", fontFamily = OutfitFontFamily) },
                            onClick = {
                                isMenuOpen = false
                                onClick()
                            }
                        )

                        DropdownMenuItem(
                            text = { Text("Delete Document", fontFamily = OutfitFontFamily, color = MaterialTheme.colorScheme.error) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Outlined.DeleteForever,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error
                                )
                            },
                            onClick = {
                                isMenuOpen = false
                                onDelete()
                            }
                        )
                    }
                }
            }
        }
    }
}
