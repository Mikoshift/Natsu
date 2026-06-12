package io.mikoshift.natsu.data.book.load

import io.mikoshift.natsu.data.book.BookStorage
import io.mikoshift.natsu.domain.model.reading.BookFormat
import io.mikoshift.natsu.domain.model.reading.BookManifest
import io.mikoshift.natsu.domain.model.reading.ManifestSection
import io.mikoshift.natsu.domain.model.reading.ReadingBook
import java.io.File

data class LoaderTestSection(
    val id: String,
    val title: String? = null,
    val path: String,
    val content: String,
)

object LoaderTestBooks {
    fun createPlainTextPackage(
        booksRoot: File,
        bookId: String = "book-1",
        title: String = "Test Book",
        sections: List<LoaderTestSection>,
    ): File {
        val storage = BookStorage(booksRoot)
        val bookDir = File(booksRoot, bookId).apply { mkdirs() }
        storage.writeManifest(
            bookDir = bookDir,
            manifest = BookManifest(
                version = 1,
                format = BookFormat.PlainText,
                title = title,
                sections = sections.map { section ->
                    ManifestSection(
                        id = section.id,
                        title = section.title,
                        path = section.path,
                    )
                },
            ),
        )
        sections.forEach { section ->
            storage.writeContentFile(
                bookDir = bookDir,
                relativePath = section.path,
                content = section.content,
            )
        }
        return bookDir
    }

    suspend fun loadPlainTextBook(
        booksRoot: File,
        bookId: String = "book-1",
        title: String = "Test Book",
        sections: List<LoaderTestSection>,
    ): ReadingBook {
        val bookDir = createPlainTextPackage(
            booksRoot = booksRoot,
            bookId = bookId,
            title = title,
            sections = sections,
        )
        val loader = ManifestReadingContentLoader(
            bookStorage = BookStorage(booksRoot),
            formatLoaders = listOf(PlainTextFormatLoader()),
        )
        return loader.load(
            documentId = bookId,
            storagePath = bookDir.absolutePath,
            title = title,
        ).getOrThrow()
    }
}
