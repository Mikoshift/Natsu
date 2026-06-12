package io.mikoshift.natsu.ui.reader

import io.mikoshift.natsu.data.reader.layoutParagraphStart
import io.mikoshift.natsu.domain.model.TextToken
import io.mikoshift.natsu.domain.model.reading.ReadingBlock
import io.mikoshift.natsu.domain.model.reading.ReadingBook
import io.mikoshift.natsu.domain.model.reading.ReadingBookOutline
import io.mikoshift.natsu.domain.model.reading.ReadingLayout
import io.mikoshift.natsu.domain.model.reading.ReadingSection
import io.mikoshift.natsu.domain.model.reading.SearchIndex
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
            val sectionParagraphCount = section.blocks.count { it.contributesLayoutParagraph() }
            val sectionTokens = tokenizedParagraphs.subList(
                layoutParagraphIndex,
                layoutParagraphIndex + sectionParagraphCount,
            )
            items.addAll(
                buildSectionItems(
                    section = section,
                    bookStoragePath = bookStoragePath,
                    tokenizedParagraphs = sectionTokens,
                    globalLayoutParagraphOffset = layoutParagraphIndex,
                ),
            )
            layoutParagraphIndex += sectionParagraphCount
        }

        return items
    }

    fun buildSectionItems(
        section: ReadingSection,
        bookStoragePath: String,
        tokenizedParagraphs: List<List<TextToken>>,
        globalLayoutParagraphOffset: Int,
    ): List<ReaderDisplayItem> {
        val items = mutableListOf<ReaderDisplayItem>()
        var sectionParagraphIndex = 0

        section.blocks.forEachIndexed { blockIndex, block ->
            when (block) {
                is ReadingBlock.Paragraph -> {
                    val included = block.contributesLayoutParagraph()
                    items.add(
                        ReaderDisplayItem(
                            sectionId = section.id,
                            blockIndex = blockIndex,
                            layoutParagraphIndex = if (included) {
                                globalLayoutParagraphOffset + sectionParagraphIndex
                            } else {
                                null
                            },
                            content = ReaderBlockContent.Paragraph(
                                tokens = if (included) {
                                    tokenizedParagraphs[sectionParagraphIndex]
                                } else {
                                    emptyList()
                                },
                            ),
                        ),
                    )
                    if (included) sectionParagraphIndex++
                }
                is ReadingBlock.Heading -> {
                    val included = block.contributesLayoutParagraph()
                    items.add(
                        ReaderDisplayItem(
                            sectionId = section.id,
                            blockIndex = blockIndex,
                            layoutParagraphIndex = if (included) {
                                globalLayoutParagraphOffset + sectionParagraphIndex
                            } else {
                                null
                            },
                            content = ReaderBlockContent.Heading(
                                text = block.text,
                                level = block.level,
                            ),
                        ),
                    )
                    if (included) sectionParagraphIndex++
                }
                is ReadingBlock.Image -> {
                    items.add(
                        ReaderDisplayItem(
                            sectionId = section.id,
                            blockIndex = blockIndex,
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

        return items
    }

    fun buildSectionNav(outline: ReadingBookOutline): List<ReaderSectionNav> {
        if (outline.manifest.sections.size <= 1) return emptyList()
        return outline.manifest.sections.map { section ->
            ReaderSectionNav(
                id = section.id,
                title = section.title?.takeIf { it.isNotBlank() } ?: section.id,
                startLayoutParagraphIndex = outline.searchIndex.layoutParagraphStart(section.id),
            )
        }
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
