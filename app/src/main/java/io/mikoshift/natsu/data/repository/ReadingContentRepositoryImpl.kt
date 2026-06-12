package io.mikoshift.natsu.data.repository

import io.mikoshift.natsu.data.book.BookStorage
import io.mikoshift.natsu.data.book.load.ManifestReadingContentLoader
import io.mikoshift.natsu.data.local.DocumentLocalStore
import io.mikoshift.natsu.data.reader.ReadingLayoutBuilder
import io.mikoshift.natsu.data.reader.SearchIndexBuilder
import io.mikoshift.natsu.domain.model.reading.ReadingBookOutline
import io.mikoshift.natsu.domain.model.reading.ReadingContent
import io.mikoshift.natsu.domain.model.reading.SectionReadingContent
import io.mikoshift.natsu.domain.repository.ReadingContentRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ReadingContentRepositoryImpl(
    private val documentLocalStore: DocumentLocalStore,
    private val bookStorage: BookStorage,
    private val manifestReadingContentLoader: ManifestReadingContentLoader,
    private val readingLayoutBuilder: ReadingLayoutBuilder,
    private val searchIndexBuilder: SearchIndexBuilder = SearchIndexBuilder(),
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

    override suspend fun loadOutline(documentId: String): Result<ReadingBookOutline> =
        withContext(Dispatchers.IO) {
            runCatching {
                val document = documentLocalStore.getById(documentId)
                    ?: throw NoSuchElementException("Document not found")
                val bookDir = bookStorage.validatedBookDirectory(document.id, document.storagePath)
                val manifest = bookStorage.readManifest(bookDir)
                var searchIndex = bookStorage.readSearchIndex(bookDir)
                if (searchIndex == null) {
                    val readingBook = manifestReadingContentLoader.load(
                        documentId = document.id,
                        storagePath = document.storagePath,
                        title = document.title,
                    ).getOrThrow()
                    val layout = readingLayoutBuilder.build(readingBook)
                    searchIndex = searchIndexBuilder.build(readingBook).copy(
                        totalCharCount = layout.canonicalText.length,
                    )
                    bookStorage.writeSearchIndex(bookDir, searchIndex)
                }
                ReadingBookOutline(
                    id = document.id,
                    title = document.title.ifBlank { manifest.title },
                    manifest = manifest,
                    searchIndex = searchIndex,
                )
            }
        }

    override suspend fun loadSection(
        documentId: String,
        sectionId: String,
    ): Result<SectionReadingContent> = withContext(Dispatchers.IO) {
        runCatching {
            val document = documentLocalStore.getById(documentId)
                ?: throw NoSuchElementException("Document not found")
            bookStorage.validatedBookDirectory(document.id, document.storagePath)
            val section = manifestReadingContentLoader.loadSection(
                documentId = document.id,
                storagePath = document.storagePath,
                sectionId = sectionId,
            ).getOrThrow()
            SectionReadingContent(
                section = section,
                layout = readingLayoutBuilder.buildSection(section),
            )
        }
    }
}
