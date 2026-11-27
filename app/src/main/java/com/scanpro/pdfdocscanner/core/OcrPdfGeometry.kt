package com.scanpro.pdfdocscanner.core

import kotlin.math.max
import kotlin.math.min

/** PDF-space placement for one invisible OCR word. */
data class OcrPdfPlacement(
    val text: String,
    val x: Float,
    val baselineY: Float,
    val fontSize: Float,
)

/**
 * Maps a top-left-origin hOCR rectangle to the centred image rectangle in PDF user space.
 * Returns null for invalid or completely out-of-image word bounds.
 */
fun calculateOcrPdfPlacement(
    word: OcrWord,
    page: OcrPage,
    pdfWidth: Float,
    pdfHeight: Float,
): OcrPdfPlacement? {
    if (page.imageWidth <= 0 || page.imageHeight <= 0 || pdfWidth <= 0f || pdfHeight <= 0f) return null
    if (word.text.isBlank()) return null
    val left = word.left.coerceIn(0, page.imageWidth)
    val top = word.top.coerceIn(0, page.imageHeight)
    val right = word.right.coerceIn(0, page.imageWidth)
    val bottom = word.bottom.coerceIn(0, page.imageHeight)
    if (right <= left || bottom <= top) return null

    val scale = min(pdfWidth / page.imageWidth, pdfHeight / page.imageHeight)
    val drawnWidth = page.imageWidth * scale
    val drawnHeight = page.imageHeight * scale
    val offsetX = (pdfWidth - drawnWidth) / 2f
    val offsetY = (pdfHeight - drawnHeight) / 2f
    val fontSize = max(1f, (bottom - top) * scale)
    val x = offsetX + left * scale
    // hOCR uses a top-left origin; PDF text uses a baseline from the bottom.
    val baselineY = offsetY + (page.imageHeight - bottom) * scale + fontSize * 0.12f
    return OcrPdfPlacement(word.text, x, baselineY, fontSize)
}
