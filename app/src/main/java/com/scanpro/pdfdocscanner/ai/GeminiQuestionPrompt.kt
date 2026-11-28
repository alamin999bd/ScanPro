package com.scanpro.pdfdocscanner.ai

/**
 * Builds the Ask PDF prompt with explicit source separation.
 * General knowledge must never be presented as if it came from the PDF.
 */
object GeminiQuestionPrompt {
    fun build(question: String, documentText: String): String = """
        You are answering a user's question inside a PDF assistant.

        SOURCE RULES:
        1. First check whether the supplied PDF text contains enough reliable information to answer the exact question.
        2. If the PDF supports the answer, answer from the PDF only and begin with: "উৎস: PDF".
        3. If the PDF does not contain enough information, you MAY answer using your general knowledge. In that case, begin with exactly: "উৎস: সাধারণ জ্ঞান (PDF-এ পাওয়া যায়নি)".
        4. Never claim or imply that general-knowledge facts came from the PDF. Do not mix the two sources without clearly separating them.
        5. If the question is misspelled, unclear, or could mean more than one thing, ask the user to clarify instead of guessing.
        6. If you are not confident about a general-knowledge answer, say so plainly. Do not invent facts, quotations, page numbers, or document contents.
        7. Reply in simple Bengali unless the user explicitly requests another language. Keep the answer relevant and concise.

        User question:
        ${question.trim()}

        Supplied PDF text:
        ${documentText}
    """.trimIndent()
}
