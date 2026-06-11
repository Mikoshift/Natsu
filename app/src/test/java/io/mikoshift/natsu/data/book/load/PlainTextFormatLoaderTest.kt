package io.mikoshift.natsu.data.book.load

import io.mikoshift.natsu.data.book.BookStorage
import io.mikoshift.natsu.domain.model.reading.BookFormat
import io.mikoshift.natsu.domain.model.reading.BookManifest
import io.mikoshift.natsu.domain.model.reading.ManifestSection
import io.mikoshift.natsu.domain.model.reading.ReadingBlock
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.charset.StandardCharsets

class PlainTextFormatLoaderTest {

    private val loader = PlainTextFormatLoader()

    @Test
    fun loadSection_splitsContentIntoParagraphBlocks() = runBlocking {
        val bookDir = createBookDirectory(
            content = "Line one\n\nLine two\n  \nLine three\n",
        )

        val section = loader.loadSection(
            bookDir = bookDir,
            section = ManifestSection(
                id = "main",
                title = null,
                path = BookStorage.PLAIN_TEXT_CONTENT_PATH,
            ),
        )

        assertEquals("main", section.id)
        assertEquals(3, section.blocks.size)
        assertTrue(section.blocks[0] is ReadingBlock.Paragraph)
        assertEquals(
            "Line one",
            (section.blocks[0] as ReadingBlock.Paragraph).spans.single().text,
        )
        assertEquals(
            "Line three",
            (section.blocks[2] as ReadingBlock.Paragraph).spans.single().text,
        )
    }

    private fun createBookDirectory(content: String): File {
        val bookDir = File.createTempFile("book", null)
        check(bookDir.delete()) { "Failed to delete temp file" }
        check(bookDir.mkdirs()) { "Failed to create temp book directory" }
        File(bookDir, BookStorage.PLAIN_TEXT_CONTENT_PATH)
            .writeText(content, StandardCharsets.UTF_8)
        return bookDir
    }
}
