package io.mikoshift.natsu.data.dictionary

import io.mikoshift.natsu.domain.model.DictionaryEntry

data class LookupCacheKey(
    val surface: String,
    val lemma: String,
    val reading: String,
    val compoundSurfaces: List<String>,
)

class LruLookupCache(
    private val maxSize: Int = 256,
) {
    private val cache = object : LinkedHashMap<LookupCacheKey, DictionaryEntry?>(maxSize, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<LookupCacheKey, DictionaryEntry?>): Boolean {
            return size > maxSize
        }
    }

    @Synchronized
    fun get(key: LookupCacheKey): DictionaryEntry? {
        return if (cache.containsKey(key)) cache[key] else null
    }

    @Synchronized
    fun contains(key: LookupCacheKey): Boolean = cache.containsKey(key)

    @Synchronized
    fun put(key: LookupCacheKey, entry: DictionaryEntry?) {
        cache[key] = entry
    }

    @Synchronized
    fun clear() {
        cache.clear()
    }
}
