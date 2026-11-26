package com.scanpro.pdfdocscanner.core

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.media.ExifInterface
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max
import kotlin.math.min

object PageSizes {
    const val A4 = 0
    const val LETTER = 1
    const val FIT_IMAGE = 2
}

/** All PDF work. Everything runs offline on-device and off the main thread. */
class PdfEngine(private val context: Context) {

    // ---------- Images -> PDF ----------

    /**
     * Builds a PDF with one page per image. JPEG-compressed with [quality] (60..100).
     * [onProgress] is called with (done, total) after each page.
     */
    suspend fun imagesToPdf(
        images: List<Uri>,
        out: File,
        pageSize: Int,
        quality: Int,
        onProgress: (Int, Int) -> Unit = { _, _ -> },
    ): Int = withContext(Dispatchers.IO) {
        require(images.isNotEmpty()) { "No images" }
        val doc = PDDocument()
        try {
            images.forEachIndexed { index, uri ->
                val bmp = decodeBitmap(uri, MAX_IMAGE_DIM)
                    ?: throw IllegalStateException("Cannot read image ${index + 1}")
                try {
                    addImagePage(doc, bmp, pageSize, quality)
                } finally {
                    bmp.recycle()
                }
                onProgress(index + 1, images.size)
            }
            out.outputStream().use { doc.save(it) }
        } finally {
            doc.close()
        }
        images.size
    }

    /** Places two images (e.g. ID card front/back) on one page, stacked, fit-to-width. */
    suspend fun idCardToPdf(
        front: Uri,
        back: Uri?,
        out: File,
        pageSize: Int,
        quality: Int,
    ) = withContext(Dispatchers.IO) {
        val rect = pdfRect(pageSize, null)
        val doc = PDDocument()
        try {
            val page = PDPage(rect)
            doc.addPage(page)
            val uris = listOfNotNull(front, back)
            // Copy-machine style layout: cards centred near the top, one under the other,
            // at a readable fixed width (about 62% of the page) so front and back match.
            val topMargin = 96f
            val gap = 32f
            val cardW = rect.width * 0.62f
            var cursorTop = rect.height - topMargin
            PDPageContentStream(doc, page).use { cs ->
                uris.forEach { uri ->
                    val bmp = decodeBitmap(uri, MAX_IMAGE_DIM) ?: throw IllegalStateException("Cannot read image")
                    try {
                        val img = JPEGFactory.createFromImage(doc, bmp, quality / 100f)
                        // Uncropped tall photos are shrunk so both sides always fit on the page.
                        val maxH = (rect.height - topMargin * 2 - gap * (uris.size - 1)) / uris.size
                        var w = cardW
                        var h = cardW * bmp.height / bmp.width
                        if (h > maxH) {
                            h = maxH
                            w = h * bmp.width / bmp.height
                        }
                        val x = (rect.width - w) / 2f
                        cs.drawImage(img, x, cursorTop - h, w, h)
                        cursorTop -= h + gap
                    } finally {
                        bmp.recycle()
                    }
                }
            }
            out.outputStream().use { doc.save(it) }
        } finally {
            doc.close()
        }
    }

    private fun addImagePage(doc: PDDocument, bmp: Bitmap, pageSize: Int, quality: Int) {
        val rect = pdfRect(pageSize, bmp)
        val page = PDPage(rect)
        doc.addPage(page)
        val img = JPEGFactory.createFromImage(doc, bmp, quality / 100f)
        val scale = min(rect.width / bmp.width, rect.height / bmp.height)
        val w = bmp.width * scale
        val h = bmp.height * scale
        PDPageContentStream(doc, page).use { cs ->
            cs.drawImage(img, (rect.width - w) / 2f, (rect.height - h) / 2f, w, h)
        }
    }

    private fun pdfRect(pageSize: Int, bmp: Bitmap?): PDRectangle = when {
        pageSize == PageSizes.LETTER -> PDRectangle.LETTER
        pageSize == PageSizes.FIT_IMAGE && bmp != null -> {
            // Keep the page at a sensible size (max ~842pt on the long side).
            val s = min(1f, 842f / max(bmp.width, bmp.height))
            PDRectangle(bmp.width * s, bmp.height * s)
        }
        else -> PDRectangle.A4
    }

