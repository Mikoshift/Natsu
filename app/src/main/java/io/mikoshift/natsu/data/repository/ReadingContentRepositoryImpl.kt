package io.mikoshift.natsu.data.repository

import io.mikoshift.natsu.data.book.BookStorage
import io.mikoshift.natsu.data.book.load.ManifestReadingContentLoader
import io.mikoshift.natsu.data.local.DocumentLocalStore
import io.mikoshift.natsu.data.reader.ReadingLayoutBuilder
import io.mikoshift.natsu.domain.model.reading.ReadingContent
import io.mikoshift.natsu.domain.repository.ReadingContentRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ReadingContentRepositoryImpl(
    private val documentLocalStore: DocumentLocalStore,
    private val bookStorage: BookStorage,
    private val manifestReadingContentLoader: ManifestReadingContentLoader,
    private val readingLayoutBuilder: ReadingLayoutBuilder,
) : ReadingContentRepository {

    override suspend fun loadReadingContent(documentId: String) =
        withContext(Dispatchers.IO) {
            runCatching {
                val document = documentLocalStore.getById(documentId)
                    ?: throw NoSuchElementException("Document not found")
                bookStorage.validatedBookDirectory(document.id, document.storagePath)
                val readingBook = manifestReadingContentLoader.load(
                    documentId = document.id,
                    storagePath = document.storagePath,
                    title = document.title,
                ).getOrThrow()
                ReadingContent(
                    book = readingBook,
                    layout = readingLayoutBuilder.build(readingBook),
                )
            }
        }
}
