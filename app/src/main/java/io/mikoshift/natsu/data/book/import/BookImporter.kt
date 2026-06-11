package io.mikoshift.natsu.data.book.import

import android.net.Uri
import io.mikoshift.natsu.data.book.ImportedBookPackage
import io.mikoshift.natsu.domain.model.reading.BookFormat

interface BookImporter {
    val format: BookFormat

    fun canImport(fileName: String, mimeType: String?): Boolean

    suspend fun import(uri: Uri, displayName: String?): Result<ImportedBookPackage>
}
