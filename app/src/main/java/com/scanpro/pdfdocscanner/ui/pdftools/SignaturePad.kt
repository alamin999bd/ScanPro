package com.scanpro.pdfdocscanner.ui.pdftools

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint
import android.graphics.Path as AndroidPath
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.min

/** A white pad where the user draws a signature with a finger. Each stroke is a list of points. */
@Composable
fun SignaturePad(
    strokes: List<List<Offset>>,
    onChange: (List<List<Offset>>) -> Unit,
    modifier: Modifier = Modifier,
) {
    var current by remember { mutableStateOf<List<Offset>>(emptyList()) }
    val latestStrokes by rememberUpdatedState(strokes)
    val latestOnChange by rememberUpdatedState(onChange)
    val shape = RoundedCornerShape(16.dp)

    Box(
        modifier = modifier
            .clip(shape)
            .background(Color.White)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape),
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { start -> current = listOf(start) },
                        onDrag = { change, _ ->
                            change.consume()
                            current = current + change.position
                        },
                        onDragEnd = {
                            if (current.size > 1) latestOnChange(latestStrokes + listOf(current))
                            current = emptyList()
                        },
                        onDragCancel = { current = emptyList() },
                    )
                },
        ) {
            val style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            for (stroke in strokes + listOf(current)) {
                if (stroke.size < 2) continue
                val path = Path().apply {
                    moveTo(stroke[0].x, stroke[0].y)
                    for (i in 1 until stroke.size) lineTo(stroke[i].x, stroke[i].y)
                }
                drawPath(path, Color.Black, style = style)
            }
        }
        if (strokes.isEmpty() && current.isEmpty()) {
            Text(
                "Draw your signature here",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF9AA0A6),
                modifier = Modifier.align(Alignment.Center),
            )
        }
    }
}

/**
 * Renders the strokes as a black-on-transparent bitmap, cropped to the ink with a small margin.
 * Returns null when there is nothing (or only a tiny dot) to place.
 */
fun renderSignature(strokes: List<List<Offset>>): Bitmap? {
    val points = strokes.flatMap { it }
    if (points.size < 2) return null
    var minX = Float.MAX_VALUE
    var minY = Float.MAX_VALUE
    var maxX = -Float.MAX_VALUE
    var maxY = -Float.MAX_VALUE
    for (p in points) {
        minX = min(minX, p.x); maxX = max(maxX, p.x)
        minY = min(minY, p.y); maxY = max(maxY, p.y)
    }
    val inkW = maxX - minX
    val inkH = maxY - minY
    if (max(inkW, inkH) < 20f) return null

    val scale = min(3f, 1200f / max(inkW, 1f))
    val pad = 12f
    val w = max(1, ((inkW + pad * 2) * scale).toInt())
    val h = max(1, ((inkH + pad * 2) * scale).toInt())
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(bmp)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.BLACK
        style = Paint.Style.STROKE
        strokeWidth = 4f * scale
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    for (stroke in strokes) {
        if (stroke.size < 2) continue
        val path = AndroidPath()
        path.moveTo((stroke[0].x - minX + pad) * scale, (stroke[0].y - minY + pad) * scale)
        for (i in 1 until stroke.size) {
            path.lineTo((stroke[i].x - minX + pad) * scale, (stroke[i].y - minY + pad) * scale)
        }
        canvas.drawPath(path, paint)
    }
    return bmp
}
