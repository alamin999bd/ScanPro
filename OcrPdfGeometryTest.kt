package com.scanpro.pdfdocscanner.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OcrPdfGeometryTest {
    @Test
    fun mapsPortraitImageIntoLetterboxedPdfPage() {
        val page = OcrPage(1000, 2000, emptyList())
        val word = OcrWord("বাংলা", 100, 200, 300, 260, 95)
        val result = calculateOcrPdfPlacement(word, page, 595f, 842f)
        requireNotNull(result)
        assertEquals("বাংলা", result.text)
        assertEquals(129.1f, result.x, 0.02f)
        assertEquals(735.57f, result.baselineY, 0.05f)
        assertEquals(25.26f, result.fontSize, 0.02f)
    }

    @Test
    fun mapsLandscapeImageWithVerticalPadding() {
        val page = OcrPage(2000, 1000, emptyList())
        val word = OcrWord("Hello", 100, 100, 300, 200, 90)
        val result = calculateOcrPdfPlacement(word, page, 595f, 842f)
        requireNotNull(result)
        assertEquals(29.75f, result.x, 0.02f)
        assertEquals(513.82f, result.baselineY, 0.05f)
        assertEquals(29.75f, result.fontSize, 0.02f)
    }

    @Test
    fun rejectsInvalidPageSizesAndEmptyWords() {
        val page = OcrPage(100, 100, emptyList())
        val word = OcrWord("word", 10, 10, 30, 30, 90)
        assertNull(calculateOcrPdfPlacement(word, page, 0f, 100f))
        assertNull(calculateOcrPdfPlacement(word, OcrPage(0, 100, emptyList()), 100f, 100f))
        assertNull(calculateOcrPdfPlacement(word, page, Float.NaN, 100f))
        assertNull(calculateOcrPdfPlacement(word, page, 100f, Float.POSITIVE_INFINITY))
        assertNull(calculateOcrPdfPlacement(word.copy(text = " "), page, 100f, 100f))
    }

    @Test
    fun rejectsBoundsOutsideImageAfterClamping() {
        val page = OcrPage(100, 100, emptyList())
        val word = OcrWord("outside", 120, 10, 140, 30, 80)
        assertNull(calculateOcrPdfPlacement(word, page, 200f, 200f))
    }

    @Test
    fun clampsPartiallyOutOfBoundsWordsToImageEdges() {
        val page = OcrPage(100, 100, emptyList())
        val word = OcrWord("edge", -10, 10, 20, 30, 80)
        val result = calculateOcrPdfPlacement(word, page, 200f, 200f)
        requireNotNull(result)
        assertEquals(0f, result.x, 0.01f)
        assertTrue(result.fontSize > 0f)
    }

    @Test
    fun rejectsZeroAreaAndReversedWordBounds() {
        val page = OcrPage(100, 100, emptyList())
        assertNull(calculateOcrPdfPlacement(OcrWord("zero", 10, 10, 10, 30, 90), page, 200f, 200f))
        assertNull(calculateOcrPdfPlacement(OcrWord("reversed", 30, 10, 10, 30, 90), page, 200f, 200f))
        assertNull(calculateOcrPdfPlacement(OcrWord("reversed-y", 10, 40, 30, 20, 90), page, 200f, 200f))
    }

    @Test
    fun clampsWordBeyondRightAndBottomEdges() {
        val page = OcrPage(100, 100, emptyList())
        val result = calculateOcrPdfPlacement(
            OcrWord("edge", 80, 80, 120, 130, 85), page, 200f, 200f
        )
        requireNotNull(result)
        assertEquals(160f, result.x, 0.01f)
        assertEquals(40f, result.fontSize, 0.01f)
        assertTrue(result.baselineY >= 0f)
    }
}
