package io.mikoshift.natsu.ui.reader

import io.mikoshift.natsu.domain.model.reading.BookFormat
import io.mikoshift.natsu.domain.model.reading.BookManifest
import io.mikoshift.natsu.domain.model.reading.ManifestSection
import io.mikoshift.natsu.domain.model.reading.ReadingBookOutline
import io.mikoshift.natsu.domain.model.reading.SearchIndex
import io.mikoshift.natsu.domain.model.reading.SearchIndexParagraph
import io.mikoshift.natsu.domain.model.reading.SectionCharOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderDisplayBuilderTest {

    @Test
    fun buildSectionNav_returnsEmptyForSingleSection() {
        val outline = outlineWithSections(
            sections = listOf(ManifestSection(id = "main", title = null, path = "content.html")),
        )

        assertTrue(ReaderDisplayBuilder.buildSectionNav(outline).isEmpty())
    }

    @Test
    fun buildSectionNav_mapsMultiSectionTitlesAndOffsets() {
        val outline = outlineWithSections(
            sections = listOf(
                ManifestSection(id = "chapter-1", title = "One", path = "chapter-1.html"),
                ManifestSection(id = "chapter-2", title = "Two", path = "chapter-2.html"),
            ),
            paragraphs = listOf(
                SearchIndexParagraph(
                    sectionId = "chapter-1",
                    blockIndex = 0,
                    globalCharOffset = 0,
                    text = "First",
                ),
                SearchIndexParagraph(
                    sectionId = "chapter-2",
                    blockIndex = 0,
                    globalCharOffset = 10,
                    text = "Second",
                ),
            ),
            sectionOffsets = listOf(
                SectionCharOffset(sectionId = "chapter-1", globalCharOffset = 0, charCount = 10),
                SectionCharOffset(sectionId = "chapter-2", globalCharOffset = 10, charCount = 8),
            ),
        )

        val nav = ReaderDisplayBuilder.buildSectionNav(outline)

        assertEquals(2, nav.size)
        assertEquals("One", nav[0].title)
        assertEquals(0, nav[0].startLayoutParagraphIndex)
        assertEquals("Two", nav[1].title)
        assertEquals(1, nav[1].startLayoutParagraphIndex)
    }

    private fun outlineWithSections(
        sections: List<ManifestSection>,
        paragraphs: List<SearchIndexParagraph> = emptyList(),
        sectionOffsets: List<SectionCharOffset> = emptyList(),
    ): ReadingBookOutline {
        return ReadingBookOutline(
            id = "book-1",
            title = "Test",
            manifest = BookManifest(
                version = 2,
                format = BookFormat.PlainText,
                title = "Test",
                sections = sections,
            ),
            searchIndex = SearchIndex(
                version = 1,
                totalCharCount = sectionOffsets.sumOf { it.charCount },
                sectionOffsets = sectionOffsets,
                paragraphs = paragraphs,
            ),
        )
    }
}
