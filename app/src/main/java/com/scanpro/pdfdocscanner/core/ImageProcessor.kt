package com.scanpro.pdfdocscanner.core

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max
import kotlin.math.min

enum class PageFilter(val label: String) {
    ORIGINAL("Original"),
    ENHANCE("Enhance"),
    DOCUMENT("B&W Document"),
    GRAYSCALE("Grayscale"),
    VIVID("Vivid"),
    AUTO("Auto"),
    LIGHTEN("Lighten"),
    DARKEN("Darken"),
    COLOR("Color"),
}

/** Crop rectangle in normalized (0..1) coordinates of the already-rotated image. */
data class NRect(
    val left: Float = 0f,
    val top: Float = 0f,
    val right: Float = 1f,
    val bottom: Float = 1f,
) {
    val width get() = right - left
    val height get() = bottom - top
    val isFull get() = left <= 0.001f && top <= 0.001f && right >= 0.999f && bottom >= 0.999f
}

data class EditParams(
    /** Clockwise degrees: 0, 90, 180, 270. */
    val rotation: Int = 0,
    val crop: NRect = NRect(),
    val filter: PageFilter = PageFilter.ORIGINAL,
    /** -100..100 */
    val brightness: Float = 0f,
    /** 0.6..1.6 (1 = unchanged) */
    val contrast: Float = 1f,
    /**
     * Page corners in normalized coordinates of the ORIGINAL (unrotated) photo, in the order
     * top-left, top-right, bottom-right, bottom-left: [x0,y0,x1,y1,x2,y2,x3,y3].
     * When set, the page is straightened (perspective correction) before anything else.
     */
    val quad: List<Float>? = null,
)

/**
 * Pure-Kotlin, fully offline image pipeline: rotate -> crop -> brightness/contrast -> filter.
 * No OpenCV or ML model is used; every filter is a small, deterministic pixel algorithm.
 */
class ImageProcessor(private val pdf: PdfEngine) {

    fun loadBitmap(file: File, maxDim: Int): Bitmap? = pdf.decodeBitmap(Uri.fromFile(file), maxDim)

    /** Rotation + look only (crop is shown as an overlay in the editor). */
    suspend fun preview(source: Bitmap, params: EditParams): Bitmap = withContext(Dispatchers.Default) {
        applyLook(rotate(warp(source, params.quad), params.rotation), params)
    }

    /** Full-resolution export of the edit to a new JPEG file. Returns false on failure. */
    suspend fun export(input: File, output: File, params: EditParams, quality: Int = 92): Boolean =
        withContext(Dispatchers.Default) {
            try {
                val source = loadBitmap(input, MAX_EXPORT_DIM) ?: return@withContext false
                val rotated = rotate(warp(source, params.quad), params.rotation)
                val cropped = crop(rotated, params.crop)
                val result = applyLook(cropped, params)
                output.outputStream().use { result.compress(Bitmap.CompressFormat.JPEG, quality, it) }
                true
            } catch (ignored: Throwable) {
                // Includes OutOfMemoryError on very large images: caller reports a clear message.
                false
            }
        }

    // ---------- geometry ----------

    /** Size of the straightened page for a source of [srcW] x [srcH] (the source size when [quad] is null). */
    fun warpedSize(srcW: Int, srcH: Int, quad: List<Float>?): Pair<Int, Int> {
        if (quad == null) return srcW to srcH
        return PageQuadGeometry.outputSize(srcW, srcH, quad, MAX_EXPORT_DIM) ?: (srcW to srcH)
    }

