package io.mikoshift.natsu.data.book.load

import io.mikoshift.natsu.data.book.BookStorage
import io.mikoshift.natsu.domain.model.reading.BookFormat
import io.mikoshift.natsu.domain.model.reading.ReadingBlock
import io.mikoshift.natsu.domain.model.reading.TextSpan
import kotlinx.coroutines.runBlocking
import java.nio.charset.StandardCharsets
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class LoaderContractTest {

    @Test
    fun plainTextLoader_satisfiesContract_singleSection() = runBlocking {
        val booksRoot = createBooksRoot()
        val book = LoaderTestBooks.loadPlainTextBook(
            booksRoot = booksRoot,
            sections = listOf(
                LoaderTestSection(
                    id = "main",
                    path = BookStorage.PLAIN_TEXT_CONTENT_PATH,
                    content = "Line one\n\nLine two\n\nLine three",
                ),
            ),
        )

        LoaderContract.verify(
            book = book,
            searchQueries = listOf("Line", "two"),
        )
    }

    @Test
    fun plainTextLoader_satisfiesContract_multiSection() = runBlocking {
        val booksRoot = createBooksRoot()
        val book = LoaderTestBooks.loadPlainTextBook(
            booksRoot = booksRoot,
            sections = listOf(
                LoaderTestSection(
                    id = "chapter-1",
                    title = "One",
                    path = "chapter-1.txt",
                    content = "Chapter one\n\nStill chapter one",
                ),
                LoaderTestSection(
                    id = "chapter-2",
                    title = "Two",
                    path = "chapter-2.txt",
                    content = "Chapter two",
                ),
            ),
        )

        LoaderContract.verify(
            book = book,
            searchQueries = listOf("Chapter", "one", "two"),
        )
    }

    @Test
    fun plainTextLoader_satisfiesContract_japaneseText() = runBlocking {
        val booksRoot = createBooksRoot()
        val book = LoaderTestBooks.loadPlainTextBook(
            booksRoot = booksRoot,
            sections = listOf(
                LoaderTestSection(
                    id = "main",
                    path = BookStorage.PLAIN_TEXT_CONTENT_PATH,
                    content = "吾輩は猫である。\n\n名前はまだ無い。",
                ),
            ),
        )

        LoaderContract.verify(
            book = book,
            searchQueries = listOf("猫", "は", "名前"),
        )
    }

    @Test
    fun markdownLoader_satisfiesContract_withHeadingsAndImage() = runBlocking {
        val booksRoot = createBooksRoot()
        val sections = listOf(
            LoaderTestSection(
                id = "main",
                path = BookStorage.MARKDOWN_CONTENT_PATH,
                content = """
                # Chapter

                Body with 猫.

                ![Cover](images/cover.png)
                """.trimIndent(),
            ),
        )
        val bookDir = LoaderTestBooks.createBookPackage(
            booksRoot = booksRoot,
            format = BookFormat.Markdown,
            sections = sections,
        )
        java.io.File(bookDir, "images/cover.png").apply {
            parentFile?.mkdirs()
            writeText("png", StandardCharsets.UTF_8)
        }
        val book = ManifestReadingContentLoader(
            bookStorage = io.mikoshift.natsu.data.book.BookStorage(booksRoot),
            formatLoaders = listOf(MarkdownFormatLoader()),
        ).load(
            documentId = "book-1",
            storagePath = bookDir.absolutePath,
            title = "Test Book",
        ).getOrThrow()

        LoaderContract.verify(
            book = book,
            searchQueries = listOf("Chapter", "猫"),
        )
    }

    @Test
    fun epubLoader_satisfiesContract_withRubyHeadingAndImage() = runBlocking {
        val booksRoot = createBooksRoot()
        val sections = listOf(
            LoaderTestSection(
                id = "spine-0",
                title = "Chapter",
                path = "OEBPS/chapter.xhtml",
                content = """
                <?xml version="1.0" encoding="utf-8"?>
                <html xmlns="http://www.w3.org/1999/xhtml">
                <body>
                  <h1>第一章</h1>
                  <p><ruby>漢字<rt>かんじ</rt></ruby>です。</p>
                  <img src="images/cover.png" alt="Cover"/>
                </body>
                </html>
                """.trimIndent(),
            ),
        )
        val bookDir = LoaderTestBooks.createEpubPackage(
            booksRoot = booksRoot,
            sections = sections,
        )
        File(bookDir, "OEBPS/images/cover.png").apply {
            parentFile?.mkdirs()
            writeText("png", StandardCharsets.UTF_8)
        }
        val book = ManifestReadingContentLoader(
            bookStorage = BookStorage(booksRoot),
            formatLoaders = listOf(EpubFormatLoader()),
        ).load(
            documentId = "book-1",
            storagePath = bookDir.absolutePath,
            title = "Test Book",
        ).getOrThrow()

        LoaderContract.verify(
            book = book,
            searchQueries = listOf("第一章", "漢字", "です"),
        )

        val section = book.sections.single()
        val paragraph = section.blocks
            .filterIsInstance<ReadingBlock.Paragraph>()
            .single { block ->
                block.spans.any { it.reading == "かんじ" }
            }
        assertEquals(
            listOf(
                TextSpan(text = "漢字", reading = "かんじ"),
                TextSpan(text = "です。"),
            ),
            paragraph.spans,
        )
        val image = section.blocks.filterIsInstance<ReadingBlock.Image>().single()
        assertEquals("OEBPS/images/cover.png", image.relativePath)
    }

    private fun createBooksRoot(): File {
        val booksRoot = File.createTempFile("books-root", null)
        check(booksRoot.delete()) { "Failed to delete temp file" }
        check(booksRoot.mkdirs()) { "Failed to create temp books root" }
        return booksRoot
    }
}
