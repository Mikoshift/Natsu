package io.mikoshift.natsu.data.book.load

import io.mikoshift.natsu.domain.model.reading.ReadingBlock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownToReadingBlocksTest {

    @Test
    fun parse_headingParagraphAndImage() {
        val blocks = MarkdownToReadingBlocks.parse(
            """
            # Chapter One

            First paragraph.

            ![Mountain](images/mountain.png)

            ## Notes

            Second paragraph.
            """.trimIndent(),
        )

        assertEquals(5, blocks.size)
        assertEquals(ReadingBlock.Heading("Chapter One", 1), blocks[0])
        assertEquals(
            ReadingBlock.Paragraph(listOf(io.mikoshift.natsu.domain.model.reading.TextSpan("First paragraph."))),
            blocks[1],
        )
        assertTrue(blocks[2] is ReadingBlock.Image)
        assertEquals("images/mountain.png", (blocks[2] as ReadingBlock.Image).relativePath)
        assertEquals(ReadingBlock.Heading("Notes", 2), blocks[3])
    }

    @Test
    fun parse_fencedCodeBlock_becomesParagraph() {
        val blocks = MarkdownToReadingBlocks.parse(
            """
            ```kotlin
            val x = 1
            ```
            """.trimIndent(),
        )

        assertEquals(1, blocks.size)
        assertEquals("val x = 1", (blocks[0] as ReadingBlock.Paragraph).spans.single().text)
    }
}
