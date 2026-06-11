package io.mikoshift.natsu.data.book

import io.mikoshift.natsu.domain.model.reading.BookFormat

data class ImportedBookPackage(
    val id: String,
    val title: String,
    val storagePath: String,
    val sourceFormat: BookFormat,
    val importedAt: Long,
)
