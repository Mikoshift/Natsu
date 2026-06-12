package io.mikoshift.natsu.domain.model.reading

data class SearchIndex(
    val version: Int,
    val totalCharCount: Int,
    val sectionOffsets: List<SectionCharOffset>,
    val paragraphs: List<SearchIndexParagraph>,
)

data class SectionCharOffset(
    val sectionId: String,
    val globalCharOffset: Int,
    val charCount: Int,
)

data class SearchIndexParagraph(
    val sectionId: String,
    val blockIndex: Int,
    val globalCharOffset: Int,
    val text: String,
)

data class SearchMatch(
    val locator: ReadingLocator,
    val globalCharOffset: Int,
    val localRange: IntRange,
)
