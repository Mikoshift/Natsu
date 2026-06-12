package io.mikoshift.natsu.data.book.load

import io.mikoshift.natsu.data.book.BookStorage
import kotlinx.coroutines.runBlocking
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

    private fun createBooksRoot(): File {
        val booksRoot = File.createTempFile("books-root", null)
        check(booksRoot.delete()) { "Failed to delete temp file" }
        check(booksRoot.mkdirs()) { "Failed to create temp books root" }
        return booksRoot
    }
}
