package io.mikoshift.natsu.domain.model

import io.mikoshift.natsu.domain.model.reading.BookFormat
import io.mikoshift.natsu.domain.model.reading.ReadingLocator

data class Document(
    val id: String,
    val title: String,
    val storagePath: String,
    val sourceFormat: BookFormat,
    val importedAt: Long,
    val charCount: Int,
    /** Global offset in flattened canonical text; used for progress percentage. */
    val lastReadCharOffset: Int,
    /** Legacy fallback when [lastReadLocator] and [lastReadCharOffset] are unset. */
    val lastReadParagraphIndex: Int,
    val lastReadLocator: ReadingLocator? = null,
)

fun Document.hasReadingProgress(): Boolean =
    lastReadLocator != null || lastReadCharOffset > 0 || lastReadParagraphIndex > 0

fun Document.readingProgressPercent(): Int? {
    if (charCount <= 0) return null
    if (lastReadCharOffset > 0) {
        return ((lastReadCharOffset * 100L) / charCount).toInt().coerceIn(0, 100)
    }
    return null
}
