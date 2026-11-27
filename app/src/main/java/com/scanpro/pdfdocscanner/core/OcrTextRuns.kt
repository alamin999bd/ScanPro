package com.scanpro.pdfdocscanner.core

/** A contiguous text run that can be rendered with one script-specific embedded font. */
data class OcrTextRun(val text: String, val usesBengaliFont: Boolean)

/**
 * Splits mixed Bengali/Latin OCR text without dropping characters.
 * Neutral punctuation and whitespace stay with the preceding script run.
 */
fun splitOcrTextByScript(text: String): List<OcrTextRun> {
    if (text.isEmpty()) return emptyList()
    val result = ArrayList<OcrTextRun>()
    var currentScript: Boolean? = null
    val current = StringBuilder()

    fun flush() {
        if (current.isNotEmpty()) {
            result += OcrTextRun(current.toString(), currentScript == true)
            current.setLength(0)
        }
    }

    for (character in text) {
        val script = when (character) {
            in '\u0980'..'\u09FF' -> true
            in 'A'..'Z', in 'a'..'z', in '0'..'9' -> false
            else -> currentScript ?: false
        }
        if (currentScript != null && script != currentScript) flush()
        currentScript = script
        current.append(character)
    }
    flush()
    return result
}
