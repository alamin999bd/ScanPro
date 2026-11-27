package com.scanpro.pdfdocscanner.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.awt.Font

class SearchablePdfTextLayerTest {
    @Test
    fun bundledBengaliFontContainsBanglaAndEnglishGlyphs() {
        val fontStream = javaClass.getResourceAsStream("/NotoSansBengali.ttf")
            ?: throw AssertionError("Noto Sans Bengali test font was not provisioned")
        val font = fontStream.use { Font.createFont(Font.TRUETYPE_FONT, it) }
        val sample = "বাংলা পরীক্ষা English searchable text"

        assertTrue("Font must have a usable family name", font.family.isNotBlank())
        assertEquals(
            "The bundled font must contain every Bangla and English glyph in the OCR sample",
            -1,
            font.canDisplayUpTo(sample)
        )
    }
}
