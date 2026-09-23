package com.example.ui.screens

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.FitScreen
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.ZoomIn
import androidx.compose.material.icons.outlined.ZoomOut
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Document
import com.example.ui.theme.NoteYellow
import com.example.ui.theme.OutfitFontFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun PdfReaderScreen(
    document: Document,
    onBack: () -> Unit,
    onPageChanged: (pageIndex: Int) -> Unit = {},
    onToggleFavorite: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    var pfd by remember { mutableStateOf<ParcelFileDescriptor?>(null) }
    var renderer by remember { mutableStateOf<PdfRenderer?>(null) }
    var totalPages by remember { mutableStateOf(document.pageCount) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isFavorite by remember(document.isFavorite) { mutableStateOf(document.isFavorite) }

    // Page bitmaps cache
    val pageBitmaps = remember { mutableStateMapOf<Int, Bitmap>() }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = document.lastOpenedPage.coerceAtLeast(0))

    // Interactive zoom
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    // Current page indicator derived from scroll position
    val currentPageIndex by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex.coerceIn(0, (totalPages - 1).coerceAtLeast(0))
        }
    }

    LaunchedEffect(currentPageIndex) {
        onPageChanged(currentPageIndex)
    }

    // Open native PdfRenderer safely
    DisposableEffect(document.localPath) {
        val file = File(document.localPath)
        if (!file.exists() || !file.canRead()) {
            errorMessage = "Document file not found on local storage"
        } else {
            try {
                val openedPfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                val openedRenderer = PdfRenderer(openedPfd)
                pfd = openedPfd
                renderer = openedRenderer
                totalPages = openedRenderer.pageCount
            } catch (e: Exception) {
                errorMessage = "Unable to open PDF: ${e.localizedMessage ?: "Corrupted file"}"
            }
        }

        onDispose {
            pageBitmaps.values.forEach { if (!it.isRecycled) it.recycle() }
            pageBitmaps.clear()
            try {
                renderer?.close()
                pfd?.close()
            } catch (_: Exception) { }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .testTag("pdf_back_button")
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
                            text = document.displayName,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontFamily = OutfitFontFamily,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            ),
                            maxLines = 1
                        )
                        Text(
                            text = "Page ${currentPageIndex + 1} of $totalPages",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = OutfitFontFamily,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                            )
                        )
                    }
                }

                // Action controls
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Zoom In
                    IconButton(
                        onClick = { scale = (scale + 0.25f).coerceAtMost(3.0f) },
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ZoomIn,
                            contentDescription = "Zoom In",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Zoom Out
                    IconButton(
                        onClick = {
                            scale = (scale - 0.25f).coerceAtLeast(1.0f)
                            if (scale == 1.0f) {
                                offsetX = 0f
                                offsetY = 0f
                            }
                        },
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ZoomOut,
                            contentDescription = "Zoom Out",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Reset Fit
                    if (scale != 1.0f) {
                        IconButton(
                            onClick = {
                                scale = 1.0f
                                offsetX = 0f
                                offsetY = 0f
                            },
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.FitScreen,
                                contentDescription = "Fit Page",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    // Favorite toggle
                    IconButton(
                        onClick = {
                            isFavorite = !isFavorite
                            onToggleFavorite(document.id)
                        },
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Icon(
                            imageVector = if (isFavorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                            contentDescription = "Favorite",
                            tint = if (isFavorite) NoteYellow else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            if (errorMessage != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Cannot Display Document",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontFamily = OutfitFontFamily,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error
                            )
                        )
                        Text(
                            text = errorMessage ?: "",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontFamily = OutfitFontFamily,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                            )
                        )
                    }
                }
            } else {
                // PDF Pages List with Gestures
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                scale = (scale * zoom).coerceIn(1.0f, 3.5f)
                                if (scale > 1.0f) {
                                    offsetX += pan.x
                                    offsetY += pan.y
                                } else {
                                    offsetX = 0f
                                    offsetY = 0f
                                }
                            }
                        }
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            translationX = offsetX
                            translationY = offsetY
                        }
                ) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(count = totalPages, key = { it }) { pageIndex ->
                            val currentRenderer = renderer
                            var bitmap by remember(pageIndex) { mutableStateOf(pageBitmaps[pageIndex]) }

                            LaunchedEffect(pageIndex, currentRenderer) {
                                if (bitmap == null && currentRenderer != null) {
                                    withContext(Dispatchers.IO) {
                                        try {
                                            val page = currentRenderer.openPage(pageIndex)
                                            val targetWidth = 1080
                                            val targetHeight = (targetWidth * (page.height.toFloat() / page.width.toFloat())).toInt()
                                            val bmp = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
                                            bmp.eraseColor(android.graphics.Color.WHITE)
                                            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                            page.close()
                                            pageBitmaps[pageIndex] = bmp
                                            bitmap = bmp
                                        } catch (_: Exception) { }
                                    }
                                }
                            }

                            PdfPageCard(
                                pageIndex = pageIndex,
                                totalPages = totalPages,
                                bitmap = bitmap
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PdfPageCard(
    pageIndex: Int,
    totalPages: Int,
    bitmap: Bitmap?,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(12.dp)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .shadow(6.dp, shape)
            .clip(shape)
            .background(Color.White),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Column {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Page ${pageIndex + 1} of $totalPages",
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.707f) // Standard ISO A4 aspect ratio
                    .background(Color(0xFFF7F7F7)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(28.dp),
                        strokeWidth = 2.5.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Rendering page ${pageIndex + 1}...",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = OutfitFontFamily,
                            color = Color(0xFF666666)
                        )
                    )
                }
            }
        }
    }
}