    // ---------- Merge / Extract ----------

    suspend fun merge(inputs: List<File>, out: File): Int = withContext(Dispatchers.IO) {
        require(inputs.size >= 2) { "Need at least two PDFs" }
        val sources = ArrayList<PDDocument>()
        val dest = PDDocument()
        try {
            var pages = 0
            for (f in inputs) {
                val src = PDDocument.load(f)
                sources.add(src)
                for (page in src.pages) {
                    dest.importPage(page)
                    pages++
                }
            }
            out.outputStream().use { dest.save(it) }
            pages
        } finally {
            dest.close()
            sources.forEach { runCatching { it.close() } }
        }
    }

    /** Copies the given 1-based [pages] (in order) into a new PDF. */
    suspend fun extractPages(input: File, pages: List<Int>, out: File): Int = withContext(Dispatchers.IO) {
        require(pages.isNotEmpty()) { "No pages selected" }
        val src = PDDocument.load(input)
        val dest = PDDocument()
        try {
            val total = src.numberOfPages
            pages.forEach { p ->
                require(p in 1..total) { "Page $p is out of range (1-$total)" }
                dest.importPage(src.getPage(p - 1))
            }
            out.outputStream().use { dest.save(it) }
            pages.size
        } finally {
            dest.close()
            src.close()
        }
    }

    suspend fun pageCount(file: File): Int = withContext(Dispatchers.IO) {
        PDDocument.load(file).use { it.numberOfPages }
    }

    /** Creates a copy with the selected 1-based pages removed. At least one page must remain. */
    suspend fun deletePages(input: File, pagesToDelete: List<Int>, out: File): Int = withContext(Dispatchers.IO) {
        require(pagesToDelete.isNotEmpty()) { "Select at least one page to delete" }
        val src = PDDocument.load(input)
        try {
            val total = src.numberOfPages
            require(pagesToDelete.all { it in 1..total }) { "A selected page is out of range" }
            require(pagesToDelete.size < total) { "A PDF must keep at least one page" }
            pagesToDelete.distinct().sortedDescending().forEach { src.removePage(it - 1) }
            out.outputStream().use { src.save(it) }
            src.numberOfPages
        } finally { src.close() }
    }

    /** Creates a copy in the exact 1-based page order supplied by the user. */
    suspend fun reorderPages(input: File, orderedPages: List<Int>, out: File): Int = withContext(Dispatchers.IO) {
        require(orderedPages.isNotEmpty()) { "Enter the page order" }
        val src = PDDocument.load(input)
        val dest = PDDocument()
        try {
            val total = src.numberOfPages
            require(orderedPages.size == total && orderedPages.toSet().size == total &&
                orderedPages.all { it in 1..total }) { "Enter every page exactly once, e.g. 3,1,2" }
            orderedPages.forEach { dest.importPage(src.getPage(it - 1)) }
            out.outputStream().use { dest.save(it) }
            dest.numberOfPages
        } finally { dest.close(); src.close() }
    }

    /** Rotates every page by the requested clockwise quarter-turns, keeping all other page data. */
    suspend fun rotatePages(input: File, degrees: Int, out: File): Int = withContext(Dispatchers.IO) {
        require(degrees in listOf(90, 180, 270)) { "Choose 90, 180 or 270 degrees" }
        val doc = PDDocument.load(input)
        try {
            doc.pages.forEach { page -> page.rotation = (page.rotation + degrees) % 360 }
            out.outputStream().use { doc.save(it) }
            doc.numberOfPages
        } finally { doc.close() }
    }

    // ---------- PDF -> Images ----------

