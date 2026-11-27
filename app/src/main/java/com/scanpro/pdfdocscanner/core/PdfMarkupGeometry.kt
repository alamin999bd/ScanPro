package com.scanpro.pdfdocscanner.core

/** PDF user-space rectangle and quad coordinates derived from visible CropBox-relative input. */
internal data class PdfMarkupGeometry(
    val left: Float,
    val bottom: Float,
    val width: Float,
    val height: Float,
) {
    val quadPoints: FloatArray
        get() = floatArrayOf(
            left, bottom + height, left + width, bottom + height,
            left, bottom, left + width, bottom,
        )
}

internal fun createPdfMarkupGeometry(
    cropLeft: Float,
    cropBottom: Float,
    cropWidth: Float,
    cropHeight: Float,
    x: Float,
    y: Float,
    width: Float,
    height: Float,
): PdfMarkupGeometry {
    val values = floatArrayOf(cropLeft, cropBottom, cropWidth, cropHeight, x, y, width, height)
    require(values.all { it.isFinite() }) { "Annotation coordinates must be finite numbers" }
    require(cropWidth > 0f && cropHeight > 0f) { "Visible page area is invalid" }
    require(x >= 0f && y >= 0f && width > 0f && height > 0f) { "Enter a valid annotation area" }
    require(x + width <= cropWidth && y + height <= cropHeight) {
        "Annotation area is outside the visible page area (${cropWidth.toInt()} × ${cropHeight.toInt()} pt)"
    }
    return PdfMarkupGeometry(cropLeft + x, cropBottom + y, width, height)
}
