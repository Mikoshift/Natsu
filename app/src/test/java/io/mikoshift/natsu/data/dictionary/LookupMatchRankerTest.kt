package io.mikoshift.natsu.data.dictionary

import io.mikoshift.natsu.domain.model.LookupMatchKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LookupMatchRankerTest {

    @Test
    fun termMatchPriority_prefersExactSurfaceMatch() {
        val exact = termMatchPriority(
            expression = "制御",
            reading = "せいぎょ",
            surface = "制御",
            lemma = "制御",
            queryReading = "セイギョ",
        )
        val homonym = termMatchPriority(
            expression = "生魚",
            reading = "せいぎょ",
            surface = "制御",
            lemma = "制御",
            queryReading = "セイギョ",
        )

        assertTrue(exact < homonym)
    }

    @Test
    fun dedupeTermKey_normalizesReading() {
        assertEquals(
            dedupeTermKey("jitendex", "制御", "せいぎょ"),
            dedupeTermKey("jitendex", "制御", "セイギョ"),
        )
    }

    @Test
    fun matchKindPriority_prefersDirectOverDeinflection() {
        assertTrue(matchKindPriority(LookupMatchKind.Direct) < matchKindPriority(LookupMatchKind.Deinflection))
        assertTrue(matchKindPriority(LookupMatchKind.Lemma) < matchKindPriority(LookupMatchKind.Compound))
    }
}
