package com.scanpro.pdfdocscanner.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ParsePageRangesTest {

    @Test
    fun parsesSinglePagesAndRangesInOrder() {
        assertEquals(listOf(1, 2, 3, 5, 8, 9, 10), parsePageRanges("1-3, 5, 8-10", 10))
    }

    @Test
    fun trimsWhitespaceAroundEntries() {
        assertEquals(listOf(2, 4, 5), parsePageRanges(" 2 , 4 - 5 ", 5))
    }

    @Test
    fun returnsNullForEmptyOrMalformedInput() {
        assertNull(parsePageRanges("", 5))
        assertNull(parsePageRanges("abc", 5))
        assertNull(parsePageRanges("1-", 5))
        assertNull(parsePageRanges("3-2", 5))
        assertNull(parsePageRanges("1,,2", 5))
        assertNull(parsePageRanges(",1", 5))
        assertNull(parsePageRanges("1,", 5))
    }

    @Test
    fun rejectsPagesOutsideDocument() {
        assertNull(parsePageRanges("0", 5))
        assertNull(parsePageRanges("6", 5))
        assertNull(parsePageRanges("1-6", 5))
    }

    @Test
    fun returnsNullWhenPageCountIsZero() {
        assertNull(parsePageRanges("1", 0))
    }
}
