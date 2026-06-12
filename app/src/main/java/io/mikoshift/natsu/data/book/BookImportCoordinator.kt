package io.mikoshift.natsu.data.book

import android.content.Context
import android.net.Uri
import io.mikoshift.natsu.data.book.import.BookImporter
import io.mikoshift.natsu.data.book.import.UnsupportedFormatException

class BookImportCoordinator(
    private val context: Context,
    private val importers: List<BookImporter>,
) {
    suspend fun import(uri: Uri, displayName: String?): Result<ImportedBookPackage> {
        val fileName = displayName ?: uri.lastPathSegment.orEmpty()
        val mimeType = context.contentResolver.getType(uri)
        val importer = importers.firstOrNull { it.canImport(fileName, mimeType) }
            ?: return Result.failure(UnsupportedFormatException(fileName))
        return importer.import(uri, displayName)
    }
}
