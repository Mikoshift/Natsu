package io.mikoshift.natsu.domain.repository

import io.mikoshift.natsu.domain.model.DictionaryEntry

interface DictionaryRepository {
    suspend fun lookup(
        surface: String,
        lemma: String,
        reading: String,
        compoundSurfaces: List<String> = emptyList(),
    ): DictionaryEntry?

    suspend fun hasEnabledDictionaries(): Boolean
}
