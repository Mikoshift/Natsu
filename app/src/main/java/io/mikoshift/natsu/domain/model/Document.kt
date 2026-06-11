package io.mikoshift.natsu.domain.model

import io.mikoshift.natsu.domain.model.reading.BookFormat

data class Document(
    val id: String,
    val title: String,
    val storagePath: String,
    val sourceFormat: BookFormat,
    val importedAt: Long,
    val charCount: Int,
    val lastReadCharOffset: Int,
    /** Legacy fallback when [lastReadCharOffset] is still zero after DB migration. */
    val lastReadParagraphIndex: Int,
)

fun Document.hasReadingProgress(): Boolean =
    lastReadCharOffset > 0 || lastReadParagraphIndex > 0

fun Document.readingProgressPercent(): Int? {
    if (charCount <= 0) return null
    if (lastReadCharOffset > 0) {
        return ((lastReadCharOffset * 100L) / charCount).toInt().coerceIn(0, 100)
    }
    return null
}
