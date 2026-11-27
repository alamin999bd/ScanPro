package com.scanpro.pdfdocscanner.core

/** A PDF annotation positioned in normalized, top-left-origin page coordinates. */
data class PdfPageMarkup(
    val kind: String,
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
    val note: String? = null,
)

/**
 * Converts a PDF user-space annotation rectangle into normalized viewer coordinates.
 * The returned bounds account for the page CropBox and clockwise page rotation.
 */
fun normalizePdfMarkupBounds(
    cropLeft: Float,
    cropBottom: Float,
    cropWidth: Float,
    cropHeight: Float,
    rectLeft: Float,
    rectBottom: Float,
    rectRight: Float,
    rectTop: Float,
    rotation: Int = 0,
    kind: String,
    note: String? = null,
): PdfPageMarkup? {
    val values = listOf(cropLeft, cropBottom, cropWidth, cropHeight, rectLeft, rectBottom, rectRight, rectTop)
    if (values.any { !it.isFinite() } || cropWidth <= 0f || cropHeight <= 0f) return null
    if (rectRight <= rectLeft || rectTop <= rectBottom) return null
    if (rectLeft < cropLeft || rectBottom < cropBottom ||
        rectRight > cropLeft + cropWidth || rectTop > cropBottom + cropHeight
    ) return null

    val x = (rectLeft - cropLeft) / cropWidth
    val y = (cropBottom + cropHeight - rectTop) / cropHeight
    val w = (rectRight - rectLeft) / cropWidth
    val h = (rectTop - rectBottom) / cropHeight

    val normalized = when (((rotation % 360) + 360) % 360) {
        0 -> floatArrayOf(x, y, w, h)
        90 -> floatArrayOf(1f - y - h, x, h, w)
        180 -> floatArrayOf(1f - x - w, 1f - y - h, w, h)
        270 -> floatArrayOf(y, 1f - x - w, h, w)
        else -> return null
    }
    val (left, top, width, height) = normalized
    if (listOf(left, top, width, height).any { !it.isFinite() } ||
        left < 0f || top < 0f || width <= 0f || height <= 0f ||
        left + width > 1.0001f || top + height > 1.0001f
    ) return null
    return PdfPageMarkup(kind, left, top, width, height, note)
}
