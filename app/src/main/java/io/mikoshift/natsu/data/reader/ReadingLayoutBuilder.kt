package io.mikoshift.natsu.data.reader

import io.mikoshift.natsu.domain.model.reading.ReadingBlock
import io.mikoshift.natsu.domain.model.reading.ReadingBook
import io.mikoshift.natsu.domain.model.reading.ReadingLayout

/**
 * Builds [ReadingLayout] from [ReadingBook] IR.
 *
 * Inclusion rules:
 * - [ReadingBlock.Paragraph]: included when joined span text is non-empty.
 * - [ReadingBlock.Heading]: included when text is not blank (rendered as a paragraph row).
 * - [ReadingBlock.Image]: skipped (not yet represented in layout).
 *
 * [ReadingLayout.canonicalText] is built by appending each included block separated by `\n`.
 * [ReadingLayout.sectionBoundaries] records the paragraph index where each section after the first begins.
 */
class ReadingLayoutBuilder {
    fun build(book: ReadingBook): ReadingLayout {
        val paragraphs = mutableListOf<String>()
        val paragraphStartOffsets = mutableListOf<Int>()
        val sectionBoundaries = mutableListOf<Int>()
        val canonicalBuilder = StringBuilder()

        book.sections.forEachIndexed { sectionIndex, section ->
            if (sectionIndex > 0 && paragraphs.isNotEmpty()) {
                sectionBoundaries.add(paragraphs.size)
            }

            section.blocks.forEach { block ->
                when (block) {
                    is ReadingBlock.Paragraph -> {
                        val paragraphText = block.spans.joinToString(separator = "") { it.text }
                        if (paragraphText.isEmpty()) return@forEach

                        if (canonicalBuilder.isNotEmpty()) {
                            canonicalBuilder.append('\n')
                        }
                        paragraphStartOffsets.add(canonicalBuilder.length)
                        canonicalBuilder.append(paragraphText)
                        paragraphs.add(paragraphText)
                    }
                    is ReadingBlock.Heading -> {
                        if (block.text.isBlank()) return@forEach

                        if (canonicalBuilder.isNotEmpty()) {
                            canonicalBuilder.append('\n')
                        }
                        paragraphStartOffsets.add(canonicalBuilder.length)
                        canonicalBuilder.append(block.text)
                        paragraphs.add(block.text)
                    }
                    is ReadingBlock.Image -> Unit
                }
            }
        }

        return ReadingLayout(
            paragraphs = paragraphs,
            canonicalText = canonicalBuilder.toString(),
            paragraphStartOffsets = paragraphStartOffsets,
            sectionBoundaries = sectionBoundaries,
        )
    }
}

/** Maps a [ReadingLayout.canonicalText] offset to the containing paragraph index. */
fun ReadingLayout.paragraphIndexForCharOffset(charOffset: Int): Int {
    if (paragraphs.isEmpty()) return 0
    if (charOffset <= 0) return 0
    val index = paragraphStartOffsets.indexOfLast { it <= charOffset }
    return if (index >= 0) index else 0
}
