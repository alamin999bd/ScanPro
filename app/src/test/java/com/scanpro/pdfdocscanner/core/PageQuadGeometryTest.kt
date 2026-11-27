package com.scanpro.pdfdocscanner.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PageQuadGeometryTest {
    @Test
    fun acceptsRectangleAndPerspectiveQuadrilateral() {
        assertTrue(PageQuadGeometry.isValid(listOf(0.1f, 0.1f, 0.9f, 0.1f, 0.85f, 0.9f, 0.15f, 0.9f)))
        assertTrue(PageQuadGeometry.isValid(listOf(0.2f, 0.1f, 0.9f, 0.2f, 0.8f, 0.9f, 0.1f, 0.8f)))
    }

    @Test
    fun rejectsMalformedNonFiniteOutOfBoundsAndTinyQuads() {
        assertFalse(PageQuadGeometry.isValid(null))
        assertFalse(PageQuadGeometry.isValid(listOf(0f, 0f, 1f)))
        assertFalse(PageQuadGeometry.isValid(listOf(0f, 0f, Float.NaN, 0f, 1f, 1f, 0f, 1f)))
        assertFalse(PageQuadGeometry.isValid(listOf(-0.1f, 0f, 1f, 0f, 1f, 1f, 0f, 1f)))
        assertFalse(PageQuadGeometry.isValid(listOf(0.4f, 0.4f, 0.6f, 0.4f, 0.6f, 0.6f, 0.4f, 0.6f), minArea = 0.1f))
    }

    @Test
    fun rejectsBowTieAndConcaveCornerOrder() {
        assertFalse(PageQuadGeometry.isValid(listOf(0.1f, 0.1f, 0.9f, 0.9f, 0.9f, 0.1f, 0.1f, 0.9f)))
        assertFalse(PageQuadGeometry.isValid(listOf(0.1f, 0.1f, 0.9f, 0.1f, 0.4f, 0.4f, 0.1f, 0.9f)))
    }

    @Test
    fun calculatesPerspectiveOutputSizeAndCapsLongestDimension() {
        val q = listOf(0.1f, 0.1f, 0.9f, 0.1f, 0.85f, 0.9f, 0.15f, 0.9f)
        val size = PageQuadGeometry.outputSize(3000, 4000, q, 2480)
        assertNotNull(size)
        assertTrue(requireNotNull(size).first <= 2480)
        assertTrue(size.second <= 2480)
        assertTrue(size.first < size.second)
    }

    @Test
    fun rejectsInvalidDimensionsOrQuadForOutputSize() {
        assertNull(PageQuadGeometry.outputSize(0, 100, listOf(0f, 0f, 1f, 0f, 1f, 1f, 0f, 1f), 2480))
        assertNull(PageQuadGeometry.outputSize(100, 100, null, 2480))
    }
}
