package io.mikoshift.natsu.domain.repository

import io.mikoshift.natsu.domain.model.reading.ReadingBookOutline
import io.mikoshift.natsu.domain.model.reading.ReadingContent
import io.mikoshift.natsu.domain.model.reading.ReadingLayout
import io.mikoshift.natsu.domain.model.reading.SectionReadingContent

interface ReadingContentRepository {
    suspend fun loadReadingContent(documentId: String): Result<ReadingContent>

    suspend fun loadOutline(documentId: String): Result<ReadingBookOutline>

    suspend fun loadSection(documentId: String, sectionId: String): Result<SectionReadingContent>

    suspend fun loadLayout(documentId: String): Result<ReadingLayout> =
        loadReadingContent(documentId).map { it.layout }
}
