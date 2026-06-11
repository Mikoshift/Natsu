package io.mikoshift.natsu.domain.repository

import io.mikoshift.natsu.domain.model.DictionaryEntry

interface DictionaryRepository {
    suspend fun lookup(surface: String, lemma: String, reading: String): DictionaryEntry?

    suspend fun hasEnabledDictionaries(): Boolean
}
