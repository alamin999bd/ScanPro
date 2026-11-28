package com.scanpro.pdfdocscanner.core

import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.Xml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.io.InputStream
import java.util.zip.ZipFile

/**
 * Basic, fully offline conversion of Word (.docx) and Excel (.xlsx) files to PDF.
 *
 * This is a TEXT-ONLY conversion: it keeps the words, paragraphs, headings, and table/sheet cell
 * text, but not fonts, colours, images, charts, page layout, formulas' formatting, or dates
 * (dates in Excel are stored as numbers). The old binary .doc and .xls formats are not supported.
 */
class OfficeConverter {

    private class Block(val text: String, val kind: Kind)

    private enum class Kind { HEADING, BODY, SMALL }

    class TooLargeException(message: String) : Exception(message)

    suspend fun docxToPdf(input: File, out: File): Int = withContext(Dispatchers.IO) {
        val blocks = ZipFile(input).use { zip ->
            val entry = zip.getEntry("word/document.xml")
                ?: throw IllegalArgumentException("This is not a valid Word (.docx) file.")
            checkSize(entry.size)
            zip.getInputStream(entry).use { parseDocx(it) }
        }
        render(blocks, out)
    }

    suspend fun xlsxToPdf(input: File, out: File): Int = withContext(Dispatchers.IO) {
        val blocks = ZipFile(input).use { zip ->
            val shared = zip.getEntry("xl/sharedStrings.xml")?.let { e ->
                checkSize(e.size)
                zip.getInputStream(e).use { parseSharedStrings(it) }
            } ?: emptyList()
            val names = zip.getEntry("xl/workbook.xml")?.let { e ->
                zip.getInputStream(e).use { parseSheetNames(it) }
            } ?: emptyList()
            val sheets = zip.entries().asSequence()
                .filter { it.name.startsWith("xl/worksheets/sheet") && it.name.endsWith(".xml") }
                .sortedBy { it.name.removePrefix("xl/worksheets/sheet").removeSuffix(".xml").toIntOrNull() ?: Int.MAX_VALUE }
                .toList()
            if (sheets.isEmpty()) throw IllegalArgumentException("This is not a valid Excel (.xlsx) file.")
            val all = ArrayList<Block>()
            var budget = MAX_CELLS
            sheets.forEachIndexed { i, entry ->
                checkSize(entry.size)
                all.add(Block(names.getOrNull(i) ?: "Sheet ${i + 1}", Kind.HEADING))
                val rows = zip.getInputStream(entry).use { parseSheet(it, shared, budget) }
                budget -= rows.sumOf { r -> r.count { c -> c.isNotEmpty() } }
                rows.forEach { row -> all.add(Block(row.joinToString("  |  "), Kind.SMALL)) }
                if (budget <= 0) {
                    all.add(Block("(The rest of this workbook was left out because it is very large.)", Kind.SMALL))
                    return@forEachIndexed
                }
            }
            all
        }
        render(blocks, out)
    }

    private fun checkSize(bytes: Long) {
        if (bytes > MAX_XML_BYTES) throw TooLargeException("This file is too large to convert on a phone.")
    }

    // ---------- Word ----------

