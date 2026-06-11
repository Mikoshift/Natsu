package io.mikoshift.natsu.data.book.load

import io.mikoshift.natsu.data.book.BookStorage
import io.mikoshift.natsu.domain.model.reading.BookFormat
import io.mikoshift.natsu.domain.model.reading.BookManifest
import io.mikoshift.natsu.domain.model.reading.ManifestSection
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class ManifestReadingContentLoaderTest {

    @Test
    fun load_readsPlainTextBookPackage() = runBlocking {
        val booksRoot = File.createTempFile("books-root", null)
        check(booksRoot.delete()) { "Failed to delete temp file" }
        check(booksRoot.mkdirs()) { "Failed to create temp books root" }

        val storage = BookStorage(booksRoot)
        val bookDir = File(booksRoot, "book-1").apply { mkdirs() }
        storage.writeManifest(
            bookDir = bookDir,
            manifest = BookManifest(
                version = 1,
                format = BookFormat.PlainText,
                title = "Sample",
                sections = listOf(
                    ManifestSection(
                        id = "main",
                        title = null,
                        path = BookStorage.PLAIN_TEXT_CONTENT_PATH,
                    ),
                ),
            ),
        )
        storage.writeContentFile(
            bookDir = bookDir,
            relativePath = BookStorage.PLAIN_TEXT_CONTENT_PATH,
            content = "Line one\n\nLine two",
        )

        val loader = ManifestReadingContentLoader(
            bookStorage = storage,
            formatLoaders = listOf(PlainTextFormatLoader()),
        )
        val book = loader.load(
            documentId = "book-1",
            storagePath = bookDir.absolutePath,
            title = "Sample",
        ).getOrThrow()

        assertEquals("book-1", book.id)
        assertEquals("Sample", book.title)
        assertEquals(1, book.sections.size)
        assertEquals(2, book.sections.single().blocks.size)
    }
}
