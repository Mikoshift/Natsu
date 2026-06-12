package io.mikoshift.natsu.ui.reader.web

import io.mikoshift.natsu.domain.model.TextToken
import org.junit.Assert.assertEquals
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

    private fun token(surface: String): TextToken =
        TextToken(
            surface = surface,
            reading = surface,
            lemma = surface,
            partOfSpeech = "名詞",
            isClickable = true,
        )
}
