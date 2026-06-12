package io.mikoshift.natsu.domain.model.reading

sealed interface ReadingBlock {
    data class Paragraph(val spans: List<TextSpan>) : ReadingBlock
    data class Heading(val text: String, val level: Int) : ReadingBlock
    data class Image(val relativePath: String, val alt: String?) : ReadingBlock
}

/** Whether this block contributes a paragraph row in [ReadingLayout]. */
fun ReadingBlock.contributesLayoutParagraph(): Boolean = when (this) {
    is ReadingBlock.Paragraph -> spans.joinToString(separator = "") { it.text }.isNotEmpty()
    is ReadingBlock.Heading -> text.isNotBlank()
    is ReadingBlock.Image -> false
}

data class TextSpan(
    val text: String,
    val reading: String? = null,
)
