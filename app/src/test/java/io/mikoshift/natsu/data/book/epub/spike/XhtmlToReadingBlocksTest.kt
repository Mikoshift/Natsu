package io.mikoshift.natsu.data.book.epub.spike

import io.mikoshift.natsu.domain.model.reading.ReadingBlock
import io.mikoshift.natsu.domain.model.reading.TextSpan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class XhtmlToReadingBlocksTest {

    @Test
    fun parse_plainParagraph() {
        val blocks = XhtmlToReadingBlocks.parse(
            """
            <html><body>
              <p>吾輩は猫である。</p>
            </body></html>
            """.trimIndent(),
        )

        assertEquals(1, blocks.size)
        val paragraph = blocks.single() as ReadingBlock.Paragraph
        assertEquals(listOf(TextSpan(text = "吾輩は猫である。")), paragraph.spans)
    }

    @Test
    fun parse_rubySpan() {
        val blocks = XhtmlToReadingBlocks.parse(
            """
            <html><body>
              <p><ruby>漢字<rt>かんじ</rt></ruby>です。</p>
            </body></html>
            """.trimIndent(),
        )

        val paragraph = blocks.single() as ReadingBlock.Paragraph
        assertEquals(
            listOf(
                TextSpan(text = "漢字", reading = "かんじ"),
                TextSpan(text = "です。"),
            ),
            paragraph.spans,
        )
    }

    @Test
    fun parse_headingAndImage() {
        val blocks = XhtmlToReadingBlocks.parse(
            """
            <html><body>
              <h1>第一章</h1>
              <p>本文</p>
              <img src="images/cover.png" alt="Cover"/>
            </body></html>
            """.trimIndent(),
            baseHref = "OEBPS/chapter.xhtml",
        )

        assertEquals(3, blocks.size)
        assertEquals(ReadingBlock.Heading(text = "第一章", level = 1), blocks[0])
        assertEquals(ReadingBlock.Paragraph(listOf(TextSpan(text = "本文"))), blocks[1])
        assertEquals(
            ReadingBlock.Image(relativePath = "images/cover.png", alt = "Cover"),
            blocks[2],
        )
    }

    @Test
    fun parse_rubyWithoutRt_isPlainText() {
        val blocks = XhtmlToReadingBlocks.parse(
            """
            <html><body>
              <p><ruby>漢字</ruby></p>
            </body></html>
            """.trimIndent(),
        )

        val paragraph = blocks.single() as ReadingBlock.Paragraph
        assertEquals(listOf(TextSpan(text = "漢字")), paragraph.spans)
    }

    @Test
    fun parse_groupRubyWithRb() {
        val blocks = XhtmlToReadingBlocks.parse(
            """
            <html><body>
              <p><ruby><rb>漢字</rb><rt>かんじ</rt></ruby></p>
            </body></html>
            """.trimIndent(),
        )

        val paragraph = blocks.single() as ReadingBlock.Paragraph
        assertEquals(listOf(TextSpan(text = "漢字", reading = "かんじ")), paragraph.spans)
    }

    @Test
    fun parse_footnoteAside_isSkipped() {
        val blocks = XhtmlToReadingBlocks.parse(
            """
            <html><body>
              <p>本文</p>
              <aside epub:type="footnote"><p>脚注</p></aside>
            </body></html>
            """.trimIndent(),
        )

        assertEquals(1, blocks.size)
        val paragraph = blocks.single() as ReadingBlock.Paragraph
        assertEquals("本文", paragraph.spans.single().text)
    }

    @Test
    fun parse_nav_isSkipped() {
        val blocks = XhtmlToReadingBlocks.parse(
            """
            <html><body>
              <nav><ol><li><a href="ch1.xhtml">One</a></li></ol></nav>
              <p>Text</p>
            </body></html>
            """.trimIndent(),
        )

        assertEquals(1, blocks.size)
        assertTrue(blocks.single() is ReadingBlock.Paragraph)
    }
}
