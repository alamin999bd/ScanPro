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
import com.tom_roush.pdfbox.pdmodel.font.PDType0Font
import com.tom_roush.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState
import com.tom_roush.pdfbox.pdmodel.graphics.state.RenderingMode
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

    /**
     * Creates a scanned PDF with an invisible Unicode text layer positioned over each image.
     * OCR bounds are measured against the same EXIF-corrected bitmap dimensions used here.
     * The embedded Noto Sans Bengali font covers Bangla and Latin text.
     */
    suspend fun imagesToSearchablePdf(
        images: List<Uri>,
        ocrPages: List<OcrPage>,
        out: File,
        pageSize: Int,
        quality: Int,
        onProgress: (Int, Int) -> Unit = { _, _ -> },
    ): Int = withContext(Dispatchers.IO) {
        require(images.isNotEmpty()) { "No images" }
        require(images.size == ocrPages.size) { "OCR page count does not match the scan" }
        val doc = PDDocument()
        try {
            val bengaliFont = context.assets.open("fonts/NotoSansBengali.ttf").use {
                PDType0Font.load(doc, it)
            }
            val latinFont = context.assets.open("fonts/NotoSans-Regular.ttf").use {
                PDType0Font.load(doc, it)
            }
            images.forEachIndexed { index, uri ->
                val bitmap = decodeBitmap(uri, MAX_IMAGE_DIM)
                    ?: throw IllegalStateException("Cannot read image ${index + 1}")
                try {
                    val ocrPage = ocrPages[index]
                    require(ocrPage.imageWidth == bitmap.width && ocrPage.imageHeight == bitmap.height) {
                        "OCR image dimensions changed before PDF export on page ${index + 1}"
                    }
                    val rect = pdfRect(pageSize, bitmap)
                    val page = PDPage(rect)
                    doc.addPage(page)
                    val image = JPEGFactory.createFromImage(doc, bitmap, quality / 100f)
                    val scale = min(rect.width / bitmap.width, rect.height / bitmap.height)
                    val imageWidth = bitmap.width * scale
                    val imageHeight = bitmap.height * scale
                    val imageLeft = (rect.width - imageWidth) / 2f
                    val imageBottom = (rect.height - imageHeight) / 2f
                    PDPageContentStream(doc, page).use { stream ->
                        stream.drawImage(image, imageLeft, imageBottom, imageWidth, imageHeight)
                        val placements = ocrPage.words.mapNotNull { word ->
                            calculateOcrPdfPlacement(word, ocrPage, rect.width, rect.height)
                        }
                        if (placements.isNotEmpty()) {
                            stream.beginText()
                            stream.setRenderingMode(RenderingMode.NEITHER)
                            placements.forEachIndexed { index, placement ->
                                val runs = splitOcrTextByScript(placement.text)
                                var cursorX = placement.x
                                runs.forEach { run ->
                                    val font = if (run.usesBengaliFont) bengaliFont else latinFont
                                    stream.setFont(font, placement.fontSize)
                                    stream.setTextMatrix(
                                        com.tom_roush.pdfbox.util.Matrix.getTranslateInstance(
                                            cursorX, placement.baselineY
                                        )
                                    )
                                    stream.showText(run.text)
                                    cursorX += font.getStringWidth(run.text) / 1000f * placement.fontSize
                                }
                                // Keep word boundaries in extracted/searchable PDF text.
                                if (index < placements.lastIndex) {
                                    stream.setFont(latinFont, placement.fontSize)
                                    stream.setTextMatrix(
                                        com.tom_roush.pdfbox.util.Matrix.getTranslateInstance(
                                            cursorX, placement.baselineY
                                        )
                                    )
                                    stream.showText(" ")
                                }
                            }
                            stream.endText()
                        }
                    }
                } finally {
                    bitmap.recycle()
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

    /** Adds selectable page numbers to a new copy of the PDF. */
    suspend fun addPageNumbers(input: File, out: File): Int = withContext(Dispatchers.IO) {
        val doc = PDDocument.load(input)
        try {
            val font = PDType1Font.HELVETICA
            val total = doc.numberOfPages
            require(total > 0) { "This PDF has no pages" }
            doc.pages.forEachIndexed { index, page ->
                val box = page.cropBox
                val label = "${index + 1} / $total"
                val fontSize = 9f
                val width = font.getStringWidth(label) / 1000f * fontSize
                PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
                    cs.setNonStrokingColor(80, 80, 80)
                    cs.beginText()
                    cs.setFont(font, fontSize)
                    cs.newLineAtOffset(box.lowerLeftX + (box.width - width) / 2f, box.lowerLeftY + 18f)
                    cs.showText(label)
                    cs.endText()
                }
            }
            out.outputStream().use { doc.save(it) }
            total
        } finally { doc.close() }
    }

    /** Extracts searchable text from a PDF, returning 1-based page numbers with matching text. */
    suspend fun searchText(input: File, query: String): List<Pair<Int, String>> = withContext(Dispatchers.IO) {
        require(query.isNotBlank()) { "Enter text to search" }
        val doc = PDDocument.load(input)
        try {
            val stripper = com.tom_roush.pdfbox.text.PDFTextStripper()
            stripper.sortByPosition = true
            (1..doc.numberOfPages).mapNotNull { pageNumber ->
                stripper.startPage = pageNumber
                stripper.endPage = pageNumber
                val content = stripper.getText(doc).trim()
                if (containsPdfSearchQuery(content, query)) pageNumber to content else null
            }
        } finally { doc.close() }
    }

    /** Reads nested PDF outline entries for in-app bookmark navigation. */
    suspend fun readBookmarks(input: File): List<PdfBookmark> = withContext(Dispatchers.IO) {
        PDDocument.load(input).use { doc ->
            val outline = doc.documentCatalog.documentOutline ?: return@withContext emptyList()
            val result = ArrayList<PdfBookmark>()

            fun appendChildren(node: com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineNode) {
                var item = node.firstChild
                while (item != null) {
                    val destination = item.destination as? com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageDestination
                    val page = destination?.page
                    val pageIndex = if (page == null) -1 else doc.pages.indexOf(page)
                    if (pageIndex >= 0 && item.title.isNotBlank()) {
                        result += PdfBookmark(item.title, pageIndex + 1)
                    }
                    appendChildren(item)
                    item = item.nextSibling
                }
            }

            appendChildren(outline)
            result
        }
    }

    /** Adds a real PDF outline/bookmark pointing to the requested 1-based page. */
    suspend fun addBookmark(input: File, out: File, title: String, pageNumber: Int): Int = withContext(Dispatchers.IO) {
        require(title.isNotBlank()) { "Enter a bookmark title" }
        require(title.trim().length <= 120) { "Bookmark title must be 120 characters or fewer" }
        val doc = PDDocument.load(input)
        try {
            require(pageNumber in 1..doc.numberOfPages) { "Page must be between 1 and ${doc.numberOfPages}" }
            val outline = doc.documentCatalog.documentOutline ?: com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDDocumentOutline().also {
                doc.documentCatalog.documentOutline = it
            }
            val item = com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem()
            item.title = title.trim()
            val destination = com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageFitDestination()
            destination.page = doc.getPage(pageNumber - 1)
            item.destination = destination
            outline.addLast(item)
            outline.openNode()
            out.outputStream().use { doc.save(it) }
            doc.numberOfPages
        } finally { doc.close() }
    }

    /** Adds a PDF text-note annotation or a yellow rectangular highlight to a page. Coordinates are points from the page's lower-left corner. */
    suspend fun addPageMarkup(
        input: File,
        out: File,
        pageNumber: Int,
        kind: String,
        note: String,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
    ): Int = withContext(Dispatchers.IO) {
        require(kind == "NOTE" || kind == "HIGHLIGHT") { "Unsupported annotation type" }
        val doc = PDDocument.load(input)
        try {
                require(pageNumber in 1..doc.numberOfPages) { "Page must be between 1 and ${doc.numberOfPages}" }
                val page = doc.getPage(pageNumber - 1)
                // UI coordinates are relative to the visible CropBox; convert once to PDF user space.
                val box = page.cropBox
                val geometry = createPdfMarkupGeometry(
                    cropLeft = box.lowerLeftX,
                    cropBottom = box.lowerLeftY,
                    cropWidth = box.width,
                    cropHeight = box.height,
                    x = x,
                    y = y,
                    width = width,
                    height = height,
                )
                val left = geometry.left
                val bottom = geometry.bottom
            val rect = PDRectangle(left, bottom, geometry.width, geometry.height)
            if (kind == "NOTE") {
                require(note.isNotBlank()) { "Enter note text" }
                val annotation = com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationText()
                // A sticky note uses a compact icon-sized rectangle; the entered area
                // locates it without covering a large portion of the page.
                annotation.rectangle = PDRectangle(left, bottom, min(width, 24f), min(height, 24f))
                annotation.contents = note.trim()
                annotation.titlePopup = "Document Scanner"
                page.annotations.add(annotation)
            } else {
                val annotation = com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationTextMarkup(
                    com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationTextMarkup.SUB_TYPE_HIGHLIGHT
                )
                annotation.rectangle = rect
                annotation.quadPoints = geometry.quadPoints
                annotation.color = com.tom_roush.pdfbox.pdmodel.graphics.color.PDColor(
                    floatArrayOf(1f, 1f, 0f),
                    com.tom_roush.pdfbox.pdmodel.graphics.color.PDDeviceRGB.INSTANCE
                )
                page.annotations.add(annotation)
            }
            out.outputStream().use { doc.save(it) }
            doc.numberOfPages
        } finally { doc.close() }
    }

    /** Reads our supported note/highlight annotations for the in-app viewer overlay. */
    suspend fun readPageMarkups(input: File): List<List<PdfPageMarkup>> = withContext(Dispatchers.IO) {
        PDDocument.load(input).use { doc ->
            doc.pages.map { page ->
                val crop = page.cropBox
                page.annotations.mapNotNull { annotation ->
                    val rect = annotation.rectangle ?: return@mapNotNull null
                    when (annotation) {
                        is com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationTextMarkup -> {
                            if (annotation.subtype != com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationTextMarkup.SUB_TYPE_HIGHLIGHT) {
                                null
                            } else {
                                normalizePdfMarkupBounds(
                                    cropLeft = crop.lowerLeftX,
                                    cropBottom = crop.lowerLeftY,
                                    cropWidth = crop.width,
                                    cropHeight = crop.height,
                                    rectLeft = rect.lowerLeftX,
                                    rectBottom = rect.lowerLeftY,
                                    rectRight = rect.upperRightX,
                                    rectTop = rect.upperRightY,
                                    rotation = page.rotation,
                                    kind = "HIGHLIGHT",
                                )
                            }
                        }
                        is com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationText -> {
                            normalizePdfMarkupBounds(
                                cropLeft = crop.lowerLeftX,
                                cropBottom = crop.lowerLeftY,
                                cropWidth = crop.width,
                                cropHeight = crop.height,
                                rectLeft = rect.lowerLeftX,
                                rectBottom = rect.lowerLeftY,
                                rectRight = rect.upperRightX,
                                rectTop = rect.upperRightY,
                                rotation = page.rotation,
                                kind = "NOTE",
                                note = annotation.contents.orEmpty(),
                            )
                        }
                        else -> null
                    }
                }
            }
        }
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
                        var file: File? = null
                        try {
                            bmp.eraseColor(android.graphics.Color.WHITE)
                            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            val createdFile = newFile()
                            file = createdFile
                            createdFile.outputStream().use { stream ->
                                check(bmp.compress(Bitmap.CompressFormat.JPEG, quality, stream)) {
                                    "Could not write rendered page ${i + 1}"
                                }
                            }
                            result.add(createdFile)
                        } catch (error: Throwable) {
                            file?.delete()
                            throw error
                        } finally {
                            bmp.recycle()
                        }
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
     * Rewrites a PDF without rasterizing its pages, preserving the existing searchable text.
     * This is a safe text-preserving rewrite, not lossy image recompression: the output may be
     * the same size or larger. The caller must compare file sizes and keep the original unless
     * the rewritten copy is strictly smaller. Page count and extracted text are verified below.
     */
    suspend fun optimizePreservingText(input: File, out: File): Int = withContext(Dispatchers.IO) {
        fun extractText(file: File): Pair<Int, String> {
            PDDocument.load(file).use { document ->
                val stripper = com.tom_roush.pdfbox.text.PDFTextStripper().apply { sortByPosition = true }
                return document.numberOfPages to stripper.getText(document)
            }
        }

        val before = extractText(input)
        val document = PDDocument.load(input)
        try {
            out.outputStream().use { document.save(it) }
        } finally {
            document.close()
        }

        try {
            val after = extractText(out)
            check(after.first == before.first) { "Optimization changed the PDF page count" }
            check(after.second == before.second) { "Optimization changed searchable PDF text; output was rejected" }
            after.first
        } catch (error: Exception) {
            out.delete()
            throw error
        }
    }

    /** Writes a password-protected (AES/RC4 128-bit) copy. The same password opens and owns it. */
    suspend fun protect(input: File, out: File, password: String): Int = withContext(Dispatchers.IO) {
        require(password.length >= 4) { "Password must be at least 4 characters" }
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
        require(password.isNotBlank()) { "Enter the current password" }
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
        require(text.isNotBlank()) { "Enter watermark text" }
        require(text.length <= 200) { "Watermark text must be 200 characters or fewer" }
        val doc = PDDocument.load(input)
        var watermarkBitmap: Bitmap? = null
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
            val picture = if (latin) null else {
                val rendered = renderTextBitmap(text)
                watermarkBitmap = rendered
                LosslessFactory.createFromImage(doc, rendered)
            }
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
            watermarkBitmap?.takeUnless { it.isRecycled }?.recycle()
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
        require(position in 0..2) { "Choose a valid signature position" }
        require(signature.width > 0 && signature.height > 0 && !signature.isRecycled) { "The signature image is invalid" }
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
            val margin = 36f
            val targetW = min(box.width * 0.28f, box.width - margin * 2f)
            val maxHeight = (box.height - 72f).coerceAtLeast(1f)
            val scale = min(targetW / signature.width, maxHeight / signature.height)
            val drawW = signature.width * scale
            val drawH = signature.height * scale
            val x = when (position) {
                0 -> box.lowerLeftX + margin
                1 -> box.lowerLeftX + (box.width - drawW) / 2f
                else -> box.lowerLeftX + box.width - drawW - margin
            }
            val y = box.lowerLeftY + 48f
            PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
                cs.drawImage(image, x, y, drawW, drawH)
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
        const val MAX_IMAGE_DIM = 2480 // ~A4 at 300 dpi
    }
}

/**
 * Parses "1-3, 5, 8-10" into [1,2,3,5,8,9,10] (1-based, validated against [pageCount]).
 * Returns null when the text is empty or malformed.
 */
fun parsePageRanges(text: String, pageCount: Int): List<Int>? {
    val parts = text.split(',').map { it.trim() }
    if (parts.isEmpty() || parts.any { it.isEmpty() }) return null
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
    if (out.distinct().size != out.size) return null
    return out
}

/** Parses a complete 1-based page order; every page must appear exactly once. */
fun parsePageOrder(text: String, pageCount: Int): List<Int>? {
    if (pageCount <= 0 || text.isBlank()) return null
    val parts = text.split(',').map { it.trim() }
    if (parts.size != pageCount || parts.any { it.isEmpty() }) return null
    val pages = parts.map { it.toIntOrNull() ?: return null }
    if (pages.any { it !in 1..pageCount } || pages.distinct().size != pageCount) return null
    return pages
}

/** A top-level PDF outline entry and its 1-based destination page. */
data class PdfBookmark(val title: String, val pageNumber: Int)
