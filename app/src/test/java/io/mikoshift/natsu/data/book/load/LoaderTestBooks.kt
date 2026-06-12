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
    fun createBookPackage(
        booksRoot: File,
        format: BookFormat,
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
                format = format,
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

    fun createPlainTextPackage(
        booksRoot: File,
        bookId: String = "book-1",
        title: String = "Test Book",
        sections: List<LoaderTestSection>,
    ): File = createBookPackage(
        booksRoot = booksRoot,
        format = BookFormat.PlainText,
        bookId = bookId,
        title = title,
        sections = sections,
    )

    suspend fun loadBook(
        booksRoot: File,
        format: BookFormat,
        bookId: String = "book-1",
        title: String = "Test Book",
        sections: List<LoaderTestSection>,
    ): ReadingBook {
        val bookDir = createBookPackage(
            booksRoot = booksRoot,
            format = format,
            bookId = bookId,
            title = title,
            sections = sections,
        )
        val formatLoaders = when (format) {
            BookFormat.PlainText -> listOf(PlainTextFormatLoader())
            BookFormat.Markdown -> listOf(MarkdownFormatLoader())
            BookFormat.Epub -> listOf(EpubFormatLoader())
        }
        val loader = ManifestReadingContentLoader(
            bookStorage = BookStorage(booksRoot),
            formatLoaders = formatLoaders,
        )
        return loader.load(
            documentId = bookId,
            storagePath = bookDir.absolutePath,
            title = title,
        ).getOrThrow()
    }

    fun createEpubPackage(
        booksRoot: File,
        bookId: String = "book-1",
        title: String = "Test Book",
        sections: List<LoaderTestSection>,
    ): File = createBookPackage(
        booksRoot = booksRoot,
        format = BookFormat.Epub,
        bookId = bookId,
        title = title,
        sections = sections,
    )

    suspend fun loadEpubBook(
        booksRoot: File,
        bookId: String = "book-1",
        title: String = "Test Book",
        sections: List<LoaderTestSection>,
    ): ReadingBook = loadBook(
        booksRoot = booksRoot,
        format = BookFormat.Epub,
        bookId = bookId,
        title = title,
        sections = sections,
    )

    suspend fun loadPlainTextBook(
        booksRoot: File,
        bookId: String = "book-1",
        title: String = "Test Book",
        sections: List<LoaderTestSection>,
    ): ReadingBook = loadBook(
        booksRoot = booksRoot,
        format = BookFormat.PlainText,
        bookId = bookId,
        title = title,
        sections = sections,
    )
}
