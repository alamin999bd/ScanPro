package com.scanpro.pdfdocscanner.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfTextSearchTest {
    @Test fun matchesCaseInsensitively() {
        assertTrue(containsPdfSearchQuery("The QUICK Brown Fox", "quick brown"))
    }

    @Test fun matchesAcrossLineBreaksAndRepeatedWhitespace() {
        assertTrue(containsPdfSearchQuery("বাংলা English\nDocument   Scanner", "english document scanner"))
    }

    @Test fun rejectsBlankQueries() {
        assertFalse(containsPdfSearchQuery("Some searchable text", "   "))
    }

    @Test fun doesNotMatchDifferentWords() {
        assertFalse(containsPdfSearchQuery("Document Scanner", "Document Scanning"))
    }
}
