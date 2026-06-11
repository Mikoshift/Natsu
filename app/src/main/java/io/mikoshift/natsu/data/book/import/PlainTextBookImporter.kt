package io.mikoshift.natsu.data.book.import

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import io.mikoshift.natsu.data.book.BookStorage
import io.mikoshift.natsu.data.book.ImportedBookPackage
import io.mikoshift.natsu.domain.model.reading.BookFormat
import io.mikoshift.natsu.domain.model.reading.BookManifest
import io.mikoshift.natsu.domain.model.reading.ManifestSection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.charset.StandardCharsets

class PlainTextBookImporter(
    private val context: Context,
    private val bookStorage: BookStorage,
) : BookImporter {
    override val format: BookFormat = BookFormat.PlainText

    override fun canImport(fileName: String, mimeType: String?): Boolean {
        if (mimeType != null && mimeType.startsWith("text/")) return true
        val extension = fileName.substringAfterLast('.', "").lowercase()
        return extension in SUPPORTED_EXTENSIONS || extension.isEmpty()
    }

    override suspend fun import(uri: Uri, displayName: String?): Result<ImportedBookPackage> =
        withContext(Dispatchers.IO) {
            runCatching {
                val title = resolveTitle(uri, displayName)
                val content = readUtf8Text(uri)
                val bookDir = bookStorage.createBookDirectory()
                bookStorage.writeContentFile(
                    bookDir = bookDir,
                    relativePath = BookStorage.PLAIN_TEXT_CONTENT_PATH,
                    content = content,
                )
                bookStorage.writeManifest(
                    bookDir = bookDir,
                    manifest = BookManifest(
                        version = MANIFEST_VERSION,
                        format = BookFormat.PlainText,
                        title = title,
                        sections = listOf(
                            ManifestSection(
                                id = MAIN_SECTION_ID,
                                title = null,
                                path = BookStorage.PLAIN_TEXT_CONTENT_PATH,
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

    private fun resolveTitle(uri: Uri, displayName: String?): String {
        displayName?.takeIf { it.isNotBlank() }?.let { return stripExtension(it) }
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex >= 0 && cursor.moveToFirst()) {
                val name = cursor.getString(nameIndex)
                if (!name.isNullOrBlank()) return stripExtension(name)
            }
        }
        return "Untitled"
    }

    private fun readUtf8Text(uri: Uri): String {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IllegalStateException("Cannot open file")
        val text = String(bytes, StandardCharsets.UTF_8)
        if (text.contains('\uFFFD')) {
            throw IllegalArgumentException("File must be UTF-8 encoded")
        }
        return text.replace("\r\n", "\n").replace('\r', '\n')
    }

    private fun stripExtension(name: String): String {
        val dotIndex = name.lastIndexOf('.')
        return if (dotIndex > 0) name.substring(0, dotIndex) else name
    }

    companion object {
        private const val MANIFEST_VERSION = 1
        private const val MAIN_SECTION_ID = "main"
        private val SUPPORTED_EXTENSIONS = setOf("txt", "text", "md", "markdown")
    }
}
