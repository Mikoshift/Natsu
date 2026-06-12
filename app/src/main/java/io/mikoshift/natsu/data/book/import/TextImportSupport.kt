package io.mikoshift.natsu.data.book.import

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.ByteArrayOutputStream

internal object TextImportSupport {
    const val MAX_IMPORT_BYTES = 50 * 1024 * 1024

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

    fun readBytes(context: Context, uri: Uri): ByteArray {
        val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
            readBytesWithLimit(input, MAX_IMPORT_BYTES)
        } ?: throw CannotOpenFileException()
        if (bytes.isEmpty()) {
            throw EmptyFileException()
        }
        return bytes
    }

    fun readText(context: Context, uri: Uri): String {
        val bytes = readBytes(context, uri)
        return TextCharsetDecoder.decode(bytes).text
    }

    private fun readBytesWithLimit(input: java.io.InputStream, maxBytes: Int): ByteArray {
        val buffer = ByteArrayOutputStream()
        val chunk = ByteArray(8192)
        var total = 0
        while (true) {
            val read = input.read(chunk)
            if (read == -1) break
            total += read
            if (total > maxBytes) {
                throw FileTooLargeException()
            }
            buffer.write(chunk, 0, read)
        }
        return buffer.toByteArray()
    }

    private fun stripExtension(name: String): String {
        val dotIndex = name.lastIndexOf('.')
        return if (dotIndex > 0) name.substring(0, dotIndex) else name
    }
}
