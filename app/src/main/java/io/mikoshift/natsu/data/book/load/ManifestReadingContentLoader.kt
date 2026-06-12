package io.mikoshift.natsu.data.book.load

import io.mikoshift.natsu.data.book.BookStorage
import io.mikoshift.natsu.domain.model.reading.BookManifest
import io.mikoshift.natsu.domain.model.reading.ManifestSection
import io.mikoshift.natsu.domain.model.reading.ReadingBook
import io.mikoshift.natsu.domain.model.reading.ReadingSection

class ManifestReadingContentLoader(
    private val bookStorage: BookStorage,
    formatLoaders: List<FormatReadingLoader>,
) {
    private val loadersByFormat = formatLoaders.associateBy { it.format }

    suspend fun load(documentId: String, storagePath: String, title: String): Result<ReadingBook> =
        runCatching {
            val bookDir = bookStorage.validatedBookDirectory(documentId, storagePath)
            require(bookDir.isDirectory) {
                "Book storage is not a directory: ${bookDir.absolutePath}"
            }
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

    suspend fun loadManifest(storagePath: String, documentId: String): Result<BookManifest> =
        runCatching {
            val bookDir = bookStorage.validatedBookDirectory(documentId, storagePath)
            bookStorage.readManifest(bookDir)
        }

    suspend fun loadSection(
        documentId: String,
        storagePath: String,
        sectionId: String,
    ): Result<ReadingSection> = runCatching {
        val bookDir = bookStorage.validatedBookDirectory(documentId, storagePath)
        val manifest = bookStorage.readManifest(bookDir)
        val manifestSection = manifest.sections.firstOrNull { it.id == sectionId }
            ?: throw NoSuchElementException("Section not found: $sectionId")
        loadManifestSection(bookDir, manifest, manifestSection)
    }

    private suspend fun loadManifestSection(
        bookDir: java.io.File,
        manifest: BookManifest,
        section: ManifestSection,
    ): ReadingSection {
        val loader = loadersByFormat[manifest.format]
            ?: throw IllegalStateException("No loader registered for format: ${manifest.format}")
        return loader.loadSection(bookDir, section)
    }
}
