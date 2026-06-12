package io.mikoshift.natsu.ui.reader.web

import io.mikoshift.natsu.domain.model.TextToken
import org.junit.Assert.assertEquals
import org.junit.Test

class CompoundTokenScannerTest {

    @Test
    fun compoundCandidates_returnsLongestFirst() {
        val tokens = listOf(
            token("食べ"),
            token("物"),
            token("です"),
        )
        val candidates = CompoundTokenScanner.compoundCandidates(tokens, tapIndex = 1)

        assertEquals(listOf("食べ物です", "食べ物", "物です"), candidates)
    }

    @Test
    fun compoundCandidates_skipsNonClickableTokens() {
        val tokens = listOf(
            token("食べ"),
            token("、", isClickable = false),
            token("物"),
        )
        val candidates = CompoundTokenScanner.compoundCandidates(tokens, tapIndex = 2)

        assertEquals(listOf("食べ物"), candidates)
    }

    @Test
    fun compoundCandidates_includesTapTokenInWindow() {
        val tokens = listOf(
            token("日本"),
            token("語"),
            token("学習"),
        )
        val candidates = CompoundTokenScanner.compoundCandidates(tokens, tapIndex = 1)

        assertEquals(listOf("日本語学習", "日本語", "語学習"), candidates)
    }

    private fun token(surface: String, isClickable: Boolean = true) = TextToken(
        surface = surface,
        reading = surface,
        lemma = surface,
        partOfSpeech = "名詞",
        isClickable = isClickable,
    )
}
