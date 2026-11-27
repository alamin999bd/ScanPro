package com.scanpro.pdfdocscanner.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HocrParserTest {

    @Test
    fun parsesWordBoundsAndConfidence() {
        val hocr = """<span class='ocrx_word' id='word_1' title='bbox 12 24 110 58; x_wconf 93'>Hello</span>"""
        assertEquals(
            listOf(OcrWord("Hello", 12, 24, 110, 58, 93)),
            HocrParser.parse(hocr),
        )
    }

    @Test
    fun parsesBanglaAndDecodesHtmlEntities() {
        val hocr = """<span class="ocrx_word" title="bbox 1 2 30 20; x_wconf 88">বাংলা &amp; English</span>"""
        assertEquals("বাংলা & English", HocrParser.parse(hocr).single().text)
    }

    @Test
    fun ignoresInvalidBoundsAndEmptyWords() {
        val hocr = """
            <span class='ocrx_word' title='bbox 5 5 5 20; x_wconf 90'>bad</span>
            <span class='ocrx_word' title='bbox 1 2 10 20; x_wconf 90'>   </span>
            <span class='ocrx_word' title='x_wconf 90'>missing</span>
        """
        assertEquals(emptyList<OcrWord>(), HocrParser.parse(hocr))
    }

    @Test
    fun ignoresNonWordSpansAndBlankInput() {
        assertEquals(emptyList<OcrWord>(), HocrParser.parse("<span class='ocr_line'>line</span>"))
        assertEquals(emptyList<OcrWord>(), HocrParser.parse(" "))
    }
}
