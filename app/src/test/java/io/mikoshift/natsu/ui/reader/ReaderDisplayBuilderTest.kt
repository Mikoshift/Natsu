package io.mikoshift.natsu.ui.reader

import io.mikoshift.natsu.data.reader.ReadingLayoutBuilder
import io.mikoshift.natsu.domain.model.TextToken
import io.mikoshift.natsu.domain.model.reading.ReadingBlock
import io.mikoshift.natsu.domain.model.reading.ReadingBook
import io.mikoshift.natsu.domain.model.reading.ReadingSection
import io.mikoshift.natsu.domain.model.reading.TextSpan
import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderDisplayBuilderTest {

    @Test
    fun buildItems_mapsImagesWithoutLayoutParagraphIndex() {
        val book = ReadingBook(
            id = "book-1",
            title = "Test",
            sections = listOf(
                ReadingSection(
                    id = "main",
                    title = null,
                    blocks = listOf(
                        ReadingBlock.Heading(text = "Title", level = 1),
                        ReadingBlock.Paragraph(listOf(TextSpan("Body"))),
                        ReadingBlock.Image(relativePath = "images/cover.png", alt = "Cover"),
                    ),
                ),
            ),
        )
        val layout = ReadingLayoutBuilder().build(book)
        val tokenized = listOf(
            listOf(
                TextToken(
                    surface = "Title",
                    reading = "Title",
                    lemma = "Title",
                    partOfSpeech = "名詞",
                    isClickable = false,
                ),
            ),
            listOf(
                TextToken(
                    surface = "Body",
                    reading = "Body",
                    lemma = "Body",
                    partOfSpeech = "名詞",
                    isClickable = false,
                ),
            ),
        )

        val items = ReaderDisplayBuilder.buildItems(
            book = book,
            bookStoragePath = "/books/book-1",
            tokenizedParagraphs = tokenized,
        )

        assertEquals(3, items.size)
        assertEquals(0, items[0].layoutParagraphIndex)
        assertEquals(1, items[1].layoutParagraphIndex)
        assertEquals(null, items[2].layoutParagraphIndex)
        assertEquals(2, layout.paragraphs.size)
        assertEquals(1, ReaderDisplayBuilder.displayIndexForLayoutParagraph(items, layoutParagraphIndex = 1))
    }

    @Test
    fun buildItems_skipsLayoutParagraphIndexForEmptyBlocks() {
        val book = ReadingBook(
            id = "book-1",
            title = "Test",
            sections = listOf(
                ReadingSection(
                    id = "main",
                    title = null,
                    blocks = listOf(
                        ReadingBlock.Paragraph(listOf(TextSpan("First"))),
                        ReadingBlock.Paragraph(emptyList()),
                        ReadingBlock.Heading(text = "   ", level = 2),
                        ReadingBlock.Paragraph(listOf(TextSpan("Second"))),
                    ),
                ),
            ),
        )
        val layout = ReadingLayoutBuilder().build(book)
        val tokenized = listOf(
            listOf(
                TextToken(
                    surface = "First",
                    reading = "First",
                    lemma = "First",
                    partOfSpeech = "名詞",
                    isClickable = false,
                ),
            ),
            listOf(
                TextToken(
                    surface = "Second",
                    reading = "Second",
                    lemma = "Second",
                    partOfSpeech = "名詞",
                    isClickable = false,
                ),
            ),
        )

        val items = ReaderDisplayBuilder.buildItems(
            book = book,
            bookStoragePath = "/books/book-1",
            tokenizedParagraphs = tokenized,
        )

        assertEquals(4, items.size)
        assertEquals(0, items[0].layoutParagraphIndex)
        assertEquals(null, items[1].layoutParagraphIndex)
        assertEquals(null, items[2].layoutParagraphIndex)
        assertEquals(1, items[3].layoutParagraphIndex)
        assertEquals(2, layout.paragraphs.size)
        assertEquals(
            3,
            ReaderDisplayBuilder.displayIndexForLayoutParagraph(items, layoutParagraphIndex = 1),
        )
    }
}