    /** Perspective correction: maps the quad onto a flat rectangle. Returns [src] when [quad] is null. */
    private fun warp(src: Bitmap, quad: List<Float>?): Bitmap {
        if (quad == null || !PageQuadGeometry.isValid(quad)) return src
        val (outW, outH) = warpedSize(src.width, src.height, quad)
        val from = FloatArray(8) { i -> quad[i] * (if (i % 2 == 0) src.width else src.height) }
        val to = floatArrayOf(0f, 0f, outW.toFloat(), 0f, outW.toFloat(), outH.toFloat(), 0f, outH.toFloat())
        val m = Matrix()
        if (!m.setPolyToPoly(from, 0, to, 0, 4)) return src
        val out = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        out.eraseColor(android.graphics.Color.WHITE)
        Canvas(out).drawBitmap(src, m, Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
        return out
    }

    private fun rotate(src: Bitmap, degrees: Int): Bitmap {
        val d = ((degrees % 360) + 360) % 360
        if (d == 0) return src
        val m = Matrix().apply { postRotate(d.toFloat()) }
        return Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
    }

    private fun crop(src: Bitmap, r: NRect): Bitmap {
        if (r.isFull) return src
        val w = src.width
        val h = src.height
        val x = (r.left * w).toInt().coerceIn(0, w - 1)
        val y = (r.top * h).toInt().coerceIn(0, h - 1)
        val cw = ((r.right - r.left) * w).toInt().coerceIn(1, w - x)
        val ch = ((r.bottom - r.top) * h).toInt().coerceIn(1, h - y)
        return Bitmap.createBitmap(src, x, y, cw, ch)
    }

    // ---------- look ----------

    private fun applyLook(input: Bitmap, p: EditParams): Bitmap {
        var cur = input
        if (p.brightness != 0f || p.contrast != 1f) {
            cur = transform(cur, contrastMatrix(p.contrast, p.brightness))
        }
        return when (p.filter) {
            PageFilter.ORIGINAL -> cur
            PageFilter.ENHANCE -> sharpen(stretch(cur), 0.6f)
            PageFilter.DOCUMENT -> adaptiveThreshold(stretch(cur))
            PageFilter.GRAYSCALE -> {
                val s = stretch(cur)
                transform(s, ColorMatrix().apply { setSaturation(0f) })
            }
            PageFilter.VIVID -> {
                val s = stretch(cur)
                transform(s, ColorMatrix().apply { setSaturation(1.35f) })
            }
            PageFilter.AUTO -> sharpen(stretch(cur), 0.45f)
            PageFilter.LIGHTEN -> stretch(transform(cur, contrastMatrix(1.04f, 22f)))
            PageFilter.DARKEN -> transform(cur, contrastMatrix(1.08f, -18f))
            PageFilter.COLOR -> transform(stretch(cur), ColorMatrix().apply { setSaturation(1.15f) })
        }
    }

    private fun contrastMatrix(contrast: Float, brightness: Float): ColorMatrix {
        val t = (-0.5f * contrast + 0.5f) * 255f + brightness
        return ColorMatrix(
            floatArrayOf(
                contrast, 0f, 0f, 0f, t,
                0f, contrast, 0f, 0f, t,
                0f, 0f, contrast, 0f, t,
                0f, 0f, 0f, 1f, 0f,
            )
        )
    }

    private fun transform(src: Bitmap, cm: ColorMatrix): Bitmap {
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG).apply { colorFilter = ColorMatrixColorFilter(cm) }
        Canvas(out).drawBitmap(src, 0f, 0f, paint)
        return out
    }

    /** Auto-levels: stretches the 1st..99th luminance percentile to the full range (whitens paper). */
    private fun stretch(src: Bitmap): Bitmap {
        val w = src.width
        val h = src.height
        val hist = IntArray(256)
        var n = 0
        val row = IntArray(w)
        val rowStep = max(1, h / 300)
        var y = 0
        while (y < h) {
            src.getPixels(row, 0, w, 0, y, w, 1)
            var x = 0
            while (x < w) {
                val c = row[x]
                hist[luma(c)]++
                n++
                x += 3
            }
            y += rowStep
        }
        if (n == 0) return src
        val lo = percentile(hist, n, 0.01)
        val hi = percentile(hist, n, 0.99)
        if (hi - lo < 40) return src
        val scale = 255f / (hi - lo)
        val off = -lo * scale
        return transform(
            src,
            ColorMatrix(
                floatArrayOf(
                    scale, 0f, 0f, 0f, off,
                    0f, scale, 0f, 0f, off,
                    0f, 0f, scale, 0f, off,
                    0f, 0f, 0f, 1f, 0f,
                )
            )
        )
    }

