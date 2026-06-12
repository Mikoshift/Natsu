package io.mikoshift.natsu.ui.reader.web

import io.mikoshift.natsu.domain.model.TextToken
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class ReaderSectionTokenCacheTest {

    @Test
    fun warm_preloadsParagraphTokens() {
        val cache = ReaderSectionTokenCache()
        val first = token("猫")
        val second = token("犬")

        cache.warm(
            sectionId = "s1",
            paragraphs = listOf("猫", "犬"),
            tokenize = { text ->
                when (text) {
                    "猫" -> listOf(first)
                    "犬" -> listOf(second)
                    else -> emptyList()
                }
            },
        )

        assertSame(first, cache.tokens("s1", "猫") { emptyList() }.first())
        assertSame(second, cache.tokens("s1", "犬") { emptyList() }.first())
    }

    @Test
    fun tokens_unknownParagraph_tokenizesOnce() {
        val cache = ReaderSectionTokenCache()
        var calls = 0

        val tokens = cache.tokens("s1", "本") {
            calls += 1
            listOf(token("本"))
        }

        assertEquals(1, calls)
        assertEquals("本", tokens.first().surface)
        assertEquals(1, cache.tokens("s1", "本") { error("should not re-tokenize") }.size)
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
