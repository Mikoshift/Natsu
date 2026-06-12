package io.mikoshift.natsu.data.reader

import io.mikoshift.natsu.domain.model.reading.ReadingBook
import io.mikoshift.natsu.domain.model.reading.SearchIndex
import io.mikoshift.natsu.domain.model.reading.SearchIndexParagraph
import io.mikoshift.natsu.domain.model.reading.SectionCharOffset

class SearchIndexBuilder(
    private val readingLayoutBuilder: ReadingLayoutBuilder = ReadingLayoutBuilder(),
) {
    fun build(book: ReadingBook): SearchIndex {
        val sectionOffsets = mutableListOf<SectionCharOffset>()
        val paragraphs = mutableListOf<SearchIndexParagraph>()
        var globalOffset = 0

        book.sections.forEach { section ->
            val sectionLayout = readingLayoutBuilder.buildSection(section)
            sectionOffsets.add(
                SectionCharOffset(
                    sectionId = section.id,
                    globalCharOffset = globalOffset,
                    charCount = sectionLayout.canonicalText.length,
                ),
            )
            sectionLayout.paragraphs.forEachIndexed { paragraphIndex, text ->
                val blockIndex = sectionLayout.blockIndexByParagraph[paragraphIndex]
                paragraphs.add(
                    SearchIndexParagraph(
                        sectionId = section.id,
                        blockIndex = blockIndex,
                        globalCharOffset = globalOffset + sectionLayout.paragraphStartOffsets[paragraphIndex],
                        text = text,
                    ),
                )
            }
            if (sectionLayout.canonicalText.isNotEmpty()) {
                globalOffset += sectionLayout.canonicalText.length + 1
            }
        }

        val totalCharCount = if (paragraphs.isEmpty()) {
            0
        } else {
            paragraphs.last().globalCharOffset + paragraphs.last().text.length
        }

        return SearchIndex(
            version = SEARCH_INDEX_VERSION,
            totalCharCount = totalCharCount,
            sectionOffsets = sectionOffsets,
            paragraphs = paragraphs,
        )
    }

    companion object {
        const val SEARCH_INDEX_VERSION = 1
    }
}
