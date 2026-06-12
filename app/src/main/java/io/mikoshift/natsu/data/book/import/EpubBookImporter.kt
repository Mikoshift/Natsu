package io.mikoshift.natsu.data.book.import

import android.content.Context
import android.net.Uri
import io.mikoshift.natsu.data.book.BookStorage
import io.mikoshift.natsu.data.book.ImportedBookPackage
import io.mikoshift.natsu.data.book.epub.EpubPublicationOpener
import io.mikoshift.natsu.data.book.epub.EpubSpineMapper
import io.mikoshift.natsu.domain.model.reading.BookFormat
import io.mikoshift.natsu.domain.model.reading.BookManifest
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class EpubBookImporter(
    private val context: Context,
    private val bookStorage: BookStorage,
    private val publicationOpener: EpubPublicationOpener = EpubPublicationOpener(context),
) : BookImporter {
    override val format: BookFormat = BookFormat.Epub

    override fun canImport(fileName: String, mimeType: String?): Boolean {
        val extension = fileName.substringAfterLast('.', "").lowercase()
        return extension == "epub" || mimeType == EPUB_MIME_TYPE
    }

    override suspend fun import(uri: Uri, displayName: String?): Result<ImportedBookPackage> =
        withContext(Dispatchers.IO) {
            runCatching {
                val fallbackTitle = TextImportSupport.resolveTitle(context, uri, displayName)
                val bytes = TextImportSupport.readBytes(context, uri)
                val bookDir = bookStorage.createBookDirectory()
                bookStorage.writeBinaryFile(
                    bookDir = bookDir,
                    relativePath = BookStorage.SOURCE_EPUB_PATH,
                    bytes = bytes,
                )
                bookStorage.unzipEpubToBookDir(bookDir, bytes)
                val epubFile = File(bookDir, BookStorage.SOURCE_EPUB_PATH)
                val publication = try {
                    publicationOpener.open(epubFile)
                } catch (error: Exception) {
                    throw CorruptEpubException(cause = error)
                }
                val sections = EpubSpineMapper.mapSpineToManifestSections(publication)
                if (sections.isEmpty()) {
                    throw UnsupportedEpubException()
                }
                val title = publication.metadata.title
                    ?.takeIf { it.isNotBlank() }
                    ?: fallbackTitle
                bookStorage.writeManifest(
                    bookDir = bookDir,
                    manifest = BookManifest(
                        version = MANIFEST_VERSION,
                        format = BookFormat.Epub,
                        title = title,
                        sections = sections,
                    ),
                )
                ImportedBookPackage(
                    id = bookDir.name,
                    title = title,
                    storagePath = bookDir.absolutePath,
                    sourceFormat = BookFormat.Epub,
                    importedAt = System.currentTimeMillis(),
                )
            }
        }

    companion object {
        private const val MANIFEST_VERSION = 1
        private const val EPUB_MIME_TYPE = "application/epub+zip"
    }
}
