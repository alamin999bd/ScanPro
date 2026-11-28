package com.scanpro.pdfdocscanner.core

import android.content.Context
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class OfficeConverterTest {
    private lateinit var converter: OfficeConverter
    private lateinit var dir: File

    @Before
    fun setUp() {
        val context: Context = RuntimeEnvironment.getApplication()
        converter = OfficeConverter()
        dir = File(context.cacheDir, "office-converter-tests").apply {
            deleteRecursively()
            mkdirs()
        }
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    @Test
    fun convertsDocxParagraphsAndTableTextToPdf() = runBlocking {
        val input = zip(
            "sample.docx",
            "word/document.xml" to """
                <?xml version="1.0" encoding="UTF-8"?>
                <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
                  <w:body>
                    <w:p><w:pPr><w:pStyle w:val="Heading1"/></w:pPr><w:r><w:t>Report title</w:t></w:r></w:p>
                    <w:p><w:r><w:t>Paragraph text</w:t></w:r></w:p>
                    <w:tbl><w:tr><w:tc><w:p><w:r><w:t>Cell A</w:t></w:r></w:p></w:tc>
                    <w:tc><w:p><w:r><w:t>Cell B</w:t></w:r></w:p></w:tc></w:tr></w:tbl>
                  </w:body>
                </w:document>
            """.trimIndent()
        )
        val output = File(dir, "docx.pdf")

        val pages = converter.docxToPdf(input, output)

        assertTrue(pages >= 1)
        assertTrue(output.exists() && output.length() > 0L)
    }

    @Test
    fun convertsXlsxSharedStringsAndSparseCellsToPdf() = runBlocking {
        val input = zip(
            "sample.xlsx",
            "xl/workbook.xml" to """<workbook xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="Overview" sheetId="1" r:id="rId1"/></sheets></workbook>""",
            "xl/sharedStrings.xml" to """<sst><si><t>Revenue</t></si><si><t>1200</t></si></sst>""",
            "xl/worksheets/sheet1.xml" to """<worksheet><sheetData><row r="1"><c r="A1" t="s"><v>0</v></c><c r="C1" t="s"><v>1</v></c></row></sheetData></worksheet>"""
        )
        val output = File(dir, "xlsx.pdf")

        val pages = converter.xlsxToPdf(input, output)

        assertTrue(pages >= 1)
        assertTrue(output.exists() && output.length() > 0L)
    }

    @Test
    fun rejectsExcelCellBeyondMaximumColumnWithoutCreatingPdf() = runBlocking {
        val input = zip(
            "invalid-column.xlsx",
            "xl/workbook.xml" to """<workbook><sheets><sheet name="Bad" sheetId="1"/></sheets></workbook>""",
            "xl/worksheets/sheet1.xml" to """<worksheet><sheetData><row r="1"><c r="XFE1" t="inlineStr"><is><t>out of range</t></is></c></row></sheetData></worksheet>"""
        )
        val output = File(dir, "invalid-column.pdf")

        try {
            converter.xlsxToPdf(input, output)
            fail("An Excel cell beyond XFD should be rejected")
        } catch (_: IllegalArgumentException) {
            // Expected: XFD is the last supported Excel column.
        }

        assertTrue(!output.exists())
    }

    private fun zip(name: String, vararg entries: Pair<String, String>): File {
        val file = File(dir, name)
        ZipOutputStream(file.outputStream()).use { zip ->
            entries.forEach { (path, xml) ->
                zip.putNextEntry(ZipEntry(path))
                zip.write(xml.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
        }
        return file
    }
}
