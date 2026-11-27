package com.scanpro.pdfdocscanner.core

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PdfMarkupGeometryTest {
    @Test fun appliesNonZeroCropBoxOrigin() {
        val result = createPdfMarkupGeometry(12f, 24f, 500f, 700f, 30f, 40f, 100f, 60f)
        assertEquals(42f, result.left, 0f)
        assertEquals(64f, result.bottom, 0f)
        assertEquals(100f, result.width, 0f)
        assertEquals(60f, result.height, 0f)
    }

    @Test fun highlightQuadUsesPdfTopLeftTopRightBottomLeftBottomRightOrder() {
        val result = createPdfMarkupGeometry(10f, 20f, 500f, 700f, 30f, 40f, 100f, 60f)
        assertArrayEquals(floatArrayOf(40f, 120f, 140f, 120f, 40f, 60f, 140f, 60f), result.quadPoints, 0f)
    }

    @Test fun rejectsAreaOutsideVisibleCropBox() {
        assertThrows(IllegalArgumentException::class.java) {
            createPdfMarkupGeometry(0f, 0f, 100f, 100f, 90f, 10f, 20f, 10f)
        }
    }

    @Test fun rejectsNonFiniteCoordinates() {
        assertThrows(IllegalArgumentException::class.java) {
            createPdfMarkupGeometry(0f, 0f, 100f, 100f, Float.NaN, 1f, 10f, 10f)
        }
    }

    @Test fun rejectsNonPositiveArea() {
        assertThrows(IllegalArgumentException::class.java) {
            createPdfMarkupGeometry(0f, 0f, 100f, 100f, 1f, 1f, 0f, 10f)
        }
    }
}
