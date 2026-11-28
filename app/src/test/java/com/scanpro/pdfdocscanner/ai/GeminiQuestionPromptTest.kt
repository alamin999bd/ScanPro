package com.scanpro.pdfdocscanner.ai

import org.junit.Assert.assertTrue
import org.junit.Test

class GeminiQuestionPromptTest {
    @Test
    fun promptClearlySeparatesPdfAndGeneralKnowledgeSources() {
        val prompt = GeminiQuestionPrompt.build("বাংলা কী?", "একটি নমুনা PDF লেখা")
        assertTrue(prompt.contains("উৎস: PDF"))
        assertTrue(prompt.contains("উৎস: সাধারণ জ্ঞান (PDF-এ পাওয়া যায়নি)"))
        assertTrue(prompt.contains("Never claim or imply that general-knowledge facts came from the PDF"))
    }

    @Test
    fun promptIncludesQuestionAndDocumentText() {
        val prompt = GeminiQuestionPrompt.build("বাংলা কী?", "এটি PDF-এর লেখা")
        assertTrue(prompt.contains("বাংলা কী?"))
        assertTrue(prompt.contains("এটি PDF-এর লেখা"))
    }
}
