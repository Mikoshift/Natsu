package io.mikoshift.natsu.domain.repository

import io.mikoshift.natsu.domain.model.DictionaryCatalogItem
import io.mikoshift.natsu.domain.model.InstalledDictionary
import kotlinx.coroutines.flow.Flow

enum class PriorityDirection {
    Up,
    Down,
}

interface DictionaryManagerRepository {
    suspend fun getCatalog(): List<DictionaryCatalogItem>

    fun observeDictionaries(): Flow<List<InstalledDictionary>>

    suspend fun downloadDictionary(catalogId: String): Result<Unit>

    suspend fun deleteDictionary(catalogId: String)

    suspend fun setEnabled(catalogId: String, enabled: Boolean)

    suspend fun movePriority(catalogId: String, direction: PriorityDirection)

    suspend fun hasEnabledDictionaries(): Boolean
}
