package io.mikoshift.natsu.domain.repository

import android.net.Uri
import io.mikoshift.natsu.domain.model.Document
import io.mikoshift.natsu.domain.model.reading.ReadingLocator
import kotlinx.coroutines.flow.Flow

interface DocumentRepository {
    fun observeDocuments(): Flow<List<Document>>

    suspend fun getDocument(id: String): Document?

    suspend fun importBook(uri: Uri, displayName: String?): Result<Document>

    suspend fun renameDocument(id: String, title: String): Result<Unit>

    suspend fun deleteDocument(id: String): Result<Unit>

    suspend fun updateReadingPosition(
        documentId: String,
        globalCharOffset: Int,
        paragraphIndex: Int,
        locator: ReadingLocator? = null,
    )

    suspend fun ensureCharCount(documentId: String, charCount: Int)

    fun notifyDocumentsChanged()
}
