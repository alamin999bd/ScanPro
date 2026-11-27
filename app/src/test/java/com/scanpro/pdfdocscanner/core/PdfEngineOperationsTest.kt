package com.scanpro.pdfdocscanner.core

import android.content.Context
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.font.PDType0Font
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class PdfEngineOperationsTest {
    private lateinit var context: Context
    private lateinit var engine: PdfEngine
    private lateinit var dir: File

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        context.assets.open("com/tom_roush/fontbox/resources/cmap/Identity-H").use { cmap ->
            assertTrue("PDFBox Identity-H CMap must be available to Robolectric", cmap.read() != -1)
        }
        PDFBoxResourceLoader.init(context)
        engine = PdfEngine(context)
        dir = File(context.cacheDir, "pdf-engine-tests").apply {
            deleteRecursively()
            mkdirs()
        }
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    @Test
    fun mergeExtractDeleteAndReorderPreserveExpectedPageContent() = runBlocking {
        val first = createPdf("ONE", "TWO", "THREE")
        val second = createPdf("FOUR")

        val merged = output("merged.pdf")
        assertEquals(4, engine.merge(listOf(first, second), merged))
        assertEquals(listOf("ONE", "TWO", "THREE", "FOUR"), pageTexts(merged))

        val extracted = output("extracted.pdf")
        assertEquals(2, engine.extractPages(merged, listOf(3, 1), extracted))
        assertEquals(listOf("THREE", "ONE"), pageTexts(extracted))

        val deleted = output("deleted.pdf")
        assertEquals(3, engine.deletePages(merged, listOf(2), deleted))
        assertEquals(listOf("ONE", "THREE", "FOUR"), pageTexts(deleted))

        val reordered = output("reordered.pdf")
        assertEquals(3, engine.reorderPages(first, listOf(3, 1, 2), reordered))
        assertEquals(listOf("THREE", "ONE", "TWO"), pageTexts(reordered))
    }

    @Test
    fun rotateChangesPageRotationWithoutChangingText() = runBlocking {
        val source = createPdf("ROTATE-ME")
        val rotated = output("rotated.pdf")
        assertEquals(1, engine.rotatePages(source, 90, rotated))
        PDDocument.load(rotated).use { assertEquals(90, it.getPage(0).rotation) }
        assertEquals(listOf("ROTATE-ME"), pageTexts(rotated))
    }

    @Test
    fun lockAndUnlockRequireTheCorrectPasswordAndPreservePages() = runBlocking {
        val source = createPdf("PRIVATE-TEXT", "SECOND-PAGE")
        val locked = output("locked.pdf")
        assertEquals(2, engine.protect(source, locked, "test-pass"))
        assertTrue(engine.isPasswordProtected(locked))

        val unlocked = output("unlocked.pdf")
        assertEquals(2, engine.unlock(locked, unlocked, "test-pass"))
        assertFalse(engine.isPasswordProtected(unlocked))
        assertEquals(listOf("PRIVATE-TEXT", "SECOND-PAGE"), pageTexts(unlocked))
    }

    @Test
    fun watermarkPageNumbersAndBookmarkAreWrittenToThePdf() = runBlocking {
        val source = createPdf("VISIBLE-CONTENT", "SECOND-CONTENT")
        val watermarked = output("watermarked.pdf")
        assertEquals(2, engine.watermark(source, watermarked, "QA WATERMARK"))
        val watermarkedTexts = pageTexts(watermarked, sortByPosition = false)
        assertTrue(
            "Expected complete watermark text on every page; extracted text: $watermarkedTexts",
            watermarkedTexts.all { it.filterNot(Char::isWhitespace).contains("QAWATERMARK") }
        )

        val numbered = output("numbered.pdf")
        assertEquals(2, engine.addPageNumbers(watermarked, numbered))
        assertTrue(pageTexts(numbered).all { it.contains("/") })

        val bookmarked = output("bookmarked.pdf")
        assertEquals(2, engine.addBookmark(numbered, bookmarked, "Second page", 2))
        assertEquals(listOf(PdfBookmark("Second page", 2)), engine.readBookmarks(bookmarked))
    }


    @Test
    fun optimizePreservesSelectableTextAndPageCount() = runBlocking {
        val source = createPdf("SEARCHABLE-ALPHA", "SEARCHABLE-BETA")
        val optimized = output("optimized.pdf")

        assertEquals(2, engine.optimizePreservingText(source, optimized))
        assertEquals(listOf("SEARCHABLE-ALPHA", "SEARCHABLE-BETA"), pageTexts(optimized))
        assertTrue(optimized.exists() && optimized.length() > 0L)
    }

    @Test
    fun pageNotesAndHighlightsRoundTripThroughPdfAnnotations() = runBlocking {
        val source = createPdf("ANNOTATION-TARGET")
        val withNote = output("with-note.pdf")
        assertEquals(
            1,
            engine.addPageMarkup(
                input = source, out = withNote, pageNumber = 1, kind = "NOTE",
                note = "Review this paragraph", x = 60f, y = 100f, width = 120f, height = 32f,
            ),
        )

        val withHighlight = output("with-highlight.pdf")
        assertEquals(
            1,
            engine.addPageMarkup(
                input = withNote, out = withHighlight, pageNumber = 1, kind = "HIGHLIGHT",
                note = "", x = 60f, y = 100f, width = 120f, height = 32f,
            ),
        )

        val markups = engine.readPageMarkups(withHighlight).single()
        assertEquals(2, markups.size)
        assertTrue(markups.any { it.kind == "NOTE" && it.note == "Review this paragraph" })
        assertTrue(markups.any { it.kind == "HIGHLIGHT" })
    }

    private fun createPdf(vararg labels: String): File {
        val file = output("fixture-${labels.size}-${labels.first()}.pdf")
        PDDocument().use { document ->
            labels.forEach { label ->
                val page = PDPage()
                document.addPage(page)
                val font = requireNotNull(javaClass.getResourceAsStream("/NotoSans-Regular.ttf")) {
                    "NotoSans-Regular.ttf test fixture resource is missing"
                }.use { fontStream ->
                    PDType0Font.load(document, fontStream)
                }
                PDPageContentStream(document, page).use { stream ->
                    stream.beginText()
                    stream.setFont(font, 16f)
                    stream.newLineAtOffset(72f, 720f)
                    stream.showText(label)
                    stream.endText()
                }
            }
            file.outputStream().use { document.save(it) }
        }
        return file
    }

    private fun pageTexts(file: File, sortByPosition: Boolean = true): List<String> =
        PDDocument.load(file).use { document ->
            (1..document.numberOfPages).map { page ->
                PDFTextStripper().apply {
                    startPage = page
                    endPage = page
                    this.sortByPosition = sortByPosition
                }.getText(document).trim()
            }
        }

    private fun output(name: String) = File(dir, name)
}