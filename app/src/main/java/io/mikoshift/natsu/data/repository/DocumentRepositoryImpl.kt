package io.mikoshift.natsu.data.repository

import io.mikoshift.natsu.data.local.DocumentLocalStore
import io.mikoshift.natsu.data.reader.TextFileImporter
import io.mikoshift.natsu.domain.model.Document
import io.mikoshift.natsu.domain.repository.DocumentRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import java.io.File

class DocumentRepositoryImpl(
    private val documentLocalStore: DocumentLocalStore,
    private val textFileImporter: TextFileImporter,
) : DocumentRepository {

    override fun observeDocuments(): Flow<List<Document>> = flow {
        emit(documentLocalStore.getAll())
        documentLocalStore.observeDocuments().collect {
            emit(documentLocalStore.getAll())
        }
    }

    override suspend fun getDocument(id: String): Document? =
        documentLocalStore.getById(id)

    override suspend fun importTextFile(
        uri: android.net.Uri,
        displayName: String?,
    ): Result<Document> =
        textFileImporter.import(uri, displayName).mapCatching { imported ->
            val document = Document(
                id = imported.id,
                title = imported.title,
                filePath = imported.filePath,
                importedAt = imported.importedAt,
                lastReadParagraphIndex = 0,
            )
            documentLocalStore.insert(document)
            document
        }

    override suspend fun readDocumentText(document: Document): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                File(document.filePath).readText(Charsets.UTF_8)
            }
        }

    override suspend fun updateReadingPosition(documentId: String, paragraphIndex: Int) {
        documentLocalStore.updateReadingPosition(documentId, paragraphIndex)
    }
}
