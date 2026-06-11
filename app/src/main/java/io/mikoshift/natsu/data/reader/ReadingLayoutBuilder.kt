package io.mikoshift.natsu.data.reader

import io.mikoshift.natsu.domain.model.reading.ReadingBlock
import io.mikoshift.natsu.domain.model.reading.ReadingBook
import io.mikoshift.natsu.domain.model.reading.ReadingLayout

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

fun ReadingLayout.paragraphIndexForCharOffset(charOffset: Int): Int {
    if (paragraphs.isEmpty()) return 0
    if (charOffset <= 0) return 0
    val index = paragraphStartOffsets.indexOfLast { it <= charOffset }
    return if (index >= 0) index else 0
}
