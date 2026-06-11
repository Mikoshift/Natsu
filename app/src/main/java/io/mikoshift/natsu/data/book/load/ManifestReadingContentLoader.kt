package io.mikoshift.natsu.data.book.load

import io.mikoshift.natsu.data.book.BookStorage
import io.mikoshift.natsu.domain.model.reading.ReadingBook
import java.io.File

class ManifestReadingContentLoader(
    private val bookStorage: BookStorage,
    formatLoaders: List<FormatReadingLoader>,
) {
    private val loadersByFormat = formatLoaders.associateBy { it.format }

    suspend fun load(documentId: String, storagePath: String, title: String): Result<ReadingBook> =
        runCatching {
            val bookDir = File(storagePath)
            require(bookDir.isDirectory) { "Book storage is not a directory: $storagePath" }
            val manifest = bookStorage.readManifest(bookDir)
            val loader = loadersByFormat[manifest.format]
                ?: throw IllegalStateException("No loader registered for format: ${manifest.format}")
            val sections = manifest.sections.map { section ->
                loader.loadSection(bookDir, section)
            }
            ReadingBook(
                id = documentId,
                title = title.ifBlank { manifest.title },
                sections = sections,
            )
        }
}
