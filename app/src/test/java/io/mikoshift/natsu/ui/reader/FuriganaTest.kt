package io.mikoshift.natsu.ui.reader

import io.mikoshift.natsu.domain.model.TextToken
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FuriganaTest {

    @Test
    fun shouldShowFurigana_kanjiWithReading_returnsTrue() {
        val token = token(surface = "猫", reading = "ネコ")
        assertTrue(shouldShowFurigana(token))
    }

    @Test
    fun shouldShowFurigana_kanaOnly_returnsFalse() {
        val token = token(surface = "まだ", reading = "マダ")
        assertFalse(shouldShowFurigana(token))
    }

    @Test
    fun shouldShowFurigana_blankReading_returnsFalse() {
        val token = token(surface = "猫", reading = "")
        assertFalse(shouldShowFurigana(token))
    }

    @Test
    fun shouldShowFurigana_kanjiVerb_returnsTrue() {
        val token = token(surface = "食べる", reading = "タベル")
        assertTrue(shouldShowFurigana(token))
    }

    @Test
    fun shouldShowFurigana_latinWord_returnsFalse() {
        val token = token(surface = "Project", reading = "プロジェクト")
        assertFalse(shouldShowFurigana(token))
    }

    @Test
    fun shouldShowFurigana_latinWithStarReading_returnsFalse() {
        val token = token(surface = "the", reading = "*")
        assertFalse(shouldShowFurigana(token))
    }

    @Test
    fun containsKanji_detectsKanjiInMixedText() {
        assertTrue(containsKanji("吾輩は猫"))
        assertFalse(containsKanji("Project Gutenberg"))
    }

    private fun token(
        surface: String,
        reading: String,
    ): TextToken = TextToken(
        surface = surface,
        reading = reading,
        lemma = surface,
        partOfSpeech = "名詞",
        isClickable = true,
    )
}
