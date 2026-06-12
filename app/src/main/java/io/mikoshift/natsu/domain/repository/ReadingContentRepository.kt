package io.mikoshift.natsu.domain.repository

import io.mikoshift.natsu.domain.model.reading.ReadingContent
import io.mikoshift.natsu.domain.model.reading.ReadingLayout

interface ReadingContentRepository {
    suspend fun loadReadingContent(documentId: String): Result<ReadingContent>

    suspend fun loadLayout(documentId: String): Result<ReadingLayout> =
        loadReadingContent(documentId).map { it.layout }
}
