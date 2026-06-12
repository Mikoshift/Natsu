package io.mikoshift.natsu.ui.reader

import io.mikoshift.natsu.domain.model.TextToken

sealed interface ReaderBlockContent {
    data class Paragraph(val tokens: List<TextToken>) : ReaderBlockContent
    data class Heading(val text: String, val level: Int) : ReaderBlockContent
    data class Image(
        val source: String,
        val alt: String?,
        val bookStoragePath: String,
    ) : ReaderBlockContent
}

data class ReaderDisplayItem(
    val sectionId: String,
    val blockIndex: Int,
    val layoutParagraphIndex: Int?,
    val content: ReaderBlockContent,
)

data class ReaderSectionNav(
    val id: String,
    val title: String,
    val startLayoutParagraphIndex: Int,
)
