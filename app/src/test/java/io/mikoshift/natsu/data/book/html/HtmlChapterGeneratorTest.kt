package io.mikoshift.natsu.data.book.html

import org.junit.Assert.assertTrue
import org.junit.Test

class HtmlChapterGeneratorTest {

    @Test
    fun fromPlainText_wrapsParagraphsInHtml() {
        val html = HtmlChapterGenerator.fromPlainText("Line one\n\nLine two")

        assertTrue(html.contains("<p>Line one</p>"))
        assertTrue(html.contains("<p>Line two</p>"))
        assertTrue(html.contains("class=\"natsu-chapter\""))
    }

    @Test
    fun fromMarkdown_rendersHeading() {
        val html = HtmlChapterGenerator.fromMarkdown("# Title\n\nBody")

        assertTrue(html.contains("<h1>Title</h1>"))
        assertTrue(html.contains("<p>Body</p>"))
    }
}
