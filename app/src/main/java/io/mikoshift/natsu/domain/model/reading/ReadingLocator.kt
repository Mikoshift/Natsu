package io.mikoshift.natsu.domain.model.reading

/**
 * Section-local reading position after the EPUB spike (one spine item = one section).
 *
 * [charOffset] is relative to the canonical text of the target [blockIndex] within [sectionId].
 */
data class ReadingLocator(
    val sectionId: String,
    val blockIndex: Int,
    val charOffset: Int,
)
