package com.example.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint
import android.graphics.Path as AndroidPath
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Redo
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.OutfitFontFamily
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

data class DrawnStroke(
    val path: List<Offset>,
    val color: Color,
    val strokeWidth: Float
)

@Composable
fun SketchPadModal(
    onDismiss: () -> Unit,
    onSaveSketch: (imagePath: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val density = LocalDensity.current

    val strokes = remember { mutableStateListOf<DrawnStroke>() }
    val undoneStrokes = remember { mutableStateListOf<DrawnStroke>() }
    var currentPath = remember { mutableStateListOf<Offset>() }

    val palette = listOf(
        Color(0xFF141414), // Ink Black
        Color(0xFFE11D48), // Ruby Red / Coral
        Color(0xFF7C3AED), // Editorial Purple
        Color(0xFF10B981), // Mint Sage
        Color(0xFF2563EB), // Ocean Blue
        Color(0xFFF59E0B)  // Warm Amber
    )

    var selectedColor by remember { mutableStateOf(palette[0]) }
    var selectedWidthDp by remember { mutableStateOf(4f) }
    val strokeWidthPx = with(density) { selectedWidthDp.dp.toPx() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xCC000000))
            .clickable(enabled = false) { },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .height(580.dp)
                .shadow(24.dp, RoundedCornerShape(28.dp))
                .clip(RoundedCornerShape(28.dp))
                .background(Color(0xFFFBF7EE))
                .padding(16.dp)
        ) {
            // Header bar: Title & Close
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Hand-Drawn Sketch",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = OutfitFontFamily,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF141414)
                    )
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Undo
                    IconButton(
                        onClick = {
                            if (strokes.isNotEmpty()) {
                                val last = strokes.removeLast()
                                undoneStrokes.add(last)
                            }
                        },
                        enabled = strokes.isNotEmpty()
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.Undo,
                            contentDescription = "Undo",
                            tint = if (strokes.isNotEmpty()) Color(0xFF141414) else Color(0x44141414)
                        )
                    }

                    // Redo
                    IconButton(
                        onClick = {
                            if (undoneStrokes.isNotEmpty()) {
                                val last = undoneStrokes.removeLast()
                                strokes.add(last)
                            }
                        },
                        enabled = undoneStrokes.isNotEmpty()
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.Redo,
                            contentDescription = "Redo",
                            tint = if (undoneStrokes.isNotEmpty()) Color(0xFF141414) else Color(0x44141414)
                        )
                    }

                    // Clear
                    IconButton(
                        onClick = {
                            strokes.clear()
                            undoneStrokes.clear()
                            currentPath.clear()
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.DeleteOutline,
                            contentDescription = "Clear Canvas",
                            tint = Color(0xFFE11D48)
                        )
                    }

                    // Close
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = "Close",
                            tint = Color(0xFF141414)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // White Drawing Area Canvas
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White)
                    .border(1.dp, Color(0x22000000), RoundedCornerShape(16.dp))
                    .pointerInput(selectedColor, strokeWidthPx) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                undoneStrokes.clear()
                                currentPath.clear()
                                currentPath.add(offset)
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                currentPath.add(change.position)
                            },
                            onDragEnd = {
                                if (currentPath.isNotEmpty()) {
                                    strokes.add(
                                        DrawnStroke(
                                            path = currentPath.toList(),
                                            color = selectedColor,
                                            strokeWidth = strokeWidthPx
                                        )
                                    )
                                    currentPath.clear()
                                }
                            },
                            onDragCancel = {
                                currentPath.clear()
                            }
                        )
                    }
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    // Draw committed strokes
                    strokes.forEach { stroke ->
                        if (stroke.path.size > 1) {
                            val p = Path().apply {
                                moveTo(stroke.path.first().x, stroke.path.first().y)
                                for (i in 1 until stroke.path.size) {
                                    lineTo(stroke.path[i].x, stroke.path[i].y)
                                }
                            }
                            drawPath(
                                path = p,
                                color = stroke.color,
                                style = Stroke(
                                    width = stroke.strokeWidth,
                                    cap = StrokeCap.Round,
                                    join = StrokeJoin.Round
                                )
                            )
                        } else if (stroke.path.size == 1) {
                            drawCircle(
                                color = stroke.color,
                                radius = stroke.strokeWidth / 2f,
                                center = stroke.path.first()
                            )
                        }
                    }

                    // Draw ongoing stroke
                    if (currentPath.size > 1) {
                        val activePath = Path().apply {
                            moveTo(currentPath.first().x, currentPath.first().y)
                            for (i in 1 until currentPath.size) {
                                lineTo(currentPath[i].x, currentPath[i].y)
                            }
                        }
                        drawPath(
                            path = activePath,
                            color = selectedColor,
                            style = Stroke(
                                width = strokeWidthPx,
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round
                            )
                        )
                    } else if (currentPath.size == 1) {
                        drawCircle(
                            color = selectedColor,
                            radius = strokeWidthPx / 2f,
                            center = currentPath.first()
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Color Palette and Stroke Width Picker
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Color circles
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    palette.forEach { color ->
                        val isSelected = selectedColor == color
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(color)
                                .clickable { selectedColor = color }
                                .border(
                                    width = if (isSelected) 3.dp else 1.dp,
                                    color = if (isSelected) Color(0xFF141414) else Color(0x33000000),
                                    shape = CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(Color.White)
                                )
                            }
                        }
                    }
                }

                // Stroke Widths (Fine, Med, Bold)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    listOf(2f to "Fine", 5f to "Med", 10f to "Bold").forEach { (width, label) ->
                        val isSelected = selectedWidthDp == width
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) Color(0xFF141414) else Color(0x18000000))
                                .clickable { selectedWidthDp = width }
                                .padding(horizontal = 8.dp, vertical = 5.dp)
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontFamily = OutfitFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) Color.White else Color(0xFF141414)
                                )
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Bottom Buttons (Cancel & Save Sketch)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0x18000000),
                        contentColor = Color(0xFF141414)
                    ),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Discard", fontFamily = OutfitFontFamily, fontWeight = FontWeight.SemiBold)
                }

                Button(
                    onClick = {
                        if (strokes.isNotEmpty()) {
                            val savedPath = exportStrokesToPng(context, strokes)
                            if (savedPath != null) {
                                onSaveSketch(savedPath)
                            }
                        }
                        onDismiss()
                    },
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF141414),
                        contentColor = Color.White
                    ),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Outlined.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Attach Sketch", fontFamily = OutfitFontFamily, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

