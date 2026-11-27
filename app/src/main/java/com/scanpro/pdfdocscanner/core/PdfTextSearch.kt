package com.scanpro.pdfdocscanner.core

import java.util.Locale

/** Search extracted PDF text while treating line breaks and repeated whitespace as word separators. */
internal fun containsPdfSearchQuery(content: String, query: String): Boolean {
    if (query.isBlank()) return false
    fun normalize(value: String) = value.trim()
        .replace(Regex("\\s+"), " ")
        .lowercase(Locale.ROOT)
    val normalizedQuery = normalize(query)
    return normalizedQuery.isNotEmpty() && normalize(content).contains(normalizedQuery)
}
