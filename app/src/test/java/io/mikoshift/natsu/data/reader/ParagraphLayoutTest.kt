package io.mikoshift.natsu.data.reader

import io.mikoshift.natsu.domain.model.Document
import io.mikoshift.natsu.domain.model.readingProgressPercent
import org.junit.Assert.assertEquals
import org.junit.Test

class ParagraphLayoutTest {

    @Test
    fun buildParagraphLayout_matchesSplitIntoParagraphs() {
        val text = "Line one\n\nLine two\n  \nLine three\n"
        val layout = buildParagraphLayout(text)
        assertEquals(listOf("Line one", "Line two", "Line three"), layout.paragraphs)
    }

    @Test
    fun buildParagraphLayout_tracksStartOffsets() {
        val text = "First\n\nSecond"
        val layout = buildParagraphLayout(text)
        assertEquals(listOf(0, 7), layout.startOffsets)
    }

    @Test
    fun paragraphIndexForCharOffset_findsContainingParagraph() {
        val layout = buildParagraphLayout("First\n\nSecond\n\nThird")
        assertEquals(0, layout.paragraphIndexForCharOffset(2))
        assertEquals(1, layout.paragraphIndexForCharOffset(7))
        assertEquals(2, layout.paragraphIndexForCharOffset(20))
    }

    @Test
    fun readingProgressPercent_usesCharOffset() {
        val document = Document(
            id = "1",
            title = "Test",
            filePath = "/tmp/test.txt",
            importedAt = 0L,
            charCount = 200,
            lastReadCharOffset = 50,
            lastReadParagraphIndex = 0,
        )
        assertEquals(25, document.readingProgressPercent())
    }
}
