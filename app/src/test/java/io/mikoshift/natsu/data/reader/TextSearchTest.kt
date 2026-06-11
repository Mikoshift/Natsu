package io.mikoshift.natsu.data.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextSearchTest {

    @Test
    fun findMatchOffsets_returnsEmptyForBlankQuery() {
        assertTrue(findMatchOffsets("吾輩は猫である", "").isEmpty())
    }

    @Test
    fun findMatchOffsets_findsAllOccurrences() {
        val text = "abc abc ab"
        assertEquals(listOf(0, 4), findMatchOffsets(text, "abc"))
    }

    @Test
    fun findMatchOffsets_findsJapaneseSubstring() {
        val text = "吾輩は猫である。名前はまだ無い。"
        assertEquals(listOf(3), findMatchOffsets(text, "猫"))
        assertEquals(listOf(0, 10), findMatchOffsets(text, "は"))
    }

    @Test
    fun paragraphIndexForMatch_mapsOffsetToParagraph() {
        val layout = buildParagraphLayout("First\n\nSecond\n\nThird")
        assertEquals(1, layout.paragraphIndexForMatch(7))
    }

    @Test
    fun localHighlightRange_mapsGlobalOffsetToParagraphRange() {
        assertEquals(3 until 4, localHighlightRange(matchOffset = 10, queryLength = 1, paragraphStartOffset = 7))
    }
}
