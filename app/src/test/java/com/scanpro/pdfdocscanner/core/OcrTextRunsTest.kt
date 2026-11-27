package com.scanpro.pdfdocscanner.core

import org.junit.Assert.assertEquals
import org.junit.Test

class OcrTextRunsTest {
    @Test
    fun splitsMixedBengaliAndEnglishWithoutDroppingCharacters() {
        val input = "বাংলা & English 123"
        val runs = splitOcrTextByScript(input)
        assertEquals("বাংলা & English 123", runs.joinToString("") { it.text })
        assertEquals(listOf(true, false), runs.map { it.usesBengaliFont })
        assertEquals("বাংলা & ", runs[0].text)
        assertEquals("English 123", runs[1].text)
    }

    @Test
    fun preservesLatinPrefixAndBengaliSuffix() {
        val runs = splitOcrTextByScript("Invoice: চালান")
        assertEquals("Invoice: চালান", runs.joinToString("") { it.text })
        assertEquals(listOf(false, true), runs.map { it.usesBengaliFont })
    }

    @Test
    fun handlesEmptyAndSingleScriptText() {
        assertEquals(emptyList<OcrTextRun>(), splitOcrTextByScript(""))
        assertEquals(listOf(OcrTextRun("শুধু বাংলা", true)), splitOcrTextByScript("শুধু বাংলা"))
        assertEquals(listOf(OcrTextRun("Only English", false)), splitOcrTextByScript("Only English"))
    }
}
