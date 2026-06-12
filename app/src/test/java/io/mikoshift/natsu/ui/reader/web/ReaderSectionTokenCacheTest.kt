package io.mikoshift.natsu.ui.reader.web

import io.mikoshift.natsu.domain.model.TextToken
import io.mikoshift.natsu.domain.model.reading.ReadingBlock
import io.mikoshift.natsu.domain.model.reading.ReadingSection
import io.mikoshift.natsu.domain.model.reading.SectionLayout
import io.mikoshift.natsu.domain.model.reading.SectionReadingContent
import io.mikoshift.natsu.domain.model.reading.TextSpan
import io.mikoshift.natsu.domain.repository.TextTokenizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class ReaderSectionTokenCacheTest {

    @Test
    fun warm_preloadsTokensByParagraphIndex() {
        val cache = ReaderSectionTokenCache()
        val first = token("猫")
        val second = token("犬")
        val tokenizer = object : TextTokenizer {
            override fun tokenize(text: String): List<TextToken> = error("unused")
            override fun tokenizeParagraph(spans: List<TextSpan>): List<TextToken> =
                when (spans.joinToString("") { it.text }) {
                    "猫" -> listOf(first)
                    "犬" -> listOf(second)
                    else -> emptyList()
                }
            override fun tokenizeParagraphs(paragraphs: List<String>): List<List<TextToken>> =
                error("unused")
        }

        cache.warm(
            sectionId = "s1",
            sectionContent = sectionContent(
                paragraphs = listOf("猫", "犬"),
                blocks = listOf(
                    ReadingBlock.Paragraph(listOf(TextSpan(text = "猫"))),
                    ReadingBlock.Paragraph(listOf(TextSpan(text = "犬"))),
                ),
            ),
            tokenizer = tokenizer,
        )

        assertSame(first, cache.tokens("s1", paragraphIndex = 0)?.first())
        assertSame(second, cache.tokens("s1", paragraphIndex = 1)?.first())
    }

    @Test
    fun tokens_invalidIndex_returnsNull() {
        val cache = ReaderSectionTokenCache()
        assertNull(cache.tokens("missing", paragraphIndex = 0))
        assertNull(cache.tokens("s1", paragraphIndex = -1))
    }

    @Test
    fun tokensByText_tokenizesOnceForFallback() {
        val cache = ReaderSectionTokenCache()
        var calls = 0

        val tokens = cache.tokensByText("s1", "本") {
            calls += 1
            listOf(token("本"))
        }

        assertEquals(1, calls)
        assertEquals("本", tokens.first().surface)
        assertEquals(1, cache.tokensByText("s1", "本") { error("should not re-tokenize") }.size)
    }

    private fun sectionContent(
        paragraphs: List<String>,
        blocks: List<ReadingBlock>,
    ): SectionReadingContent {
        val layout = SectionLayout(
            sectionId = "s1",
            paragraphs = paragraphs,
            canonicalText = paragraphs.joinToString("\n"),
            paragraphStartOffsets = paragraphs.indices.map { index ->
                paragraphs.take(index).sumOf { it.length + 1 }.coerceAtLeast(0)
            },
            blockIndexByParagraph = blocks.indices.toList(),
        )
        return SectionReadingContent(
            section = ReadingSection(id = "s1", title = null, blocks = blocks),
            layout = layout,
        )
    }

    private fun token(surface: String): TextToken =
        TextToken(
            surface = surface,
            reading = surface,
            lemma = surface,
            partOfSpeech = "名詞",
            isClickable = true,
        )
}