    private fun percentile(hist: IntArray, total: Int, p: Double): Int {
        val target = (total * p).toInt()
        var acc = 0
        for (i in 0..255) {
            acc += hist[i]
            if (acc >= target) return i
        }
        return 255
    }

    private fun luma(c: Int): Int =
        ((((c shr 16) and 255) * 299 + ((c shr 8) and 255) * 587 + (c and 255) * 114) / 1000)

    /** 4-neighbour unsharp mask for crisper text. */
    private fun sharpen(src: Bitmap, amount: Float): Bitmap {
        val w = src.width
        val h = src.height
        if (w < 3 || h < 3) return src
        val px = IntArray(w * h)
        src.getPixels(px, 0, w, 0, 0, w, h)
        val out = px.copyOf()
        for (y in 1 until h - 1) {
            val rowStart = y * w
            for (x in 1 until w - 1) {
                val i = rowStart + x
                val c = px[i]
                val u = px[i - w]
                val d = px[i + w]
                val l = px[i - 1]
                val r = px[i + 1]
                out[i] = (0xFF shl 24) or
                    (sharpChannel(c, u, d, l, r, 16, amount) shl 16) or
                    (sharpChannel(c, u, d, l, r, 8, amount) shl 8) or
                    sharpChannel(c, u, d, l, r, 0, amount)
            }
        }
        val res = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        res.setPixels(out, 0, w, 0, 0, w, h)
        return res
    }

    private fun sharpChannel(c: Int, u: Int, d: Int, l: Int, r: Int, shift: Int, a: Float): Int {
        val cv = (c shr shift) and 255
        val n = ((u shr shift) and 255) + ((d shr shift) and 255) + ((l shr shift) and 255) + ((r shr shift) and 255)
        return (cv + a * (4 * cv - n)).toInt().coerceIn(0, 255)
    }

    /**
     * Adaptive (local-mean) threshold using an integral image: pixels clearly darker than their
     * neighbourhood become black, everything else white. Works well for shadows on paper.
     */
    private fun adaptiveThreshold(input: Bitmap): Bitmap {
        var src = input
        // The integral image is an Int array: cap the pixel count so sums cannot overflow.
        if (src.width.toLong() * src.height > MAX_THRESHOLD_PIXELS) {
            val s = Math.sqrt(MAX_THRESHOLD_PIXELS.toDouble() / (src.width.toDouble() * src.height)).toFloat()
            src = Bitmap.createScaledBitmap(src, (src.width * s).toInt().coerceAtLeast(1), (src.height * s).toInt().coerceAtLeast(1), true)
        }
        val w = src.width
        val h = src.height
        val px = IntArray(w * h)
        src.getPixels(px, 0, w, 0, 0, w, h)
        val gray = IntArray(w * h) { luma(px[it]) }

        val stride = w + 1
        val integral = IntArray(stride * (h + 1))
        for (y in 0 until h) {
            var rowSum = 0
            for (x in 0 until w) {
                rowSum += gray[y * w + x]
                integral[(y + 1) * stride + (x + 1)] = integral[y * stride + (x + 1)] + rowSum
            }
        }

        val r = max(8, max(w, h) / 32)
        val black = 0xFF000000.toInt()
        val white = 0xFFFFFFFF.toInt()
        val out = IntArray(w * h)
        for (y in 0 until h) {
            val y0 = max(0, y - r)
            val y1 = min(h - 1, y + r)
            for (x in 0 until w) {
                val x0 = max(0, x - r)
                val x1 = min(w - 1, x + r)
                val area = (x1 - x0 + 1) * (y1 - y0 + 1)
                val sum = integral[(y1 + 1) * stride + (x1 + 1)] -
                    integral[y0 * stride + (x1 + 1)] -
                    integral[(y1 + 1) * stride + x0] +
                    integral[y0 * stride + x0]
                val mean = sum / area
                // Darker than 88% of the local mean => ink.
                out[y * w + x] = if (gray[y * w + x] * 100 < mean * 88) black else white
            }
        }
        val res = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        res.setPixels(out, 0, w, 0, 0, w, h)
        return res
    }

