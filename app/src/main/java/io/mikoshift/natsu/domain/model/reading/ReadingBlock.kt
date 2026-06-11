package io.mikoshift.natsu.domain.model.reading

sealed interface ReadingBlock {
    data class Paragraph(val spans: List<TextSpan>) : ReadingBlock
    data class Heading(val text: String, val level: Int) : ReadingBlock
    data class Image(val relativePath: String, val alt: String?) : ReadingBlock
}

data class TextSpan(
    val text: String,
    val reading: String? = null,
)