    /** Renders every page to a JPEG file created by [newFile]. Returns the created files. */
    suspend fun pdfToImages(
        input: File,
        newFile: () -> File,
        targetWidthPx: Int = 1600,
        quality: Int = 90,
        onProgress: (Int, Int) -> Unit = { _, _ -> },
    ): List<File> = withContext(Dispatchers.IO) {
        val result = ArrayList<File>()
        ParcelFileDescriptor.open(input, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
            PdfRenderer(pfd).use { renderer ->
                val total = renderer.pageCount
                for (i in 0 until total) {
                    renderer.openPage(i).use { page ->
                        val scale = targetWidthPx.toFloat() / page.width
                        val bmp = Bitmap.createBitmap(
                            targetWidthPx, (page.height * scale).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888
                        )
                        bmp.eraseColor(android.graphics.Color.WHITE)
                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        val file = newFile()
                        file.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, quality, it) }
                        bmp.recycle()
                        result.add(file)
                    }
                    onProgress(i + 1, total)
                }
            }
        }
        result
    }

    /** Renders the first page as a small bitmap for list thumbnails. Returns null on failure. */
    suspend fun renderThumbnail(input: File, widthPx: Int): Bitmap? = withContext(Dispatchers.IO) {
        runCatching {
            ParcelFileDescriptor.open(input, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    if (renderer.pageCount == 0) return@use null
                    renderer.openPage(0).use { page ->
                        val scale = widthPx.toFloat() / page.width
                        val bmp = Bitmap.createBitmap(
                            widthPx, (page.height * scale).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888
                        )
                        bmp.eraseColor(android.graphics.Color.WHITE)
                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        bmp
                    }
                }
            }
        }.getOrNull()
    }

    // ---------- Compress / Lock / Unlock / Watermark ----------

    /**
     * Shrinks a PDF by re-drawing every page as a JPEG [maxWidthPx] wide.
     * Trade-off: text becomes part of the picture (it can no longer be selected or searched).
     */
    suspend fun compress(
        input: File,
        out: File,
        quality: Int,
        maxWidthPx: Int,
        onProgress: (Int, Int) -> Unit = { _, _ -> },
    ): Int = withContext(Dispatchers.IO) {
        val doc = PDDocument()
        try {
            ParcelFileDescriptor.open(input, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    val total = renderer.pageCount
                    for (i in 0 until total) {
                        renderer.openPage(i).use { page ->
                            val ptW = page.width.toFloat()
                            val ptH = page.height.toFloat()
                            val scale = maxWidthPx / ptW
                            val bmp = Bitmap.createBitmap(
                                maxWidthPx, (ptH * scale).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888
                            )
                            bmp.eraseColor(android.graphics.Color.WHITE)
                            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            val pdPage = PDPage(PDRectangle(ptW, ptH))
                            doc.addPage(pdPage)
                            val img = JPEGFactory.createFromImage(doc, bmp, quality / 100f)
                            PDPageContentStream(doc, pdPage).use { cs -> cs.drawImage(img, 0f, 0f, ptW, ptH) }
                            bmp.recycle()
                        }
                        onProgress(i + 1, total)
                    }
                }
            }
            out.outputStream().use { doc.save(it) }
            doc.numberOfPages
        } finally {
            doc.close()
        }
    }

    /** Writes a password-protected (AES/RC4 128-bit) copy. The same password opens and owns it. */
    suspend fun protect(input: File, out: File, password: String): Int = withContext(Dispatchers.IO) {
        val doc = PDDocument.load(input)
        try {
            val permission = AccessPermission()
            permission.setCanModify(false)
            val policy = StandardProtectionPolicy(password, password, permission)
            policy.setEncryptionKeyLength(128)
            doc.protect(policy)
            out.outputStream().use { doc.save(it) }
            doc.numberOfPages
        } finally {
            doc.close()
        }
    }

    /** True when the file is a PDF that needs a password to open. */
    fun isPasswordProtected(file: File): Boolean =
        try {
            PDDocument.load(file).close()
            false
        } catch (e: Exception) {
            e.javaClass.simpleName == "InvalidPasswordException"
        }

    /** Writes an unprotected copy of a password-protected PDF. Throws if the password is wrong. */
    suspend fun unlock(input: File, out: File, password: String): Int = withContext(Dispatchers.IO) {
        val doc = PDDocument.load(input, password)
        try {
            doc.setAllSecurityToBeRemoved(true)
            out.outputStream().use { doc.save(it) }
            doc.numberOfPages
        } finally {
            doc.close()
        }
    }

    /**
     * Stamps [text] diagonally across every page in light grey. Plain Latin text uses the built-in
     * PDF font (stays real text). Any other script, such as Bangla, is drawn with the phone's own
     * fonts and stamped as a picture, because the PDF built-in fonts cannot show those letters.
     */
    suspend fun watermark(input: File, out: File, text: String): Int = withContext(Dispatchers.IO) {
        val doc = PDDocument.load(input)
        try {
            val font = PDType1Font.HELVETICA_BOLD
            val latin = try {
                font.encode(text)
                true
            } catch (e: Exception) {
                false
            }
            val gs = PDExtendedGraphicsState()
            gs.setNonStrokingAlphaConstant(0.25f)
            val unitWidth = if (latin) font.getStringWidth(text) / 1000f else 0f
            val picture = if (latin) null else LosslessFactory.createFromImage(doc, renderTextBitmap(text))
            val aspect = if (picture == null) 1f else picture.height.toFloat() / picture.width
            for (page in doc.pages) {
                val box = page.mediaBox
                val diagonal = Math.hypot(box.width.toDouble(), box.height.toDouble()).toFloat()
                PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
                    cs.setGraphicsStateParameters(gs)
                    if (picture == null) {
                        val fontSize = min(min(box.width, box.height) / 8f, diagonal * 0.8f / unitWidth)
                        val textWidth = unitWidth * fontSize
                        val half = textWidth / 2f * 0.7071f
                        cs.setNonStrokingColor(110, 110, 110)
                        cs.beginText()
                        cs.setFont(font, fontSize)
                        cs.setTextMatrix(
                            com.tom_roush.pdfbox.util.Matrix.getRotateInstance(
                                Math.toRadians(45.0), box.width / 2f - half, box.height / 2f - half
                            )
                        )
                        cs.showText(text)
                        cs.endText()
                    } else {
                        // Picture of the text, rotated 45 degrees around the page centre.
                        val w = min(diagonal * 0.7f, min(box.width, box.height) * 0.95f / (0.7071f * (1f + aspect)))
                        val h = w * aspect
                        val cx = box.lowerLeftX + box.width / 2f
                        val cy = box.lowerLeftY + box.height / 2f
                        val tx = cx - (w / 2f * 0.7071f - h / 2f * 0.7071f)
                        val ty = cy - (w / 2f * 0.7071f + h / 2f * 0.7071f)
                        cs.saveGraphicsState()
                        cs.transform(com.tom_roush.pdfbox.util.Matrix.getRotateInstance(Math.toRadians(45.0), tx, ty))
                        cs.drawImage(picture, 0f, 0f, w, h)
                        cs.restoreGraphicsState()
                    }
                }
            }
            out.outputStream().use { doc.save(it) }
            doc.numberOfPages
        } finally {
            doc.close()
        }
    }

    /** Grey text on a transparent bitmap, drawn with the system fonts (handles Bangla and other scripts). */
    private fun renderTextBitmap(text: String): Bitmap {
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.rgb(110, 110, 110)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            textSize = 160f
        }
        val measured = paint.measureText(text)
        if (measured > 3000f) paint.textSize = 160f * 3000f / measured
        val fm = paint.fontMetrics
        val pad = 24
        val w = kotlin.math.ceil(paint.measureText(text)).toInt() + pad * 2
        val h = kotlin.math.ceil(fm.descent - fm.ascent).toInt() + pad * 2
        val bmp = Bitmap.createBitmap(max(1, w), max(1, h), Bitmap.Config.ARGB_8888)
        Canvas(bmp).drawText(text, pad.toFloat(), pad - fm.ascent, paint)
        return bmp
    }

    /**
     * Places a drawn signature (a bitmap with transparent background) on one page.
     * [position]: 0 = bottom-left, 1 = bottom-centre, 2 = bottom-right. [pageNumber] is 1-based.
     * This is a picture of a signature, not a legally certified digital signature.
     */
    suspend fun sign(
        input: File,
        out: File,
        signature: Bitmap,
        pageNumber: Int,
        position: Int,
    ): Int = withContext(Dispatchers.IO) {
        val doc = PDDocument.load(input)
        try {
            if (pageNumber < 1 || pageNumber > doc.numberOfPages) {
                throw IllegalArgumentException("Choose a page between 1 and ${doc.numberOfPages}.")
            }
            val page = doc.getPage(pageNumber - 1)
            if (page.rotation % 360 != 0) {
                throw IllegalArgumentException("This page is rotated, so the signature cannot be placed reliably.")
            }
            val box = page.mediaBox
            val image = LosslessFactory.createFromImage(doc, signature)
            val targetW = box.width * 0.28f
            val targetH = targetW * signature.height / signature.width
            val margin = 36f
            val x = when (position) {
                0 -> box.lowerLeftX + margin
                1 -> box.lowerLeftX + (box.width - targetW) / 2f
                else -> box.lowerLeftX + box.width - targetW - margin
            }
            val y = box.lowerLeftY + 48f
            PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
                cs.drawImage(image, x, y, targetW, targetH)
            }
            out.outputStream().use { doc.save(it) }
            doc.numberOfPages
        } finally {
            doc.close()
        }
    }

    // ---------- Bitmap decoding ----------

    /** Decodes [uri] down-sampled to at most [maxDim] px and applies EXIF rotation. */
    fun decodeBitmap(uri: Uri, maxDim: Int): Bitmap? {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / sample > maxDim * 2) sample *= 2
        var decoded: Bitmap? = null
        var attempts = 0
        while (decoded == null && attempts < 3) {
            val opts = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            try {
                decoded = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
                    ?: return null
            } catch (oom: OutOfMemoryError) {
                // Very large photo on a low-memory phone: retry at half the resolution.
                sample *= 2
                attempts++
            }
        }
        var bmp: Bitmap = decoded ?: return null

        val longest = max(bmp.width, bmp.height)
        if (longest > maxDim) {
            val s = maxDim.toFloat() / longest
            val scaled = Bitmap.createScaledBitmap(bmp, (bmp.width * s).toInt(), (bmp.height * s).toInt(), true)
            if (scaled !== bmp) bmp.recycle()
            bmp = scaled
        }

        val orientation = runCatching {
            resolver.openInputStream(uri)?.use {
                ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            }
        }.getOrNull() ?: ExifInterface.ORIENTATION_NORMAL

        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            else -> return flattenAlpha(bmp)
        }
        val rotated = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, matrix, true)
        if (rotated !== bmp) bmp.recycle()
        return flattenAlpha(rotated)
    }

    /** JPEG has no alpha: paint transparent PNGs onto white so they do not turn black. */
    private fun flattenAlpha(bmp: Bitmap): Bitmap {
        if (!bmp.hasAlpha()) return bmp
        val flat = Bitmap.createBitmap(bmp.width, bmp.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(flat)
        canvas.drawColor(android.graphics.Color.WHITE)
        canvas.drawBitmap(bmp, 0f, 0f, null)
        bmp.recycle()
        return flat
    }

    companion object {
        private const val MAX_IMAGE_DIM = 2480 // ~A4 at 300 dpi
    }
}

/**
 * Parses "1-3, 5, 8-10" into [1,2,3,5,8,9,10] (1-based, validated against [pageCount]).
 * Returns null when the text is empty or malformed.
 */
fun parsePageRanges(text: String, pageCount: Int): List<Int>? {
    val parts = text.split(',').map { it.trim() }.filter { it.isNotEmpty() }
    if (parts.isEmpty()) return null
    val out = ArrayList<Int>()
    for (part in parts) {
        if (part.contains('-')) {
            val (a, b) = part.split('-', limit = 2).map { it.trim().toIntOrNull() ?: return null }
            if (a < 1 || b > pageCount || a > b) return null
            for (p in a..b) out.add(p)
        } else {
            val p = part.toIntOrNull() ?: return null
            if (p !in 1..pageCount) return null
            out.add(p)
        }
    }
    return out
}
