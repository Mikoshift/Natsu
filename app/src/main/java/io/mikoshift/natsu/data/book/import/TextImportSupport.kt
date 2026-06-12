package io.mikoshift.natsu.data.book.import

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns

internal object TextImportSupport {
    fun resolveTitle(context: Context, uri: Uri, displayName: String?): String {
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

    fun readText(context: Context, uri: Uri): String {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw CannotOpenFileException()
        if (bytes.isEmpty()) {
            throw EmptyFileException()
        }
        return TextCharsetDecoder.decode(bytes).text
    }

    private fun stripExtension(name: String): String {
        val dotIndex = name.lastIndexOf('.')
        return if (dotIndex > 0) name.substring(0, dotIndex) else name
    }
}