    // ---------- automatic page detection ----------

    /**
     * Looks for the page inside a photo: the biggest region that is clearly brighter (or darker)
     * than its surroundings. Returns its bounding box, or null when nothing convincing is found.
     * This is a rectangle only: there is no perspective correction.
     */
    suspend fun detectDocument(source: Bitmap): NRect? = withContext(Dispatchers.Default) {
        try {
            val f = findPageBlob(source) ?: return@withContext null
            val blob = f.blob
            val padX = 0.01f
            val padY = 0.01f
            NRect(
                left = (blob[0].toFloat() / f.w - padX).coerceAtLeast(0f),
                top = (blob[1].toFloat() / f.h - padY).coerceAtLeast(0f),
                right = ((blob[2] + 1).toFloat() / f.w + padX).coerceAtMost(1f),
                bottom = ((blob[3] + 1).toFloat() / f.h + padY).coerceAtMost(1f),
            )
        } catch (e: Throwable) {
            null
        }
    }

    /**
     * Finds the four corners of the page for perspective correction, as normalized coordinates
     * [x0,y0,...,x3,y3] (top-left, top-right, bottom-right, bottom-left), or null when the shape
     * is not convincing. Works best on a bright page lying on a darker surface (or the reverse).
     */
    suspend fun detectQuad(source: Bitmap): List<Float>? = withContext(Dispatchers.Default) {
        try {
            val f = findPageBlob(source) ?: return@withContext null
            val b = f.blob
            // b[5..12] = TL, TR, BR, BL pixel positions of the extreme points of the region.
            val q = ArrayList<Float>(8)
            for (i in 0 until 4) {
                q.add(((b[5 + i * 2] + 0.5f) / f.w).coerceIn(0f, 1f))
                q.add(((b[6 + i * 2] + 0.5f) / f.h).coerceIn(0f, 1f))
            }
            // Shoelace area (normalized) and minimum edge length must look like a real page.
            var area = 0f
            for (i in 0 until 4) {
                val j = (i + 1) % 4
                area += q[i * 2] * q[j * 2 + 1] - q[j * 2] * q[i * 2 + 1]
            }
            area = kotlin.math.abs(area) / 2f
            if (!PageQuadGeometry.isValid(q, minArea = 0.12f, minEdge = 0.1f)) return@withContext null
            q
        } catch (e: Throwable) {
            null
        }
    }

    private class PageBlob(val blob: IntArray, val w: Int, val h: Int)

    /** Shared by [detectDocument] and [detectQuad]: biggest bright (or dark) region of a small copy. */
    private fun findPageBlob(source: Bitmap): PageBlob? {
        val longest = max(source.width, source.height)
        val scale = min(1f, 320f / longest)
        val w = max(1, (source.width * scale).toInt())
        val h = max(1, (source.height * scale).toInt())
        val small = Bitmap.createScaledBitmap(source, w, h, true)
        val px = IntArray(w * h)
        small.getPixels(px, 0, w, 0, 0, w, h)
        val gray = IntArray(w * h) { luma(px[it]) }
        val threshold = otsuThreshold(gray)
        val total = (w * h).toFloat()
        for (bright in booleanArrayOf(true, false)) {
            val blob = largestBlob(gray, w, h, threshold, bright) ?: continue
            val fraction = blob[4] / total
            // Reject "everything" and "a speck": that is background, not a page.
            if (fraction < 0.15f || fraction > 0.97f) continue
            return PageBlob(blob, w, h)
        }
        return null
    }

