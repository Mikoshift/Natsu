package io.mikoshift.natsu.domain.repository

import android.net.Uri
import io.mikoshift.natsu.domain.model.Document
import kotlinx.coroutines.flow.Flow

interface DocumentRepository {
    fun observeDocuments(): Flow<List<Document>>

    suspend fun getDocument(id: String): Document?

    suspend fun importTextFile(uri: Uri, displayName: String?): Result<Document>

    suspend fun readDocumentText(document: Document): Result<String>

    suspend fun updateReadingPosition(documentId: String, paragraphIndex: Int)
}
