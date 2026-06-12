package io.mikoshift.natsu.data.reader

import io.mikoshift.natsu.domain.model.reading.ReadingBlock
import io.mikoshift.natsu.domain.model.reading.ReadingSection
import io.mikoshift.natsu.domain.model.reading.TextSpan
import org.junit.Assert.assertEquals
import org.junit.Test

class SectionLayoutBuilderTest {

    private val builder = ReadingLayoutBuilder()

    @Test
    fun buildSection_producesSectionLocalLayout() {
        val section = ReadingSection(
            id = "chapter-1",
            title = "Chapter",
            blocks = listOf(
                ReadingBlock.Heading(text = "Title", level = 1),
                ReadingBlock.Paragraph(listOf(TextSpan(text = "Body"))),
                ReadingBlock.Image(relativePath = "cover.png", alt = null),
            ),
        )

        val sectionLayout = builder.buildSection(section)

        assertEquals("chapter-1", sectionLayout.sectionId)
        assertEquals(listOf("Title", "Body"), sectionLayout.paragraphs)
        assertEquals(listOf(0, 1), sectionLayout.blockIndexByParagraph)
        assertEquals("Title\nBody", sectionLayout.canonicalText)
    }
}
