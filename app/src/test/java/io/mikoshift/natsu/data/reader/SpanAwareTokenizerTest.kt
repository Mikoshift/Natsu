package io.mikoshift.natsu.data.reader

import io.mikoshift.natsu.domain.model.TextToken
import io.mikoshift.natsu.domain.model.reading.TextSpan
import org.junit.Assert.assertEquals
import org.junit.Test

class SpanAwareTokenizerTest {

    @Test
    fun rubySpan_preservesSourceReading() {
        val tokens = SpanAwareTokenizer.tokenizeParagraph(
            spans = listOf(TextSpan(text = "漢字", reading = "かんじ")),
            tokenizeText = { error("Kuromoji should not run for ruby spans") },
        )

        assertEquals(
            listOf(
                TextToken(
                    surface = "漢字",
                    reading = "かんじ",
                    lemma = "漢字",
                    partOfSpeech = "名詞",
                    isClickable = true,
                ),
            ),
            tokens,
        )
    }

    @Test
    fun plainSpan_usesKuromojiTokenizer() {
        val tokens = SpanAwareTokenizer.tokenizeParagraph(
            spans = listOf(TextSpan(text = "猫")),
            tokenizeText = { text ->
                listOf(
                    TextToken(
                        surface = text,
                        reading = "ネコ",
                        lemma = "猫",
                        partOfSpeech = "名詞",
                        isClickable = true,
                    ),
                )
            },
        )

        assertEquals("猫", tokens.single().surface)
        assertEquals("ネコ", tokens.single().reading)
    }

    @Test
    fun mixedSpans_rubyAndPlain() {
        val tokens = SpanAwareTokenizer.tokenizeParagraph(
            spans = listOf(
                TextSpan(text = "漢字", reading = "かんじ"),
                TextSpan(text = "です"),
            ),
            tokenizeText = { text ->
                listOf(
                    TextToken(
                        surface = text,
                        reading = "デス",
                        lemma = "です",
                        partOfSpeech = "助動詞",
                        isClickable = true,
                    ),
                )
            },
        )

        assertEquals(2, tokens.size)
        assertEquals("かんじ", tokens[0].reading)
        assertEquals("デス", tokens[1].reading)
    }
}
