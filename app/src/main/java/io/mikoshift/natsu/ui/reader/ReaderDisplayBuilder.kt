package io.mikoshift.natsu.ui.reader

import io.mikoshift.natsu.domain.model.TextToken
import io.mikoshift.natsu.domain.model.reading.ReadingBlock
import io.mikoshift.natsu.domain.model.reading.ReadingBook
import io.mikoshift.natsu.domain.model.reading.ReadingLayout
import io.mikoshift.natsu.domain.model.reading.contributesLayoutParagraph

object ReaderDisplayBuilder {
    fun buildItems(
        book: ReadingBook,
        bookStoragePath: String,
        tokenizedParagraphs: List<List<TextToken>>,
    ): List<ReaderDisplayItem> {
        val items = mutableListOf<ReaderDisplayItem>()
        var layoutParagraphIndex = 0

        book.sections.forEach { section ->
            section.blocks.forEach { block ->
                when (block) {
                    is ReadingBlock.Paragraph -> {
                        val included = block.contributesLayoutParagraph()
                        items.add(
                            ReaderDisplayItem(
                                layoutParagraphIndex = if (included) layoutParagraphIndex else null,
                                content = ReaderBlockContent.Paragraph(
                                    tokens = if (included) {
                                        tokenizedParagraphs[layoutParagraphIndex]
                                    } else {
                                        emptyList()
                                    },
                                ),
                            ),
                        )
                        if (included) layoutParagraphIndex++
                    }
                    is ReadingBlock.Heading -> {
                        val included = block.contributesLayoutParagraph()
                        items.add(
                            ReaderDisplayItem(
                                layoutParagraphIndex = if (included) layoutParagraphIndex else null,
                                content = ReaderBlockContent.Heading(
                                    text = block.text,
                                    level = block.level,
                                ),
                            ),
                        )
                        if (included) layoutParagraphIndex++
                    }
                    is ReadingBlock.Image -> {
                        items.add(
                            ReaderDisplayItem(
                                layoutParagraphIndex = null,
                                content = ReaderBlockContent.Image(
                                    source = block.relativePath,
                                    alt = block.alt,
                                    bookStoragePath = bookStoragePath,
                                ),
                            ),
                        )
                    }
                }
            }
        }

        return items
    }

    fun buildSectionNav(book: ReadingBook, layout: ReadingLayout): List<ReaderSectionNav> {
        if (book.sections.size <= 1) return emptyList()

        return book.sections.mapIndexed { index, section ->
            val startLayoutParagraphIndex = if (index == 0) {
                0
            } else {
                layout.sectionBoundaries[index - 1]
            }
            ReaderSectionNav(
                id = section.id,
                title = section.title?.takeIf { it.isNotBlank() } ?: "Section ${index + 1}",
                startLayoutParagraphIndex = startLayoutParagraphIndex,
            )
        }
    }

    fun displayIndexForLayoutParagraph(
        items: List<ReaderDisplayItem>,
        layoutParagraphIndex: Int,
    ): Int = items.indexOfFirst { it.layoutParagraphIndex == layoutParagraphIndex }.coerceAtLeast(0)

    fun layoutParagraphForDisplayIndex(
        items: List<ReaderDisplayItem>,
        displayIndex: Int,
    ): Int {
        val item = items.getOrNull(displayIndex) ?: return 0
        item.layoutParagraphIndex?.let { return it }
        return items
            .take(displayIndex + 1)
            .lastOrNull { it.layoutParagraphIndex != null }
            ?.layoutParagraphIndex
            ?: 0
    }
}
