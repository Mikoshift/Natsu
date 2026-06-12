package io.mikoshift.natsu.data.dictionary.deinflect

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JapaneseDeinflectorTest {

    @Test
    fun deinflect_ichidanVerbForms() {
        assertContainsBase("食べて", "食べる")
        assertContainsBase("食べた", "食べる")
        assertContainsBase("食べない", "食べる")
        assertContainsBase("食べます", "食べる")
        assertContainsBase("食べました", "食べる")
    }

    @Test
    fun deinflect_godanVerbForms() {
        assertContainsBase("行った", "行く")
        assertContainsBase("書いた", "書く")
        assertContainsBase("話した", "話す")
        assertContainsBase("読んだ", "読む")
        assertContainsBase("泳いだ", "泳ぐ")
        assertContainsBase("買った", "買う")
    }

    @Test
    fun deinflect_iAdjectiveForms() {
        assertContainsBase("高くない", "高い")
        assertContainsBase("高かった", "高い")
    }

    @Test
    fun deinflect_naAdjectiveForms() {
        assertContainsBase("静かだった", "静かだ")
        assertContainsBase("静かじゃない", "静かだ")
    }

    @Test
    fun deinflect_suruAndKuru() {
        assertContainsBase("勉強した", "勉強する")
        assertContainsBase("来た", "来る")
    }

    @Test
    fun deinflect_includesConditionsOut() {
        val candidates = JapaneseDeinflector.deinflect("食べた")
        val match = candidates.first { it.text == "食べる" }
        assertTrue(match.conditionsOut.contains("v1"))
        assertEquals("past", match.ruleName)
    }

    private fun assertContainsBase(inflected: String, expectedBase: String) {
        val texts = JapaneseDeinflector.deinflect(inflected).map { it.text }
        assertTrue(
            "Expected $expectedBase among deinflections of $inflected, got $texts",
            expectedBase in texts,
        )
    }
}
