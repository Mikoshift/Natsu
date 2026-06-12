package io.mikoshift.natsu.domain.model.reading

sealed interface ReadingBlock {
    data class Paragraph(val spans: List<TextSpan>) : ReadingBlock
    data class Heading(val text: String, val level: Int) : ReadingBlock
    data class Image(val relativePath: String, val alt: String?) : ReadingBlock
}

/** Layout-visible text for this block, or null when the block is omitted from [ReadingLayout]. */
fun ReadingBlock.layoutParagraphText(): String? = when (this) {
    is ReadingBlock.Paragraph -> spans.joinToString(separator = "") { it.text }.takeIf { it.isNotEmpty() }
    is ReadingBlock.Heading -> text.takeIf { it.isNotBlank() }
    is ReadingBlock.Image -> alt ?: ""
}

/** Whether this block contributes a paragraph row in [ReadingLayout]. */
fun ReadingBlock.contributesLayoutParagraph(): Boolean = when (this) {
    is ReadingBlock.Image -> true
    else -> layoutParagraphText() != null
}

data class TextSpan(
    val text: String,
    val reading: String? = null,
)
