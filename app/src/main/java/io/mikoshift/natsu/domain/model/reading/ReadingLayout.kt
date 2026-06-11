package io.mikoshift.natsu.domain.model.reading

data class ReadingLayout(
    val paragraphs: List<String>,
    val canonicalText: String,
    val paragraphStartOffsets: List<Int>,
    val sectionBoundaries: List<Int>,
)
