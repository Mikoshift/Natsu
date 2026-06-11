package io.mikoshift.natsu.data.book

import android.net.Uri
import io.mikoshift.natsu.data.book.import.BookImporter

class BookImportCoordinator(
    private val importers: List<BookImporter>,
) {
    suspend fun import(uri: Uri, displayName: String?): Result<ImportedBookPackage> {
        val fileName = displayName ?: uri.lastPathSegment.orEmpty()
        val importer = importers.firstOrNull { it.canImport(fileName, mimeType = null) }
            ?: return Result.failure(
                IllegalArgumentException("Unsupported file format: $fileName"),
            )
        return importer.import(uri, displayName)
    }
}
