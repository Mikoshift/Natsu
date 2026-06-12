package io.mikoshift.natsu.ui.reader.web

import io.mikoshift.natsu.domain.model.TextToken
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReaderWordTapTest {

    @Test
    fun tokenAtCharOffset_findsTokenUnderTap() {
        val tokens = listOf(
            token("吾輩は"),
            token("猫"),
            token("である"),
        )

        val tapped = ReaderWordTap.tokenAtCharOffset(tokens, charOffset = 3)

        assertEquals("猫", tapped?.surface)
    }

    @Test
    fun tokenAtCharOffset_atStart_returnsFirstToken() {
        val tokens = listOf(
            token("吾輩は"),
            token("猫"),
        )

        val tapped = ReaderWordTap.tokenAtCharOffset(tokens, charOffset = 0)

        assertEquals("吾輩は", tapped?.surface)
    }

    @Test
    fun tokenAtCharOffset_atEnd_returnsNull() {
        val tokens = listOf(
            token("吾輩は"),
            token("猫"),
        )

        val tapped = ReaderWordTap.tokenAtCharOffset(tokens, charOffset = 4)

        assertNull(tapped)
    }

    @Test
    fun tokenAtCharOffset_negativeOffset_treatsAsZero() {
        val tokens = listOf(token("猫"))

        val tapped = ReaderWordTap.tokenAtCharOffset(tokens, charOffset = -5)

        assertEquals("猫", tapped?.surface)
    }

    @Test
    fun resolveTapToken_onPunctuation_findsNearestClickableToken() {
        val tokens = listOf(
            token("吾輩は"),
            punctuation("、"),
            token("猫"),
        )

        val tapped = ReaderWordTap.resolveTapToken(tokens, charOffset = 3)

        assertEquals("猫", tapped?.surface)
    }

    @Test
    fun resolveTapToken_outOfRange_doesNotFallbackToFirstToken() {
        val tokens = listOf(
            token("吾輩は"),
            token("猫"),
        )

        assertNull(ReaderWordTap.resolveTapToken(tokens, charOffset = 99))
    }

    private fun token(surface: String): TextToken =
        TextToken(
            surface = surface,
            reading = surface,
            lemma = surface,
            partOfSpeech = "名詞",
            isClickable = true,
        )

    private fun punctuation(surface: String): TextToken =
        TextToken(
            surface = surface,
            reading = surface,
            lemma = surface,
            partOfSpeech = "記号",
            isClickable = false,
        )
}
