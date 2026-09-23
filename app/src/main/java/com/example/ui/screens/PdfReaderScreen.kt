package com.example.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Brightness4
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.FitScreen
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Pageview
import androidx.compose.material.icons.outlined.RotateRight
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material.icons.outlined.ZoomIn
import androidx.compose.material.icons.outlined.ZoomOut
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Document
import com.example.ui.components.glassmorphism
import com.example.ui.theme.NoteCoral
import com.example.ui.theme.NoteYellow
import com.example.ui.theme.OutfitFontFamily
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

enum class PdfReadingMode {
    LIGHT_PAPER,
    SEPIA_WARMTH,
    DARK_NIGHT
}

@Composable
fun PdfReaderScreen(
    document: Document,
    onBack: () -> Unit,
    onPageChanged: (Int) -> Unit = {},
    onToggleFavorite: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val configuration = LocalConfiguration.current
    val screenWidthPx = with(LocalDensity.current) { configuration.screenWidthDp.dp.roundToPx() }

    var totalPages by remember { mutableIntStateOf(document.pageCount.coerceAtLeast(1)) }
    var scale by remember { mutableFloatStateOf(1.0f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    var rotationDegrees by remember { mutableIntStateOf(0) }
    var isFavorite by remember { mutableStateOf(document.isFavorite) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    var isControlsVisible by remember { mutableStateOf(true) }
    var isThumbnailsVisible by remember { mutableStateOf(false) }
    var isJumpDialogVisible by remember { mutableStateOf(false) }
    var readingMode by remember { mutableStateOf(PdfReadingMode.LIGHT_PAPER) }

    val listState = rememberLazyListState(initialFirstVisibleItemIndex = document.lastOpenedPage)
    val pageBitmaps = remember { mutableStateMapOf<Int, Bitmap>() }
    val thumbnailBitmaps = remember { mutableStateMapOf<Int, Bitmap>() }
    val renderMutex = remember { Mutex() }

    var pfd by remember { mutableStateOf<ParcelFileDescriptor?>(null) }
    var renderer by remember { mutableStateOf<PdfRenderer?>(null) }

    val currentPageIndex by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex.coerceIn(0, (totalPages - 1).coerceAtLeast(0))
        }
    }

    LaunchedEffect(currentPageIndex) {
        onPageChanged(currentPageIndex)
    }

    // Dynamic resolution scaling: whenever the user zooms in significantly and releases/settles,
    // re-render the active page at high DPI so the vector/text is razor sharp without pixelation!
    var highResScale by remember { mutableFloatStateOf(1.0f) }
    LaunchedEffect(scale) {
        // Debounce zoom gesture end
        delay(250)
        highResScale = scale.coerceIn(1.0f, 3.5f)
    }

    // Open native PdfRenderer safely for either file path or content:// Uri
    DisposableEffect(document.localPath) {
        isLoading = true
        errorMessage = null
        try {
            val openedPfd: ParcelFileDescriptor? = if (document.localPath.startsWith("content://")) {
                context.contentResolver.openFileDescriptor(Uri.parse(document.localPath), "r")
            } else {
                val file = File(document.localPath)
                if (file.exists() && file.canRead()) {
                    ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                } else {
                    null
                }
            }

            if (openedPfd == null) {
                errorMessage = "Document file not accessible or moved from device storage."
                isLoading = false
            } else {
                val openedRenderer = PdfRenderer(openedPfd)
                pfd = openedPfd
                renderer = openedRenderer
                totalPages = openedRenderer.pageCount
                isLoading = false
            }
        } catch (e: Exception) {
            errorMessage = "Unable to open PDF: ${e.localizedMessage ?: "Protected or corrupted file"}"
            isLoading = false
        }

        onDispose {
            pageBitmaps.values.forEach { bmp ->
                try {
                    if (!bmp.isRecycled) bmp.recycle()
                } catch (_: Exception) { }
            }
            pageBitmaps.clear()
            thumbnailBitmaps.values.forEach { bmp ->
                try {
                    if (!bmp.isRecycled) bmp.recycle()
                } catch (_: Exception) { }
            }
            thumbnailBitmaps.clear()
            try {
                renderer?.close()
                pfd?.close()
            } catch (_: Exception) { }
        }
    }

    // Smart bounded memory cache for low-end mobile: Prune distant page bitmaps (> 2 pages away)
    LaunchedEffect(currentPageIndex) {
        val keysToRemove = pageBitmaps.keys.filter { Math.abs(it - currentPageIndex) > 2 }
        keysToRemove.forEach { key ->
            pageBitmaps[key]?.let { bmp ->
                try {
                    if (!bmp.isRecycled) bmp.recycle()
                } catch (_: Exception) { }
            }
            pageBitmaps.remove(key)
        }
    }

    // Color filter for night reading and sepia mode
    val activeColorFilter = remember(readingMode) {
        when (readingMode) {
            PdfReadingMode.LIGHT_PAPER -> null
            PdfReadingMode.DARK_NIGHT -> {
                // Invert luminance while preserving contrast
                val matrix = ColorMatrix(
                    floatArrayOf(
                        -0.9f,  0.0f,  0.0f, 0.0f, 240f,
                         0.0f, -0.9f,  0.0f, 0.0f, 240f,
                         0.0f,  0.0f, -0.9f, 0.0f, 240f,
                         0.0f,  0.0f,  0.0f, 1.0f,   0f
                    )
                )
                ColorFilter.colorMatrix(matrix)
            }
            PdfReadingMode.SEPIA_WARMTH -> {
                // Warm eye-comfort sepia filter
                val matrix = ColorMatrix(
                    floatArrayOf(
                        0.393f * 1.1f, 0.769f * 1.1f, 0.189f * 1.1f, 0f, 15f,
                        0.349f * 1.05f, 0.686f * 1.05f, 0.168f * 1.05f, 0f, 10f,
                        0.272f * 0.9f, 0.534f * 0.9f, 0.131f * 0.9f, 0f, 0f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
                ColorFilter.colorMatrix(matrix)
            }
        }
    }

    val backgroundColor = when (readingMode) {
        PdfReadingMode.LIGHT_PAPER -> MaterialTheme.colorScheme.background
        PdfReadingMode.SEPIA_WARMTH -> Color(0xFFF4ECD8)
        PdfReadingMode.DARK_NIGHT -> Color(0xFF0F0F12)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundColor)
    ) {
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(40.dp)
                )
            }
        } else if (errorMessage != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ErrorOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(52.dp)
                    )
                    Text(
                        text = "Cannot Display PDF",
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
            // Main Google Drive-style zoomable canvas
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = {
                                isControlsVisible = !isControlsVisible
                            },
                            onDoubleTap = { tapOffset ->
                                if (scale > 1.2f) {
                                    scale = 1.0f
                                    offsetX = 0f
                                    offsetY = 0f
                                } else {
                                    scale = 2.5f
                                    // Center zoom near tap
                                    offsetX = (screenWidthPx / 2f - tapOffset.x) * 1.2f
                                    offsetY = 0f
                                }
                            }
                        )
                    }
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(0.75f, 5.0f)
                            if (scale > 1.0f) {
                                val maxOffsetX = screenWidthPx * (scale - 1f) / 1.5f
                                offsetX = (offsetX + pan.x).coerceIn(-maxOffsetX, maxOffsetX)
                                offsetY = (offsetY + pan.y).coerceIn(-1200f, 1200f)
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
                        rotationZ = rotationDegrees.toFloat()
                    }
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 80.dp,
                        bottom = if (isThumbnailsVisible) 180.dp else 120.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    items(count = totalPages, key = { it }) { pageIndex ->
                        val currentRenderer = renderer
                        var bitmap by remember(pageIndex) { mutableStateOf(pageBitmaps[pageIndex]) }

                        // High-DPI Lossless Dynamic Render on scale change
                        LaunchedEffect(pageIndex, currentRenderer, highResScale) {
                            if (currentRenderer != null) {
                                withContext(Dispatchers.IO) {
                                    try {
                                        renderMutex.withLock {
                                            val page = currentRenderer.openPage(pageIndex)
                                            // Dynamic quality: base 1080px multiplied by zoom factor (up to 2700px)
                                            // for crystal-clear vector text without memory blowout
                                            val baseWidth = (screenWidthPx * 1.5f).toInt().coerceIn(720, 1080)
                                            val targetWidth = (baseWidth * highResScale.coerceIn(1.0f, 2.5f)).toInt()
                                            val ratio = page.height.toFloat() / page.width.toFloat()
                                            val targetHeight = (targetWidth * ratio).toInt().coerceAtLeast(1)

                                            val bmp = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
                                            bmp.eraseColor(android.graphics.Color.WHITE)
                                            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                            page.close()

                                            pageBitmaps[pageIndex]?.let { old ->
                                                if (old != bmp && !old.isRecycled) old.recycle()
                                            }
                                            pageBitmaps[pageIndex] = bmp
                                            bitmap = bmp
                                        }
                                    } catch (_: Exception) { }
                                }
                            }
                        }

                        PdfPageCard(
                            pageIndex = pageIndex,
                            totalPages = totalPages,
                            bitmap = bitmap,
                            colorFilter = activeColorFilter
                        )
                    }
                }
            }

            // Top Glassmorphic Navigation Bar
            AnimatedVisibility(
                visible = isControlsVisible,
                enter = fadeIn() + slideInVertically { -it },
                exit = fadeOut() + slideOutVertically { -it },
                modifier = Modifier.align(Alignment.TopCenter)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .glassmorphism(
                            shape = RoundedCornerShape(24.dp),
                            blurRadius = 16.dp,
                            isDark = readingMode == PdfReadingMode.DARK_NIGHT,
                            alpha = 0.88f
                        )
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            IconButton(
                                onClick = onBack,
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                                    .testTag("pdf_back_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                                    contentDescription = "Back",
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            Column(modifier = Modifier.clickable { isJumpDialogVisible = true }) {
                                Text(
                                    text = document.displayName,
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontFamily = OutfitFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = MaterialTheme.colorScheme.onBackground
                                    ),
                                    maxLines = 1
                                )
                                Text(
                                    text = "Page ${currentPageIndex + 1} / $totalPages  •  Tap to jump",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontFamily = OutfitFontFamily,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                                    )
                                )
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            // Rotation
                            IconButton(
                                onClick = { rotationDegrees = (rotationDegrees + 90) % 360 },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.RotateRight,
                                    contentDescription = "Rotate",
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            // Favorite
                            IconButton(
                                onClick = {
                                    isFavorite = !isFavorite
                                    onToggleFavorite(document.id)
                                },
                                modifier = Modifier.size(36.dp)
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
                }
            }

            // Bottom Glassmorphic Control Dock
            AnimatedVisibility(
                visible = isControlsVisible,
                enter = fadeIn() + slideInVertically { it },
                exit = fadeOut() + slideOutVertically { it },
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Optional Thumbnails Bar
                    if (isThumbnailsVisible) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(90.dp)
                                .glassmorphism(
                                    shape = RoundedCornerShape(20.dp),
                                    blurRadius = 16.dp,
                                    isDark = readingMode == PdfReadingMode.DARK_NIGHT,
                                    alpha = 0.90f
                                )
                                .padding(8.dp)
                        ) {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(count = totalPages) { pageIdx ->
                                    val isSelected = pageIdx == currentPageIndex
                                    var thumbBitmap by remember(pageIdx) { mutableStateOf(thumbnailBitmaps[pageIdx]) }

                                    LaunchedEffect(pageIdx, renderer) {
                                        if (thumbBitmap == null && renderer != null) {
                                            withContext(Dispatchers.IO) {
                                                try {
                                                    renderMutex.withLock {
                                                        val page = renderer!!.openPage(pageIdx)
                                                        val targetW = 120
                                                        val targetH = (targetW * (page.height.toFloat() / page.width.toFloat())).toInt().coerceAtLeast(1)
                                                        val bmp = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.RGB_565)
                                                        bmp.eraseColor(android.graphics.Color.WHITE)
                                                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                                        page.close()
                                                        thumbnailBitmaps[pageIdx] = bmp
                                                        thumbBitmap = bmp
                                                    }
                                                } catch (_: Exception) { }
                                            }
                                        }
                                    }

                                    Box(
                                        modifier = Modifier
                                            .width(55.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .border(
                                                width = if (isSelected) 2.5.dp else 1.dp,
                                                color = if (isSelected) NoteCoral else Color.Gray.copy(alpha = 0.4f),
                                                shape = RoundedCornerShape(8.dp)
                                            )
                                            .clickable {
                                                coroutineScope.launch {
                                                    listState.animateScrollToItem(pageIdx)
                                                }
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (thumbBitmap != null && !thumbBitmap!!.isRecycled) {
                                            Image(
                                                bitmap = thumbBitmap!!.asImageBitmap(),
                                                contentDescription = "Thumb ${pageIdx + 1}",
                                                contentScale = ContentScale.FillWidth,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        } else {
                                            Text(
                                                text = "${pageIdx + 1}",
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    fontFamily = OutfitFontFamily,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Main Capsule Action Dock
                    Box(
                        modifier = Modifier
                            .shadow(12.dp, RoundedCornerShape(32.dp))
                            .glassmorphism(
                                shape = RoundedCornerShape(32.dp),
                                blurRadius = 20.dp,
                                isDark = readingMode == PdfReadingMode.DARK_NIGHT,
                                alpha = 0.90f
                            )
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Zoom Out
                            IconButton(
                                onClick = {
                                    scale = (scale - 0.25f).coerceAtLeast(0.75f)
                                    if (scale <= 1.0f) {
                                        offsetX = 0f
                                        offsetY = 0f
                                    }
                                },
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.ZoomOut,
                                    contentDescription = "Zoom Out",
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            // Zoom level percentage text / Fit Screen
                            Text(
                                text = "${(scale * 100).toInt()}%",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontFamily = OutfitFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                ),
                                modifier = Modifier
                                    .clickable {
                                        scale = 1.0f
                                        offsetX = 0f
                                        offsetY = 0f
                                    }
                                    .padding(horizontal = 4.dp)
                            )

                            // Zoom In
                            IconButton(
                                onClick = { scale = (scale + 0.25f).coerceAtMost(5.0f) },
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.ZoomIn,
                                    contentDescription = "Zoom In",
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            // Fit Page Reset button if scaled
                            if (scale != 1.0f || offsetX != 0f || offsetY != 0f) {
                                IconButton(
                                    onClick = {
                                        scale = 1.0f
                                        offsetX = 0f
                                        offsetY = 0f
                                    },
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.FitScreen,
                                        contentDescription = "Fit Page",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }

                            // Reading Mode Cycle (Light Paper -> Sepia Warmth -> Dark Night)
                            IconButton(
                                onClick = {
                                    readingMode = when (readingMode) {
                                        PdfReadingMode.LIGHT_PAPER -> PdfReadingMode.SEPIA_WARMTH
                                        PdfReadingMode.SEPIA_WARMTH -> PdfReadingMode.DARK_NIGHT
                                        PdfReadingMode.DARK_NIGHT -> PdfReadingMode.LIGHT_PAPER
                                    }
                                },
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(
                                    imageVector = when (readingMode) {
                                        PdfReadingMode.LIGHT_PAPER -> Icons.Outlined.WbSunny
                                        PdfReadingMode.SEPIA_WARMTH -> Icons.Outlined.MenuBook
                                        PdfReadingMode.DARK_NIGHT -> Icons.Outlined.DarkMode
                                    },
                                    contentDescription = "Theme",
                                    tint = when (readingMode) {
                                        PdfReadingMode.LIGHT_PAPER -> MaterialTheme.colorScheme.onSurface
                                        PdfReadingMode.SEPIA_WARMTH -> Color(0xFFC4820A)
                                        PdfReadingMode.DARK_NIGHT -> NoteYellow
                                    },
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            // Thumbnails toggle
                            IconButton(
                                onClick = { isThumbnailsVisible = !isThumbnailsVisible },
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isThumbnailsVisible) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                        else Color.Transparent
                                    )
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Pageview,
                                    contentDescription = "Thumbnails",
                                    tint = if (isThumbnailsVisible) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Direct Page Jumper Dialog
            if (isJumpDialogVisible) {
                var targetPageStr by remember { mutableStateOf((currentPageIndex + 1).toString()) }
                var sliderValue by remember { mutableFloatStateOf((currentPageIndex + 1).toFloat()) }

                AlertDialog(
                    onDismissRequest = { isJumpDialogVisible = false },
                    title = {
                        Text(
                            text = "Jump to Page",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontFamily = OutfitFontFamily,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    },
                    text = {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Page ${sliderValue.toInt()} of $totalPages",
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontFamily = OutfitFontFamily,
                                    fontWeight = FontWeight.SemiBold
                                )
                            )

                            Slider(
                                value = sliderValue,
                                onValueChange = {
                                    sliderValue = it
                                    targetPageStr = it.toInt().toString()
                                },
                                valueRange = 1f..totalPages.toFloat(),
                                steps = (totalPages - 2).coerceAtLeast(0),
                                colors = SliderDefaults.colors(
                                    thumbColor = NoteCoral,
                                    activeTrackColor = NoteCoral
                                )
                            )

                            OutlinedTextField(
                                value = targetPageStr,
                                onValueChange = { input ->
                                    val filtered = input.filter { it.isDigit() }
                                    targetPageStr = filtered
                                    filtered.toIntOrNull()?.let {
                                        sliderValue = it.coerceIn(1, totalPages).toFloat()
                                    }
                                },
                                label = { Text("Enter page (1-$totalPages)") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Number,
                                    imeAction = ImeAction.Done
                                ),
                                keyboardActions = KeyboardActions(
                                    onDone = {
                                        val pageNum = targetPageStr.toIntOrNull() ?: (currentPageIndex + 1)
                                        val targetIdx = (pageNum - 1).coerceIn(0, totalPages - 1)
                                        coroutineScope.launch {
                                            listState.animateScrollToItem(targetIdx)
                                        }
                                        isJumpDialogVisible = false
                                    }
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                val pageNum = targetPageStr.toIntOrNull() ?: (currentPageIndex + 1)
                                val targetIdx = (pageNum - 1).coerceIn(0, totalPages - 1)
                                coroutineScope.launch {
                                    listState.animateScrollToItem(targetIdx)
                                }
                                isJumpDialogVisible = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Text("Go", fontFamily = OutfitFontFamily, fontWeight = FontWeight.Bold)
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { isJumpDialogVisible = false }) {
                            Text("Cancel", fontFamily = OutfitFontFamily)
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun PdfPageCard(
    pageIndex: Int,
    totalPages: Int,
    bitmap: Bitmap?,
    colorFilter: ColorFilter? = null,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(14.dp)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .shadow(6.dp, shape)
            .clip(shape)
            .background(Color.White),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null && !bitmap.isRecycled) {
            Box {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Page ${pageIndex + 1} of $totalPages",
                    contentScale = ContentScale.FillWidth,
                    colorFilter = colorFilter,
                    modifier = Modifier.fillMaxWidth()
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black.copy(alpha = 0.65f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "${pageIndex + 1} / $totalPages",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontFamily = OutfitFontFamily,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.707f)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CircularProgressIndicator(
                        color = Color(0xFF888888),
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = "Rendering Page ${pageIndex + 1}...",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = OutfitFontFamily,
                            fontSize = 11.sp,
                            color = Color(0xFF666666)
                        )
                    )
                }
            }
        }
    }
}
