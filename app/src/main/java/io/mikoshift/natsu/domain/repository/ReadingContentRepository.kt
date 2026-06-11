package io.mikoshift.natsu.domain.repository

import io.mikoshift.natsu.domain.model.reading.ReadingLayout

interface ReadingContentRepository {
    suspend fun loadLayout(documentId: String): Result<ReadingLayout>
}
