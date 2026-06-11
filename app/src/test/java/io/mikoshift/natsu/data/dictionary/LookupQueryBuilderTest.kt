package io.mikoshift.natsu.data.dictionary

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LookupQueryBuilderTest {

    @Test
    fun buildLookupQueries_includesNormalizedReading() {
        val queries = buildLookupQueries(
            surface = "食べる",
            lemma = "食べる",
            reading = "タベル",
        )

        assertTrue(queries.contains("食べる"))
        assertTrue(queries.contains("タベル"))
        assertTrue(queries.contains("たべる"))
    }

    @Test
    fun buildLookupQueries_omitsBlankReadingCandidates() {
        val queries = buildLookupQueries(
            surface = "喫茶",
            lemma = "喫茶",
            reading = "",
        )

        assertEquals(listOf("喫茶"), queries)
        assertFalse(queries.contains(""))
    }

    @Test
    fun buildLookupQueries_filtersWildcardLemma() {
        val queries = buildLookupQueries(
            surface = "です",
            lemma = "*",
            reading = "デス",
        )

        assertFalse(queries.contains("*"))
        assertTrue(queries.contains("です"))
        assertTrue(queries.contains("デス"))
    }

    @Test
    fun buildLookupQueries_includesKatakanaSurfaceVariant() {
        val queries = buildLookupQueries(
            surface = "ひらがな",
            lemma = "ひらがな",
            reading = "ヒラガナ",
        )

        assertTrue(queries.contains("ひらがな"))
        assertTrue(queries.contains("ヒラガナ"))
    }

    @Test
    fun buildLookupQueries_deduplicatesWhenSurfaceEqualsLemma() {
        val queries = buildLookupQueries(
            surface = "食べる",
            lemma = "食べる",
            reading = "タベル",
        )

        assertEquals(queries.size, queries.distinct().size)
        assertEquals(1, queries.count { it == "食べる" })
    }
}