    /** Otsu's method: the grey level that best separates dark from bright pixels. */
    private fun otsuThreshold(gray: IntArray): Int {
        val hist = IntArray(256)
        for (g in gray) hist[g.coerceIn(0, 255)]++
        val total = gray.size.toDouble()
        var sumAll = 0.0
        for (i in 0..255) sumAll += i * hist[i].toDouble()
        var sumBack = 0.0
        var weightBack = 0.0
        var bestVariance = -1.0
        var best = 128
        for (t in 0..255) {
            weightBack += hist[t]
            if (weightBack == 0.0) continue
            val weightFront = total - weightBack
            if (weightFront == 0.0) break
            sumBack += t * hist[t].toDouble()
            val meanBack = sumBack / weightBack
            val meanFront = (sumAll - sumBack) / weightFront
            val variance = weightBack * weightFront * (meanBack - meanFront) * (meanBack - meanFront)
            if (variance > bestVariance) {
                bestVariance = variance
                best = t
            }
        }
        return best
    }

    /**
     * Biggest 4-connected group of pixels brighter (or darker) than [threshold].
     * Returns [minX, minY, maxX, maxY, pixelCount, TLx, TLy, TRx, TRy, BRx, BRy, BLx, BLy] or null,
     * where the corners are the extreme points of the region along the two diagonals.
     */
    private fun largestBlob(gray: IntArray, w: Int, h: Int, threshold: Int, bright: Boolean): IntArray? {
        val visited = BooleanArray(w * h)
        val queue = IntArray(w * h)
        var best: IntArray? = null
        for (start in 0 until w * h) {
            if (visited[start] || !matches(gray[start], threshold, bright)) continue
            var head = 0
            var tail = 0
            queue[tail++] = start
            visited[start] = true
            var minX = w
            var minY = h
            var maxX = 0
            var maxY = 0
            var count = 0
            var tlS = Int.MAX_VALUE; var tlX = 0; var tlY = 0
            var brS = Int.MIN_VALUE; var brX = 0; var brY = 0
            var trD = Int.MIN_VALUE; var trX = 0; var trY = 0
            var blD = Int.MAX_VALUE; var blX = 0; var blY = 0
            while (head < tail) {
                val cur = queue[head++]
                val x = cur % w
                val y = cur / w
                count++
                if (x < minX) minX = x
                if (x > maxX) maxX = x
                if (y < minY) minY = y
                if (y > maxY) maxY = y
                val sum = x + y
                val diff = x - y
                if (sum < tlS) { tlS = sum; tlX = x; tlY = y }
                if (sum > brS) { brS = sum; brX = x; brY = y }
                if (diff > trD) { trD = diff; trX = x; trY = y }
                if (diff < blD) { blD = diff; blX = x; blY = y }
                if (x > 0 && !visited[cur - 1] && matches(gray[cur - 1], threshold, bright)) {
                    visited[cur - 1] = true; queue[tail++] = cur - 1
                }
                if (x < w - 1 && !visited[cur + 1] && matches(gray[cur + 1], threshold, bright)) {
                    visited[cur + 1] = true; queue[tail++] = cur + 1
                }
                if (y > 0 && !visited[cur - w] && matches(gray[cur - w], threshold, bright)) {
                    visited[cur - w] = true; queue[tail++] = cur - w
                }
                if (y < h - 1 && !visited[cur + w] && matches(gray[cur + w], threshold, bright)) {
                    visited[cur + w] = true; queue[tail++] = cur + w
                }
            }
            if (best == null || count > best[4]) {
                best = intArrayOf(minX, minY, maxX, maxY, count, tlX, tlY, trX, trY, brX, brY, blX, blY)
            }
        }
        return best
    }

    private fun matches(g: Int, threshold: Int, bright: Boolean): Boolean =
        if (bright) g > threshold else g <= threshold

    companion object {
        private const val MAX_EXPORT_DIM = 2480
        private const val MAX_THRESHOLD_PIXELS = 8_000_000L
    }
}
