package io.mikoshift.natsu.data.dictionary

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JapaneseReadingNormalizerTest {

    @Test
    fun toHiragana_convertsKatakana() {
        assertEquals("たべる", toHiragana("タベル"))
    }

    @Test
    fun toKatakana_convertsHiragana() {
        assertEquals("タベル", toKatakana("たべる"))
    }

    @Test
    fun toHiragana_leavesKanjiUnchanged() {
        assertEquals("食べる", toHiragana("食べる"))
    }

    @Test
    fun toHiragana_leavesKanjiOnlyTextUnchanged() {
        assertEquals("喫茶", toHiragana("喫茶"))
    }

    @Test
    fun vuKana_convertsBidirectionally() {
        assertEquals("ゔ", toHiragana("ヴ"))
        assertEquals("ヴ", toKatakana("ゔ"))
    }

    @Test
    fun containsKana_detectsKanaCharacters() {
        assertTrue(containsKana("たべる"))
        assertTrue(containsKana("タベル"))
        assertTrue(containsKana("食べる"))
        assertFalse(containsKana("喫茶"))
        assertFalse(containsKana("hello"))
    }
}
