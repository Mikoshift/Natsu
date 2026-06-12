package io.mikoshift.natsu.data.reader

import io.mikoshift.natsu.domain.model.reading.ReadingBlock
import io.mikoshift.natsu.domain.model.reading.ReadingBook
import io.mikoshift.natsu.domain.model.reading.ReadingLayout
import io.mikoshift.natsu.domain.model.reading.ReadingSection
import io.mikoshift.natsu.domain.model.reading.TextSpan
import org.junit.Test

class ReadingLayoutInvariantsTest {

    private val builder = ReadingLayoutBuilder()

    @Test
    fun emptyBook_satisfiesInvariants() {
        assertReadingLayoutInvariants(
            ReadingLayout(
                paragraphs = emptyList(),
                canonicalText = "",
                paragraphStartOffsets = emptyList(),
                sectionBoundaries = emptyList(),
            ),
        )
    }

    @Test
    fun singleSectionBook_satisfiesInvariants() {
        val layout = builder.build(
            ReadingBook(
                id = "book-1",
                title = "Test",
                sections = listOf(
                    ReadingSection(
                        id = "main",
                        title = null,
                        blocks = listOf(
                            ReadingBlock.Paragraph(listOf(TextSpan("First"))),
                            ReadingBlock.Heading(text = "Chapter", level = 1),
                            ReadingBlock.Paragraph(listOf(TextSpan("Second"))),
                            ReadingBlock.Image(relativePath = "img.png", alt = null),
                            ReadingBlock.Paragraph(listOf(TextSpan(""))),
                        ),
                    ),
                ),
            ),
        )

        assertReadingLayoutInvariants(layout)
        assertSearchOffsetsAreValid(layout, "ir")
        assertSearchOffsetsAreValid(layout, "Chapter")
    }

    @Test
    fun multiSectionBook_satisfiesInvariants() {
        val layout = builder.build(
            ReadingBook(
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
                            ReadingBlock.Paragraph(listOf(TextSpan("More text"))),
                        ),
                    ),
                ),
            ),
        )

        assertReadingLayoutInvariants(layout)
        assertSearchOffsetsAreValid(layout, "Chapter")
        assertSearchOffsetsAreValid(layout, "text")
    }
}
