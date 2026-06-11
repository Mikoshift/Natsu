package io.mikoshift.natsu.data.dictionary

import io.mikoshift.natsu.domain.model.DictionaryEntry
import io.mikoshift.natsu.domain.model.DictionarySense
import io.mikoshift.natsu.domain.repository.DictionaryRepository

class MultiDictionaryRepository(
    private val localStore: DictionaryLocalStore,
) : DictionaryRepository {

    override suspend fun lookup(surface: String, lemma: String, reading: String): DictionaryEntry? {
        if (!localStore.hasEnabledDictionaries()) return null

        val queries = buildList {
            add(surface)
            add(lemma)
            if (reading.isNotBlank()) add(reading)
        }.distinct().filter { it.isNotBlank() && it != "*" }

        if (queries.isEmpty()) return null

        val rows = localStore.lookupTerms(queries)
        if (rows.isEmpty()) return null

        val senses = rows.map { row ->
            val content = parseSenseContentJson(row.glossesJson)
            DictionarySense(
                dictionaryTitle = row.dictionaryTitle,
                kanji = listOf(row.expression),
                readings = listOf(row.reading),
                partsOfSpeech = content.partsOfSpeech,
                senseBlocks = content.senseBlocks,
            )
        }

        return DictionaryEntry(
            querySurface = surface,
            queryLemma = lemma,
            queryReading = reading,
            senses = senses,
        )
    }

    override suspend fun hasEnabledDictionaries(): Boolean =
        localStore.hasEnabledDictionaries()
}
