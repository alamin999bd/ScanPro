package com.scanpro.pdfdocscanner.core

/**
 * A recognized word and its rectangle in the original OCR bitmap.
 * Coordinates use a top-left origin and are measured in bitmap pixels.
 */
data class OcrWord(
    val text: String,
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
    val confidence: Int?,
)

/** Parses Tesseract hOCR word spans and preserves their source-image geometry. */
object HocrParser {
    private val wordSpan = Regex(
        """<span\b(?=[^>]*\bclass=['"][^'"]*\bocrx_word\b[^'"]*['"])([^>]*)>(.*?)</span>""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )
    private val bbox = Regex("""\bbbox\s+(\d+)\s+(\d+)\s+(\d+)\s+(\d+)""", RegexOption.IGNORE_CASE)
    private val confidence = Regex("""\bx_wconf\s+(\d+)""", RegexOption.IGNORE_CASE)
    private val titleAttribute = Regex("""\btitle=['"]([^'"]*)['"]""", RegexOption.IGNORE_CASE)
    private val tags = Regex("""<[^>]+>""")

    fun parse(hocr: String): List<OcrWord> {
        if (hocr.isBlank()) return emptyList()
        return wordSpan.findAll(hocr).mapNotNull { match ->
            val title = titleAttribute.find(match.groupValues[1])?.groupValues?.get(1)
                ?: return@mapNotNull null
            val bounds = bbox.find(title)?.groupValues?.drop(1)?.mapNotNull(String::toIntOrNull)
                ?: return@mapNotNull null
            if (bounds.size != 4) return@mapNotNull null
            val (left, top, right, bottom) = bounds
            if (left < 0 || top < 0 || right <= left || bottom <= top) return@mapNotNull null
            val text = decodeEntities(tags.replace(match.groupValues[2], ""))
                .replace(Regex("""\s+"""), " ")
                .trim()
            if (text.isEmpty()) return@mapNotNull null
            OcrWord(text, left, top, right, bottom, confidence.find(title)?.groupValues?.get(1)?.toIntOrNull())
        }.toList()
    }

    private fun decodeEntities(value: String): String {
        val named = value.replace("&amp;", "&").replace("&lt;", "<")
            .replace("&gt;", ">").replace("&quot;", "\"")
            .replace("&#39;", "'").replace("&nbsp;", " ")
        return Regex("""&#(x[0-9a-fA-F]+|[0-9]+);""").replace(named) { match ->
            val token = match.groupValues[1]
            val codePoint = if (token.startsWith("x", ignoreCase = true)) token.substring(1).toIntOrNull(16)
                else token.toIntOrNull()
            codePoint?.takeIf { Character.isValidCodePoint(it) }
                ?.let { String(Character.toChars(it)) } ?: match.value
        }
    }
}

/** Word geometry and decoded-image dimensions for one OCR page. */
data class OcrPage(
    val imageWidth: Int,
    val imageHeight: Int,
    val words: List<OcrWord>,
)
