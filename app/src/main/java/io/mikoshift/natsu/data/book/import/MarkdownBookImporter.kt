package io.mikoshift.natsu.data.book.import

import android.content.Context
import android.net.Uri
import io.mikoshift.natsu.data.book.BookStorage
import io.mikoshift.natsu.data.book.ImportedBookPackage
import io.mikoshift.natsu.data.book.html.HtmlChapterGenerator
import io.mikoshift.natsu.domain.model.reading.BookFormat
import io.mikoshift.natsu.domain.model.reading.BookManifest
import io.mikoshift.natsu.domain.model.reading.ManifestSection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MarkdownBookImporter(
    private val context: Context,
    private val bookStorage: BookStorage,
) : BookImporter {
    override val format: BookFormat = BookFormat.Markdown

    override fun canImport(fileName: String, mimeType: String?): Boolean {
        val extension = fileName.substringAfterLast('.', "").lowercase()
        return extension in SUPPORTED_EXTENSIONS
    }

    override suspend fun import(uri: Uri, displayName: String?): Result<ImportedBookPackage> =
        withContext(Dispatchers.IO) {
            runCatching {
                val title = TextImportSupport.resolveTitle(context, uri, displayName)
                val content = TextImportSupport.readText(context, uri)
                val bookDir = bookStorage.createBookDirectory()
                bookStorage.writeContentFile(
                    bookDir = bookDir,
                    relativePath = BookStorage.HTML_CONTENT_PATH,
                    content = HtmlChapterGenerator.fromMarkdown(content),
                )
                bookStorage.writeManifest(
                    bookDir = bookDir,
                    manifest = BookManifest(
                        version = BookStorage.MANIFEST_VERSION_HTML,
                        format = BookFormat.Html,
                        title = title,
                        sections = listOf(
                            ManifestSection(
                                id = MAIN_SECTION_ID,
                                title = null,
                                path = BookStorage.HTML_CONTENT_PATH,
                            ),
                        ),
                    ),
                )
                ImportedBookPackage(
                    id = bookDir.name,
                    title = title,
                    storagePath = bookDir.absolutePath,
                    sourceFormat = BookFormat.Markdown,
                    importedAt = System.currentTimeMillis(),
                )
            }
        }

    companion object {
        private const val MAIN_SECTION_ID = "main"
        private val SUPPORTED_EXTENSIONS = setOf("md", "markdown")
    }
}
