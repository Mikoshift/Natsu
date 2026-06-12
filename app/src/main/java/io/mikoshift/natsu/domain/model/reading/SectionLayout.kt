package io.mikoshift.natsu.domain.model.reading

/**
 * Layout for a single [ReadingSection], without flattening the whole book.
 */
data class SectionLayout(
    val sectionId: String,
    val paragraphs: List<String>,
    val canonicalText: String,
    val paragraphStartOffsets: List<Int>,
    /** Maps each layout paragraph index to the source block index in the section. */
    val blockIndexByParagraph: List<Int>,
)
