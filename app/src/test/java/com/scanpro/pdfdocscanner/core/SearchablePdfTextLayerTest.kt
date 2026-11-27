package com.scanpro.pdfdocscanner.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchablePdfTextLayerTest {
    @Test
    fun bundledBengaliFontIsAValidTrueTypeFile() {
        val fontBytes = javaClass.getResourceAsStream("/NotoSansBengali.ttf")
            ?.use { it.readBytes() }
            ?: throw AssertionError("Noto Sans Bengali test font was not provisioned")

        assertTrue("Font file must not be truncated", fontBytes.size > 10_000)
        val signature = fontBytes.copyOfRange(0, 4).joinToString("") {
            "%02X".format(it.toInt() and 0xFF)
        }
        assertEquals("00010000", signature)
    }
}
