package io.mikoshift.natsu.data.repository

import io.mikoshift.natsu.data.book.BookImportCoordinator
import io.mikoshift.natsu.data.book.BookStorage
import io.mikoshift.natsu.data.book.load.ManifestReadingContentLoader
import io.mikoshift.natsu.data.local.DocumentLocalStore
import io.mikoshift.natsu.data.reader.ReadingLayoutBuilder
import io.mikoshift.natsu.domain.model.Document
import io.mikoshift.natsu.domain.repository.DocumentRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext

class DocumentRepositoryImpl(
    private val documentLocalStore: DocumentLocalStore,
    private val bookImportCoordinator: BookImportCoordinator,
    private val bookStorage: BookStorage,
    private val manifestReadingContentLoader: ManifestReadingContentLoader,
    private val readingLayoutBuilder: ReadingLayoutBuilder,
) : DocumentRepository {

    override fun observeDocuments(): Flow<List<Document>> = flow {
        emit(documentLocalStore.getAll())
        documentLocalStore.observeDocuments().collect {
            emit(documentLocalStore.getAll())
        }
    }

    override suspend fun getDocument(id: String): Document? =
        documentLocalStore.getById(id)

    override suspend fun importBook(
        uri: android.net.Uri,
        displayName: String?,
    ): Result<Document> =
        bookImportCoordinator.import(uri, displayName).mapCatching { imported ->
            val readingBook = manifestReadingContentLoader.load(
                documentId = imported.id,
                storagePath = imported.storagePath,
                title = imported.title,
            ).getOrThrow()
            val layout = readingLayoutBuilder.build(readingBook)
            val document = Document(
                id = imported.id,
                title = imported.title,
                storagePath = imported.storagePath,
                sourceFormat = imported.sourceFormat,
                importedAt = imported.importedAt,
                charCount = layout.canonicalText.length,
                lastReadCharOffset = 0,
                lastReadParagraphIndex = 0,
            )
            documentLocalStore.insert(document)
            document
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
                bookStorage.deleteBookPackage(document.storagePath)
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
