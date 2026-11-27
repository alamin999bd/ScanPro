package com.scanpro.pdfdocscanner.ui.editor

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.RotateLeft
import androidx.compose.material.icons.rounded.RotateRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.scanpro.pdfdocscanner.AppContainer
import com.scanpro.pdfdocscanner.core.EditParams
import com.scanpro.pdfdocscanner.core.NRect
import com.scanpro.pdfdocscanner.core.PageFilter
import com.scanpro.pdfdocscanner.core.PageQuadGeometry
import com.scanpro.pdfdocscanner.ui.components.ProgressDialog
import com.scanpro.pdfdocscanner.ui.components.ScanProTopBar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

const val ID_CARD_ASPECT = 1.586f // ISO/IEC 7810 ID-1 (85.6 x 54 mm)
const val A4_PORTRAIT_ASPECT = 0.707f

/**
 * Full-screen editor for one captured/imported page: crop (draggable handles + aspect presets),
 * rotate, offline filters and brightness/contrast. Applying writes a NEW file and returns it,
 * so image caches never show a stale version. Cancelling leaves the original untouched.
 */
@Composable
fun PageEditorScreen(
    container: AppContainer,
    file: File,
    initialAspect: Float?,
    onCancel: () -> Unit,
    onApplied: (File) -> Unit,
) {
    val processor = container.imageProcessor
    val scope = rememberCoroutineScope()

    var source by remember { mutableStateOf<Bitmap?>(null) }
    var loadFailed by remember { mutableStateOf(false) }
    var shown by remember { mutableStateOf<Bitmap?>(null) }

    var rotation by remember { mutableIntStateOf(0) }
    var filter by remember { mutableStateOf(PageFilter.ORIGINAL) }
    var brightness by remember { mutableFloatStateOf(0f) }
    var contrast by remember { mutableFloatStateOf(1f) }
    var aspect by remember { mutableStateOf(initialAspect) }
    var crop by remember { mutableStateOf(NRect()) }
    var tab by remember { mutableIntStateOf(0) }
    var saving by remember { mutableStateOf(false) }
    var detecting by remember { mutableStateOf(false) }
    var quad by remember { mutableStateOf<List<Float>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    BackHandler(enabled = !saving) { onCancel() }

    LaunchedEffect(file) {
        val bmp = withContext(Dispatchers.IO) { processor.loadBitmap(file, 1400) }
        if (bmp == null) {
            loadFailed = true
        } else {
            source = bmp
            val a = aspect
            if (a != null) crop = fitRect(a, bmp.width, bmp.height)
        }
    }

    LaunchedEffect(source, rotation, filter, brightness, contrast, quad) {
        val s = source ?: return@LaunchedEffect
        shown = processor.preview(s, EditParams(rotation, NRect(), filter, brightness, contrast, quad))
    }

    fun rotatedDims(): Pair<Int, Int> {
        val s = source ?: return 1 to 1
        val (w, h) = processor.warpedSize(s.width, s.height, quad)
        return if (rotation % 180 == 0) w to h else h to w
    }

    fun resetCropFor(newRotation: Int) {
        val s = source ?: return
        val (bw, bh) = processor.warpedSize(s.width, s.height, quad)
        val (w, h) = if (newRotation % 180 == 0) bw to bh else bh to bw
        val a = aspect
        crop = if (a != null) fitRect(a, w, h) else NRect()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            ScanProTopBar(
                title = "Edit page",
                onBack = { if (!saving) onCancel() },
                actions = {
                    IconButton(
                        enabled = shown != null && !saving,
                        onClick = {
                            saving = true
                            error = null
                            scope.launch {
                                val out = container.fileStore.newTempFile("jpg")
                                val ok = processor.export(
                                    input = file,
                                    output = out,
                                    params = EditParams(rotation, crop, filter, brightness, contrast, quad),
                                )
                                saving = false
                                if (ok) {
                                    onApplied(out)
                                } else {
                                    out.delete()
                                    error = "Could not apply the changes. The image may be too large for this device."
                                }
                            }
                        },
                    ) { Icon(Icons.Rounded.Check, contentDescription = "Apply") }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .background(Color(0xFF111111)),
                contentAlignment = Alignment.Center,
            ) {
                val img = shown
                when {
                    loadFailed -> Text("This image could not be opened.", color = Color.White)
                    img == null -> CircularProgressIndicator()
                    else -> CropArea(
                        image = if (quad != null) source ?: img else img,
                        crop = crop,
                        aspect = aspect,
                        quad = quad,
                        onCrop = { crop = it },
                        onQuad = { quad = it },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }

            error?.let {
                Text(
                    it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }

            TabRow(
                selectedTabIndex = tab,
                containerColor = MaterialTheme.colorScheme.background,
                modifier = Modifier.padding(top = 8.dp),
            ) {
                listOf("Crop & Rotate", "Filters", "Adjust").forEachIndexed { i, label ->
                    Tab(selected = tab == i, onClick = { tab = i }, text = { Text(label) })
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .height(112.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                when (tab) {
                    0 -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            IconButton(onClick = {
                                val r = (rotation + 270) % 360
                                rotation = r
                                resetCropFor(r)
                            }) { Icon(Icons.Rounded.RotateLeft, contentDescription = "Rotate left") }
                            IconButton(onClick = {
                                val r = (rotation + 90) % 360
                                rotation = r
                                resetCropFor(r)
                            }) { Icon(Icons.Rounded.RotateRight, contentDescription = "Rotate right") }
                            OutlinedButton(onClick = {
                                val src = source ?: return@OutlinedButton
                                detecting = true
                                error = null
                                scope.launch {
                                    val rotated = processor.preview(src, EditParams(rotation = rotation, quad = quad))
                                    val box = processor.detectDocument(rotated)
                                    detecting = false
                                    if (box != null) {
                                        aspect = null
                                        crop = box
                                    } else {
                                        error = "Could not find the page edges. Drag the corners by hand."
                                    }
                                }
                            }, enabled = source != null && !detecting) { Text("Auto") }
                            OutlinedButton(onClick = {
                                aspect = null
                                crop = NRect()
                            }) { Text("Reset") }
                        }
                        Spacer(Modifier.height(6.dp))
                        OutlinedButton(
                            onClick = {
                                val src = source ?: return@OutlinedButton
                                if (quad != null) {
                                    quad = null
                                    aspect = null
                                    crop = NRect()
                                    return@OutlinedButton
                                }
                                detecting = true
                                error = null
                                scope.launch {
                                    val found = processor.detectQuad(src)
                                    detecting = false
                                    if (found != null) {
                                        quad = found
                                        aspect = null
                                        crop = NRect()
                                    } else {
                                        error = "Could not find the four page corners. " +
                                            "Try a photo where the page is on a plain, contrasting surface."
                                    }
                                }
                            },
                            enabled = source != null && !detecting,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(if (quad != null) "Undo straighten" else "Straighten page (fix perspective)") }
                        Spacer(Modifier.height(6.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            val presets = listOf<Pair<String, Float?>>(
                                "Free" to null,
                                "ID Card" to ID_CARD_ASPECT,
                                "A4" to A4_PORTRAIT_ASPECT,
                                "Square" to 1f,
                            )
                            items(presets) { (label, value) ->
                                FilterChip(
                                    selected = aspect == value,
                                    onClick = {
                                        aspect = value
                                        val (w, h) = rotatedDims()
                                        if (value != null) crop = fitRect(value, w, h)
                                    },
                                    label = { Text(label) },
                                )
                            }
                        }
                    }
                    1 -> LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(PageFilter.entries.toList()) { f ->
                            FilterChip(
                                selected = filter == f,
                                onClick = { filter = f },
                                label = { Text(f.label) },
                            )
                        }
                    }
                    else -> Column {
                        LabeledSlider("Brightness", brightness, -100f..100f) { brightness = it }
                        LabeledSlider("Contrast", contrast, 0.6f..1.6f) { contrast = it }
                    }
                }
            }
        }
    }

    if (saving) ProgressDialog("Applying changes…")
}

@Composable
private fun LabeledSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.size(width = 88.dp, height = 24.dp))
        Slider(value = value, onValueChange = onChange, valueRange = range, modifier = Modifier.weight(1f))
    }
}

/** Largest centred rectangle with pixel aspect [aspect] (w/h) that fits ~92% of the image. */
private fun fitRect(aspect: Float, imgW: Int, imgH: Int): NRect {
    val maxW = imgW * 0.92f
    val maxH = imgH * 0.92f
    var w = maxW
    var h = w / aspect
    if (h > maxH) {
        h = maxH
        w = h * aspect
    }
    val nw = w / imgW
    val nh = h / imgH
    return NRect((1 - nw) / 2f, (1 - nh) / 2f, (1 + nw) / 2f, (1 + nh) / 2f)
}

private const val MIN_SIZE = 0.08f

@Composable
private fun CropArea(
    image: Bitmap,
    crop: NRect,
    aspect: Float?,
    quad: List<Float>?,
    onCrop: (NRect) -> Unit,
    onQuad: (List<Float>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val primary = MaterialTheme.colorScheme.primary
    val density = LocalDensity.current
    val bitmap = remember(image) { image.asImageBitmap() }
    val currentCrop by rememberUpdatedState(crop)
    val currentOnCrop by rememberUpdatedState(onCrop)
    val currentQuad by rememberUpdatedState(quad)
    val currentOnQuad by rememberUpdatedState(onQuad)

    BoxWithConstraints(modifier = modifier) {
        val boxW = constraints.maxWidth.toFloat()
        val boxH = constraints.maxHeight.toFloat()
        val scale = min(boxW / image.width, boxH / image.height)
        val dw = image.width * scale
        val dh = image.height * scale
        val ox = (boxW - dw) / 2f
        val oy = (boxH - dh) / 2f
        val touch = with(density) { 44.dp.toPx() }
        val handleR = with(density) { 9.dp.toPx() }
        val stroke = with(density) { 2.dp.toPx() }

        Image(
            bitmap = bitmap,
            contentDescription = "Page preview",
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
        )

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(dw, dh, aspect) {
                    // With a detected quadrilateral, drag its four corners directly.
                    // Otherwise use the rectangular crop handles below.
                    var active = -1
                    var activeQuadCorner = -1
                    detectDragGestures(
                        onDragStart = { p ->
                            val q = currentQuad
                            if (q != null && q.size == 8) {
                                val points = (0..3).map { i ->
                                    Offset(ox + q[i * 2] * dw, oy + q[i * 2 + 1] * dh)
                                }
                                var best = -1
                                var bestDist = touch * 1.5f
                                points.forEachIndexed { i, point ->
                                    val d = (point - p).getDistance()
                                    if (d < bestDist) { bestDist = d; best = i }
                                }
                                activeQuadCorner = best
                                active = -2
                            } else {
                                val cropNow = currentCrop
                                val l = ox + cropNow.left * dw
                                val t = oy + cropNow.top * dh
                                val r = ox + cropNow.right * dw
                                val b = oy + cropNow.bottom * dh
                                val corners = listOf(Offset(l, t), Offset(r, t), Offset(l, b), Offset(r, b))
                                var best = -1
                                var bestDist = touch
                                corners.forEachIndexed { i, point ->
                                    val d = (point - p).getDistance()
                                    if (d < bestDist) { bestDist = d; best = i }
                                }
                                active = when {
                                    best >= 0 -> best
                                    p.x in l..r && p.y in t..b -> 4
                                    else -> -1
                                }
                            }
                        },
                        onDragEnd = { active = -1; activeQuadCorner = -1 },
                        onDragCancel = { active = -1; activeQuadCorner = -1 },
                        onDrag = { change, drag ->
                            change.consume()
                            if (active == -2 && activeQuadCorner >= 0) {
                                val q = currentQuad?.toMutableList() ?: return@detectDragGestures
                                val i = activeQuadCorner * 2
                                val minGap = 0.025f
                                val nx = (q[i] + drag.x / dw).coerceIn(0f, 1f)
                                val ny = (q[i + 1] + drag.y / dh).coerceIn(0f, 1f)
                                q[i] = nx
                                q[i + 1] = ny
                                // Keep each corner inside the image; prevent exact edge degeneracy.
                                val prev = (activeQuadCorner + 3) % 4
                                val next = (activeQuadCorner + 1) % 4
                                if (kotlin.math.hypot((q[i] - q[prev * 2]).toDouble(), (q[i + 1] - q[prev * 2 + 1]).toDouble()) > minGap &&
                                    kotlin.math.hypot((q[i] - q[next * 2]).toDouble(), (q[i + 1] - q[next * 2 + 1]).toDouble()) > minGap &&
                                    PageQuadGeometry.isValid(q)
                                ) currentOnQuad(q)
                            } else if (active >= 0) {
                                currentOnCrop(updateCrop(currentCrop, active, drag.x / dw, drag.y / dh, aspect, dw, dh))
                            }
                        },
                    )
                },
        ) {
            val l = ox + crop.left * dw
            val t = oy + crop.top * dh
            val r = ox + crop.right * dw
            val b = oy + crop.bottom * dh
            val dim = Color.Black.copy(alpha = 0.55f)
            drawRect(dim, Offset(ox, oy), Size(dw, t - oy))
            drawRect(dim, Offset(ox, b), Size(dw, oy + dh - b))
            drawRect(dim, Offset(ox, t), Size(l - ox, b - t))
            drawRect(dim, Offset(r, t), Size(ox + dw - r, b - t))
            drawRect(Color.White, Offset(l, t), Size(r - l, b - t), style = Stroke(stroke))
            val third = Color.White.copy(alpha = 0.35f)
            for (i in 1..2) {
                val x = l + (r - l) * i / 3f
                val y = t + (b - t) * i / 3f
                drawLine(third, Offset(x, t), Offset(x, b), strokeWidth = 1f)
                drawLine(third, Offset(l, y), Offset(r, y), strokeWidth = 1f)
            }
            listOf(Offset(l, t), Offset(r, t), Offset(l, b), Offset(r, b)).forEach {
                drawCircle(Color.White, handleR, it)
                drawCircle(primary, handleR - stroke, it)
            }
            // Four editable perspective-correction corners (TL, TR, BR, BL).
            val q = quad
            if (q != null && q.size == 8) {
                val points = (0..3).map { i -> Offset(ox + q[i * 2] * dw, oy + q[i * 2 + 1] * dh) }
                for (i in 0..3) drawLine(Color(0xFF35D6A6), points[i], points[(i + 1) % 4], strokeWidth = stroke * 1.8f)
                points.forEach { point ->
                    drawCircle(Color.White, handleR * 1.35f, point)
                    drawCircle(Color(0xFF087F68), handleR, point)
                }
            }
        }
    }
}

/** Applies a drag to the crop rectangle, optionally keeping a fixed pixel aspect ratio. */
private fun updateCrop(c: NRect, handle: Int, dxn: Float, dyn: Float, aspect: Float?, dw: Float, dh: Float): NRect {
    if (handle == 4) {
        val w = c.width
        val h = c.height
        val left = (c.left + dxn).coerceIn(0f, 1f - w)
        val top = (c.top + dyn).coerceIn(0f, 1f - h)
        return NRect(left, top, left + w, top + h)
    }
    val leftSide = handle == 0 || handle == 2
    val topSide = handle == 0 || handle == 1
    val ax = if (leftSide) c.right else c.left // fixed (opposite) corner
    val ay = if (topSide) c.bottom else c.top
    val px = (if (leftSide) c.left else c.right) + dxn
    val py = (if (topSide) c.top else c.bottom) + dyn

    val maxW = if (leftSide) ax else 1f - ax
    val maxH = if (topSide) ay else 1f - ay
    var wn = abs(px - ax)
    var hn = abs(py - ay)
    if (aspect != null) {
        hn = wn * dw / aspect / dh
        if (wn > maxW) {
            wn = maxW
            hn = wn * dw / aspect / dh
        }
        if (hn > maxH) {
            hn = maxH
            wn = hn * dh * aspect / dw
        }
    } else {
        wn = min(wn, maxW)
        hn = min(hn, maxH)
    }
    wn = max(wn, MIN_SIZE)
    hn = max(hn, MIN_SIZE)
    val left = if (leftSide) ax - wn else ax
    val top = if (topSide) ay - hn else ay
    return NRect(left.coerceAtLeast(0f), top.coerceAtLeast(0f), (left + wn).coerceAtMost(1f), (top + hn).coerceAtMost(1f))
}