    private fun parseDocx(stream: InputStream): List<Block> {
        val parser = Xml.newPullParser()
        parser.setInput(stream, null)
        val blocks = ArrayList<Block>()
        val para = StringBuilder()
        var heading = false
        var tableDepth = 0
        val cell = StringBuilder()
        val row = ArrayList<String>()
        var inText = false

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "w:p" -> { para.setLength(0); heading = false }
                    "w:pStyle" -> {
                        val v = parser.getAttributeValue(null, "w:val").orEmpty()
                        if (v.startsWith("Heading", true) || v.equals("Title", true)) heading = true
                    }
                    "w:t" -> inText = true
                    "w:tab" -> para.append('\t')
                    "w:br", "w:cr" -> para.append('\n')
                    "w:tbl" -> tableDepth++
                    "w:tr" -> row.clear()
                    "w:tc" -> cell.setLength(0)
                }
                XmlPullParser.TEXT -> if (inText) para.append(parser.text)
                XmlPullParser.END_TAG -> when (parser.name) {
                    "w:t" -> inText = false
                    "w:p" -> {
                        if (tableDepth > 0) {
                            if (cell.isNotEmpty() && para.isNotEmpty()) cell.append(' ')
                            cell.append(para)
                        } else {
                            blocks.add(Block(para.toString(), if (heading) Kind.HEADING else Kind.BODY))
                        }
                    }
                    "w:tc" -> row.add(cell.toString().trim())
                    "w:tr" -> blocks.add(Block(row.joinToString("  |  "), Kind.BODY))
                    "w:tbl" -> { tableDepth = (tableDepth - 1).coerceAtLeast(0) }
                }
            }
            event = parser.next()
        }
        return blocks
    }

    // ---------- Excel ----------

    private fun parseSharedStrings(stream: InputStream): List<String> {
        val parser = Xml.newPullParser()
        parser.setInput(stream, null)
        val result = ArrayList<String>()
        val cur = StringBuilder()
        var inT = false
        var skip = 0 // inside <rPh> phonetic runs
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "si" -> cur.setLength(0)
                    "rPh" -> skip++
                    "t" -> inT = true
                }
                XmlPullParser.TEXT -> if (inT && skip == 0) cur.append(parser.text)
                XmlPullParser.END_TAG -> when (parser.name) {
                    "t" -> inT = false
                    "rPh" -> skip = (skip - 1).coerceAtLeast(0)
                    "si" -> result.add(cur.toString())
                }
            }
            event = parser.next()
        }
        return result
    }

    private fun parseSheetNames(stream: InputStream): List<String> {
        val parser = Xml.newPullParser()
        parser.setInput(stream, null)
        val names = ArrayList<String>()
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && parser.name == "sheet") {
                parser.getAttributeValue(null, "name")?.let { names.add(it) }
            }
            event = parser.next()
        }
        return names
    }

    /** Returns rows of cell texts; empty cells are "" so columns stay aligned. */
    private fun parseSheet(stream: InputStream, shared: List<String>, cellBudget: Int): List<List<String>> {
        val parser = Xml.newPullParser()
        parser.setInput(stream, null)
        val rows = ArrayList<List<String>>()
        var row = ArrayList<String>()
        var type = ""
        var col = 0
        var inV = false
        var inIs = false
        var inT = false
        val value = StringBuilder()
        var used = 0

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT && used < cellBudget) {
            when (event) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "row" -> row = ArrayList()
                    "c" -> {
                        type = parser.getAttributeValue(null, "t").orEmpty()
                        value.setLength(0)
                        val ref = parser.getAttributeValue(null, "r")
                        if (ref != null) col = columnIndex(ref)
                    }
                    "v" -> inV = true
                    "is" -> inIs = true
                    "t" -> if (inIs) inT = true
                }
                XmlPullParser.TEXT -> if (inV || inT) value.append(parser.text)
                XmlPullParser.END_TAG -> when (parser.name) {
                    "v" -> inV = false
                    "t" -> inT = false
                    "is" -> inIs = false
                    "c" -> {
                        val text = when (type) {
                            "s" -> value.toString().trim().toIntOrNull()?.let { shared.getOrNull(it) }.orEmpty()
                            "b" -> if (value.toString().trim() == "1") "TRUE" else "FALSE"
                            else -> value.toString()
                        }
                        while (row.size < col) row.add("")
                        if (row.size == col) row.add(text) else row[col] = text
                        if (text.isNotEmpty()) used++
                    }
                    "row" -> {
                        if (row.any { it.isNotEmpty() }) {
                            rows.add(row.dropLastWhile { it.isEmpty() })
                        }
                    }
                }
            }
            event = parser.next()
        }
        return rows
    }

    /** "C7" -> 2, "AA1" -> 26 (zero-based column). */
    private fun columnIndex(ref: String): Int {
        var n = 0
        var letters = 0
        for (ch in ref) {
            if (ch !in 'A'..'Z') break
            letters++
            val digit = ch - 'A' + 1
            require(n <= (MAX_COLUMNS - digit) / 26) {
                "This Excel file contains a cell outside the supported column range."
            }
            n = n * 26 + digit
        }
        require(letters > 0 && n in 1..MAX_COLUMNS) {
            "This Excel file contains an invalid cell reference."
        }
        return n - 1
    }

    // ---------- PDF output ----------

    /** Lays the blocks out on A4 pages using the phone's own text engine (so Bangla and other scripts work). */
    private fun render(blocks: List<Block>, out: File): Int {
        val pageW = 595
        val pageH = 842
        val margin = 48
        val textW = pageW - margin * 2
        val bottom = pageH - margin

        fun paint(kind: Kind) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.BLACK
            when (kind) {
                Kind.HEADING -> { textSize = 17f; typeface = Typeface.DEFAULT_BOLD }
                Kind.BODY -> textSize = 11f
                Kind.SMALL -> textSize = 9f
            }
        }

        val doc = PdfDocument()
        var pageNumber = 0
        var page: PdfDocument.Page? = null
        var y = margin.toFloat()

        fun newPage() {
            page?.let { doc.finishPage(it) }
            pageNumber++
            page = doc.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, pageNumber).create())
            y = margin.toFloat()
        }

        try {
            newPage()
            for (block in blocks) {
                val text = block.text.ifEmpty { " " }
                val layout = StaticLayout.Builder
                    .obtain(text, 0, text.length, paint(block.kind), textW)
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                    .setLineSpacing(0f, 1.1f)
                    .build()
                val gapBefore = if (block.kind == Kind.HEADING) 8f else 0f
                y += gapBefore
                for (i in 0 until layout.lineCount) {
                    val top = layout.getLineTop(i).toFloat()
                    val lineBottom = layout.getLineBottom(i).toFloat()
                    val height = lineBottom - top
                    if (y + height > bottom) newPage()
                    val canvas = page!!.canvas
                    canvas.save()
                    canvas.translate(margin.toFloat(), y - top)
                    canvas.clipRect(0f, top, textW.toFloat(), lineBottom)
                    layout.draw(canvas)
                    canvas.restore()
                    y += height
                }
                y += if (block.kind == Kind.SMALL) 2f else 6f
            }
            page?.let { doc.finishPage(it) }
            out.outputStream().use { doc.writeTo(it) }
            return pageNumber
        } finally {
            doc.close()
        }
    }

    companion object {
        private const val MAX_XML_BYTES = 40L * 1024 * 1024
        private const val MAX_CELLS = 60_000
        private const val MAX_COLUMNS = 16_384
    }
}
