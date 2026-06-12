package io.mikoshift.natsu.ui.reader.web

import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderWebUrlsTest {

    @Test
    fun chapterUrl_encodesSegments() {
        val url = ReaderWebUrls.chapterUrl(
            documentId = "book-1",
            relativePath = "OEBPS/chapter one.xhtml",
        )

        assertEquals(
            "https://appassets.androidplatform.net/books/book-1/OEBPS/chapter%20one.xhtml",
            url,
        )
    }

    @Test
    fun relativePathFromChapterUrl_roundTrips() {
        val url = ReaderWebUrls.chapterUrl("book-1", "content.html")
        assertEquals("content.html", ReaderWebUrls.relativePathFromChapterUrl(url, "book-1"))
    }
}
