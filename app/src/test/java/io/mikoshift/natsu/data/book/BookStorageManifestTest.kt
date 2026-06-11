package io.mikoshift.natsu.data.book

import io.mikoshift.natsu.domain.model.reading.BookFormat
import io.mikoshift.natsu.domain.model.reading.BookManifest
import io.mikoshift.natsu.domain.model.reading.ManifestSection
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class BookStorageManifestTest {

    @Test
    fun writeAndReadManifest_roundTripsPlainTextPackage() {
        val rootDir = File.createTempFile("books-root", null)
        check(rootDir.delete()) { "Failed to delete temp file" }
        check(rootDir.mkdirs()) { "Failed to create temp books root" }
        val storage = BookStorage(rootDir)
        val bookDir = File(rootDir, "book-1").apply { mkdirs() }
        val manifest = BookManifest(
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
        )

        storage.writeManifest(bookDir, manifest)
        storage.writeContentFile(
            bookDir = bookDir,
            relativePath = BookStorage.PLAIN_TEXT_CONTENT_PATH,
            content = "Hello\n\nWorld",
        )

        val manifestFile = File(bookDir, BookStorage.MANIFEST_FILE_NAME)
        assertEquals(true, manifestFile.exists())

        assertEquals(manifest, storage.readManifest(bookDir))
    }
}
