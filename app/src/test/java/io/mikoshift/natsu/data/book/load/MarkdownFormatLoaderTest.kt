package io.mikoshift.natsu.data.book.load

import io.mikoshift.natsu.data.book.BookStorage
import io.mikoshift.natsu.domain.model.reading.ManifestSection
import io.mikoshift.natsu.domain.model.reading.ReadingBlock
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.charset.StandardCharsets

class MarkdownFormatLoaderTest {

    private val loader = MarkdownFormatLoader()

    @Test
    fun loadSection_parsesMarkdownBlocks() = runBlocking {
        val bookDir = createBookDirectory(
            markdown = """
            # Title

            Body text.

            ![Cover](images/cover.png)
            """.trimIndent(),
        )
        File(bookDir, "images/cover.png").apply {
            parentFile?.mkdirs()
            writeText("png", StandardCharsets.UTF_8)
        }

        val section = loader.loadSection(
            bookDir = bookDir,
            section = ManifestSection(
                id = "main",
                title = null,
                path = BookStorage.MARKDOWN_CONTENT_PATH,
            ),
        )

        assertEquals(3, section.blocks.size)
        assertEquals(ReadingBlock.Heading("Title", 1), section.blocks[0])
        assertTrue(section.blocks[2] is ReadingBlock.Image)
        assertEquals("images/cover.png", (section.blocks[2] as ReadingBlock.Image).relativePath)
    }

    @Test
    fun resolveAssetPath_normalizesRelativeImagePath() {
        val bookDir = File.createTempFile("book", null).apply {
            check(delete())
            check(mkdirs())
        }

        val resolved = loader.resolveAssetPath(
            bookDir = bookDir,
            sectionPath = BookStorage.MARKDOWN_CONTENT_PATH,
            destination = "images/cover.png",
        )

        assertEquals("images/cover.png", resolved)
    }

    private fun createBookDirectory(markdown: String): File {
        val bookDir = File.createTempFile("book", null)
        check(bookDir.delete()) { "Failed to delete temp file" }
        check(bookDir.mkdirs()) { "Failed to create temp book directory" }
        File(bookDir, BookStorage.MARKDOWN_CONTENT_PATH)
            .writeText(markdown, StandardCharsets.UTF_8)
        return bookDir
    }
}
