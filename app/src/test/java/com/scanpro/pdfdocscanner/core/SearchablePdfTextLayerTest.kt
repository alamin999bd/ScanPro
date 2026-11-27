package com.scanpro.pdfdocscanner.core

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.font.PDType0Font
import com.tom_roush.pdfbox.pdmodel.graphics.state.RenderingMode
import com.tom_roush.pdfbox.text.PDFTextStripper
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchablePdfTextLayerTest {
    @Test
    fun embeddedBengaliFontKeepsBanglaAndEnglishExtractableFromInvisibleText() {
        val fontStream = javaClass.getResourceAsStream("/NotoSansBengali.ttf")
            ?: throw AssertionError("Noto Sans Bengali test font was not provisioned")
        val document = PDDocument()
        try {
            val font = fontStream.use { PDType0Font.load(document, it) }
            val page = PDPage()
            document.addPage(page)
            PDPageContentStream(document, page).use { stream ->
                stream.beginText()
                stream.setFont(font, 12f)
                stream.setRenderingMode(RenderingMode.NEITHER)
                stream.newLineAtOffset(40f, 700f)
                stream.showText("বাংলা পরীক্ষা English searchable text")
                stream.endText()
            }

            val bytes = java.io.ByteArrayOutputStream()
            document.save(bytes)
            val reopened = PDDocument.load(bytes.toByteArray())
            try {
                val extracted = PDFTextStripper().getText(reopened)
                assertTrue("Bangla characters must be extractable: $extracted", extracted.contains("বাংলা"))
                assertTrue("English characters must be extractable: $extracted", extracted.contains("English"))
                assertTrue("The full mixed-language phrase must be extractable: $extracted",
                    extracted.contains("বাংলা পরীক্ষা English searchable text"))
            } finally {
                reopened.close()
            }
        } finally {
            document.close()
        }
    }
}
