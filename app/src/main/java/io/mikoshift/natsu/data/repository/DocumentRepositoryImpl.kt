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
            val content = File(imported.filePath).readText(Charsets.UTF_8)
            val document = Document(
                id = imported.id,
                title = imported.title,
                filePath = imported.filePath,
                importedAt = imported.importedAt,
                charCount = content.length,
                lastReadCharOffset = 0,
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

    override suspend fun renameDocument(id: String, title: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val trimmed = title.trim()
                require(trimmed.isNotEmpty()) { "Title cannot be empty" }
                documentLocalStore.getById(id)
                    ?: throw NoSuchElementException("Document not found")
                documentLocalStore.updateTitle(id, trimmed)
                Unit
            }
        }

    override suspend fun deleteDocument(id: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val document = documentLocalStore.getById(id)
                    ?: throw NoSuchElementException("Document not found")
                File(document.filePath).delete()
                documentLocalStore.delete(id)
                Unit
            }
        }

    override suspend fun updateReadingPosition(
        documentId: String,
        charOffset: Int,
        paragraphIndex: Int,
    ) {
        documentLocalStore.updateReadingPosition(documentId, charOffset, paragraphIndex)
    }

    override suspend fun ensureCharCount(documentId: String, charCount: Int) {
        val document = documentLocalStore.getById(documentId) ?: return
        if (document.charCount != charCount) {
            documentLocalStore.updateCharCount(documentId, charCount)
        }
    }

    override fun notifyDocumentsChanged() {
        documentLocalStore.notifyDocumentsChanged()
    }
}
