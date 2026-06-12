package io.mikoshift.natsu.data.dictionary

import io.mikoshift.natsu.domain.model.DictionaryEntry
import io.mikoshift.natsu.domain.model.LookupMatchKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class LruLookupCacheTest {

    @Test
    fun putAndGet_returnsStoredEntry() {
        val cache = LruLookupCache(maxSize = 2)
        val key = LookupCacheKey("食べる", "食べる", "たべる", emptyList())
        val entry = sampleEntry("食べる")

        cache.put(key, entry)

        assertSame(entry, cache.get(key))
    }

    @Test
    fun putAndGet_storesNullResults() {
        val cache = LruLookupCache(maxSize = 2)
        val key = LookupCacheKey("未知", "*", "みち", emptyList())

        cache.put(key, null)

        assertTrue(cache.contains(key))
        assertNull(cache.get(key))
    }

    @Test
    fun evictsOldestEntryWhenFull() {
        val cache = LruLookupCache(maxSize = 2)
        val key1 = LookupCacheKey("a", "a", "a", emptyList())
        val key2 = LookupCacheKey("b", "b", "b", emptyList())
        val key3 = LookupCacheKey("c", "c", "c", emptyList())

        cache.put(key1, sampleEntry("a"))
        cache.put(key2, sampleEntry("b"))
        cache.get(key1)
        cache.put(key3, sampleEntry("c"))

        assertNull(cache.get(key2))
        assertEquals("a", cache.get(key1)?.querySurface)
        assertEquals("c", cache.get(key3)?.querySurface)
    }

    @Test
    fun clear_removesAllEntries() {
        val cache = LruLookupCache(maxSize = 2)
        val key = LookupCacheKey("a", "a", "a", emptyList())
        cache.put(key, sampleEntry("a"))

        cache.clear()

        assertTrue(!cache.contains(key))
    }

    private fun sampleEntry(surface: String) = DictionaryEntry(
        querySurface = surface,
        queryLemma = surface,
        queryReading = surface,
        senses = emptyList(),
        matchKind = LookupMatchKind.Direct,
        matchedExpression = surface,
        matchedReading = surface,
    )
}
