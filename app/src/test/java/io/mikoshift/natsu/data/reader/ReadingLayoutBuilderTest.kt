package io.mikoshift.natsu.data.reader

import io.mikoshift.natsu.data.reader.findMatchOffsets
import io.mikoshift.natsu.domain.model.reading.ReadingBlock
import io.mikoshift.natsu.domain.model.reading.ReadingBook
import io.mikoshift.natsu.domain.model.reading.ReadingSection
import io.mikoshift.natsu.domain.model.reading.TextSpan
import org.junit.Assert.assertEquals
import org.junit.Test

class ReadingLayoutBuilderTest {

    private val builder = ReadingLayoutBuilder()

    @Test
    fun build_flattensSectionsAndTracksOffsets() {
        val book = ReadingBook(
            id = "book-1",
            title = "Test",
            sections = listOf(
                ReadingSection(
                    id = "main",
                    title = null,
                    blocks = listOf(
                        ReadingBlock.Paragraph(listOf(TextSpan("First"))),
                        ReadingBlock.Paragraph(listOf(TextSpan("Second"))),
                    ),
                ),
            ),
        )

        val layout = builder.build(book)

        assertEquals(listOf("First", "Second"), layout.paragraphs)
        assertEquals("First\nSecond", layout.canonicalText)
        assertEquals(listOf(0, 6), layout.paragraphStartOffsets)
        assertEquals(0, layout.paragraphIndexForCharOffset(2))
        assertEquals(1, layout.paragraphIndexForCharOffset(7))
    }

    @Test
    fun build_isCompatibleWithTextSearch() {
        val book = ReadingBook(
            id = "book-1",
            title = "Test",
            sections = listOf(
                ReadingSection(
                    id = "main",
                    title = null,
                    blocks = listOf(
                        ReadingBlock.Paragraph(listOf(TextSpan("Alpha beta"))),
                        ReadingBlock.Paragraph(listOf(TextSpan("Gamma alpha"))),
                    ),
                ),
            ),
        )

        val layout = builder.build(book)
        val matches = findMatchOffsets(layout.canonicalText, "Alpha")

        assertEquals(listOf(0), matches)
    }

    @Test
    fun build_recordsSectionBoundaries() {
        val book = ReadingBook(
            id = "book-1",
            title = "Test",
            sections = listOf(
                ReadingSection(
                    id = "chapter-1",
                    title = "One",
                    blocks = listOf(
                        ReadingBlock.Paragraph(listOf(TextSpan("Chapter one"))),
                    ),
                ),
                ReadingSection(
                    id = "chapter-2",
                    title = "Two",
                    blocks = listOf(
                        ReadingBlock.Paragraph(listOf(TextSpan("Chapter two"))),
                    ),
                ),
            ),
        )

        val layout = builder.build(book)

        assertEquals(listOf(1), layout.sectionBoundaries)
        assertEquals("Chapter one\nChapter two", layout.canonicalText)
    }
}
