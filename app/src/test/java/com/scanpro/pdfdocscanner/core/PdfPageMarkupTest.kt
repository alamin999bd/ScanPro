package com.scanpro.pdfdocscanner.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PdfPageMarkupTest {
    @Test fun cropBoxOffsetMapsToTopLeftNormalizedCoordinates() {
        val result = normalizePdfMarkupBounds(
            cropLeft = 10f, cropBottom = 20f, cropWidth = 200f, cropHeight = 400f,
            rectLeft = 30f, rectBottom = 320f, rectRight = 70f, rectTop = 360f,
            kind = "HIGHLIGHT",
        )!!
        assertEquals(0.1f, result.left, 0.0001f)
        assertEquals(0.15f, result.top, 0.0001f)
        assertEquals(0.2f, result.width, 0.0001f)
        assertEquals(0.1f, result.height, 0.0001f)
    }

    @Test fun clockwiseRotationSwapsWidthAndHeight() {
        val result = normalizePdfMarkupBounds(
            cropLeft = 0f, cropBottom = 0f, cropWidth = 100f, cropHeight = 200f,
            rectLeft = 10f, rectBottom = 150f, rectRight = 30f, rectTop = 190f,
            rotation = 90, kind = "NOTE", note = "Reminder",
        )!!
        assertEquals(0.75f, result.left, 0.0001f)
        assertEquals(0.1f, result.top, 0.0001f)
        assertEquals(0.2f, result.width, 0.0001f)
        assertEquals(0.2f, result.height, 0.0001f)
        assertEquals("Reminder", result.note)
    }

    @Test fun rejectsInvalidAndOutOfCropBoxBounds() {
        assertNull(normalizePdfMarkupBounds(
            0f, 0f, 100f, 100f, -1f, 10f, 20f, 30f, kind = "NOTE",
        ))
        assertNull(normalizePdfMarkupBounds(
            0f, 0f, 100f, 100f, 10f, 10f, 20f, 20f, rotation = 45, kind = "NOTE",
        ))
    }
}
