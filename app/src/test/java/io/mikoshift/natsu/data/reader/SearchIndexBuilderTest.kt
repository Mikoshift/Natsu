package io.mikoshift.natsu.data.reader

import io.mikoshift.natsu.domain.model.reading.BookFormat
import io.mikoshift.natsu.domain.model.reading.ReadingBlock
import io.mikoshift.natsu.domain.model.reading.ReadingBook
import io.mikoshift.natsu.domain.model.reading.ReadingSection
import io.mikoshift.natsu.domain.model.reading.TextSpan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchIndexBuilderTest {

    private val builder = SearchIndexBuilder()

    @Test
    fun build_multiSectionIndex_matchesFlattenedLayout() {
        val book = ReadingBook(
            id = "book-1",
            title = "Test",
            sections = listOf(
                ReadingSection(
                    id = "chapter-1",
                    title = "One",
                    blocks = listOf(
                        ReadingBlock.Heading(text = "One", level = 1),
                        ReadingBlock.Paragraph(listOf(TextSpan(text = "猫"))),
                    ),
                ),
                ReadingSection(
                    id = "chapter-2",
                    title = "Two",
                    blocks = listOf(
                        ReadingBlock.Paragraph(listOf(TextSpan(text = "犬"))),
                    ),
                ),
            ),
        )

        val layout = ReadingLayoutBuilder().build(book)
        val index = builder.build(book).copy(totalCharCount = layout.canonicalText.length)

        assertEquals(layout.canonicalText.length, index.totalCharCount)
        assertEquals(2, index.sectionOffsets.size)
        assertEquals(3, index.paragraphs.size)
        assertEquals("chapter-1", index.paragraphs.first().sectionId)
        assertEquals("chapter-2", index.paragraphs.last().sectionId)
    }

    @Test
    fun findMatches_returnsSectionLocalLocator() {
        val book = ReadingBook(
            id = "book-1",
            title = "Test",
            sections = listOf(
                ReadingSection(
                    id = "main",
                    title = null,
                    blocks = listOf(
                        ReadingBlock.Paragraph(listOf(TextSpan(text = "吾輩は猫である。"))),
                    ),
                ),
            ),
        )
        val index = builder.build(book)

        val matches = index.findMatches("猫")
        assertEquals(1, matches.size)
        assertEquals("main", matches.single().locator.sectionId)
        assertTrue(matches.single().localRange.first >= 0)
    }
}