private fun exportStrokesToPng(context: Context, strokes: List<DrawnStroke>): String? {
    return try {
        val width = 720
        val height = 720
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = AndroidCanvas(bitmap)
        canvas.drawColor(android.graphics.Color.WHITE)

        val paint = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

        // Compute scaling ratio from the canvas preview to 720x720
        strokes.forEach { stroke ->
            paint.color = stroke.color.toArgb()
            paint.strokeWidth = stroke.strokeWidth * 1.5f

            if (stroke.path.size > 1) {
                val path = AndroidPath().apply {
                    moveTo(stroke.path.first().x, stroke.path.first().y)
                    for (i in 1 until stroke.path.size) {
                        lineTo(stroke.path[i].x, stroke.path[i].y)
                    }
                }
                canvas.drawPath(path, paint)
            } else if (stroke.path.size == 1) {
                paint.style = Paint.Style.FILL
                canvas.drawCircle(stroke.path.first().x, stroke.path.first().y, paint.strokeWidth / 2f, paint)
                paint.style = Paint.Style.STROKE
            }
        }

        val sketchDir = File(context.filesDir, "sketches").apply { mkdirs() }
        val file = File(sketchDir, "sketch_${UUID.randomUUID().toString().take(8)}.png")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 95, out)
        }
        bitmap.recycle()
        file.absolutePath
    } catch (_: Exception) {
        null
    }
}
