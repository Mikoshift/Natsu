package io.mikoshift.natsu.domain.model

data class Document(
    val id: String,
    val title: String,
    val filePath: String,
    val importedAt: Long,
    val lastReadParagraphIndex: Int,
)
