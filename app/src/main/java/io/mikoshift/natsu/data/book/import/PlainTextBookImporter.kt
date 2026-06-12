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

class PlainTextBookImporter(
    private val context: Context,
    private val bookStorage: BookStorage,
) : BookImporter {
    override val format: BookFormat = BookFormat.PlainText

    override fun canImport(fileName: String, mimeType: String?): Boolean {
        val extension = fileName.substringAfterLast('.', "").lowercase()
        if (extension in MARKDOWN_EXTENSIONS) return false
        if (mimeType != null && mimeType.startsWith("text/")) return true
        return extension in SUPPORTED_EXTENSIONS ||
            (extension.isEmpty() && mimeType != null && mimeType.startsWith("text/"))
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
                    content = HtmlChapterGenerator.fromPlainText(content),
                )
                bookStorage.writeManifest(
                    bookDir = bookDir,
                    manifest = BookManifest(
                        version = BookStorage.MANIFEST_VERSION_HTML,
                        format = BookFormat.PlainText,
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
                    sourceFormat = BookFormat.PlainText,
                    importedAt = System.currentTimeMillis(),
                )
            }
        }

    companion object {
        private const val MAIN_SECTION_ID = "main"
        private val SUPPORTED_EXTENSIONS = setOf("txt", "text")
        private val MARKDOWN_EXTENSIONS = setOf("md", "markdown")
    }
}
