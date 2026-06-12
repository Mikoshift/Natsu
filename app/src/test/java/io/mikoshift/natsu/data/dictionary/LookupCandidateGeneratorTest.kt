package io.mikoshift.natsu.data.dictionary

import io.mikoshift.natsu.domain.model.LookupMatchKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LookupCandidateGeneratorTest {

    @Test
    fun generate_ordersDirectBeforeDeinflection() {
        val candidates = LookupCandidateGenerator.generate(
            surface = "食べた",
            lemma = "食べる",
            reading = "たべた",
        )

        val kinds = candidates.map { it.kind }
        val directIndex = kinds.indexOf(LookupMatchKind.Direct)
        val deinflectionIndex = kinds.indexOfFirst { it == LookupMatchKind.Deinflection }
        assertTrue(directIndex >= 0)
        assertTrue(deinflectionIndex >= 0)
        assertTrue(directIndex < deinflectionIndex)
    }

    @Test
    fun generate_includesLemmaAndDeinflectionCandidates() {
        val candidates = LookupCandidateGenerator.generate(
            surface = "食べた",
            lemma = "食べる",
            reading = "たべた",
        )

        assertTrue(candidates.any { it.text == "食べる" && it.kind == LookupMatchKind.Lemma })
        assertTrue(candidates.any { it.kind == LookupMatchKind.Deinflection })
    }

    @Test
    fun generate_includesCompoundSurfaces() {
        val candidates = LookupCandidateGenerator.generate(
            surface = "物",
            lemma = "物",
            reading = "もの",
            compoundSurfaces = listOf("食べ物", "物"),
        )

        assertTrue(candidates.any { it.text == "食べ物" && it.kind == LookupMatchKind.Compound })
    }

    @Test
    fun generate_deduplicatesByBestKind() {
        val candidates = LookupCandidateGenerator.generate(
            surface = "食べる",
            lemma = "食べる",
            reading = "たべる",
        )

        assertEquals(1, candidates.count { it.text == "食べる" })
        assertEquals(LookupMatchKind.Direct, candidates.first { it.text == "食べる" }.kind)
    }
}
