package io.mikoshift.natsu.data.reader

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.UUID

class TextFileImporter(
    private val context: Context,
) {
    suspend fun import(uri: Uri, displayName: String?): Result<ImportedTextFile> =
        withContext(Dispatchers.IO) {
            runCatching {
                val title = resolveTitle(uri, displayName)
                val content = readUtf8Text(uri)
                val id = UUID.randomUUID().toString()
                val booksDir = File(context.filesDir, "books").apply { mkdirs() }
                val destination = File(booksDir, "$id.txt")
                destination.writeText(content, StandardCharsets.UTF_8)
                ImportedTextFile(
                    id = id,
                    title = title,
                    filePath = destination.absolutePath,
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
        val utf8 = StandardCharsets.UTF_8
        val text = String(bytes, utf8)
        if (text.contains('\uFFFD')) {
            throw IllegalArgumentException("File must be UTF-8 encoded")
        }
        return text.replace("\r\n", "\n").replace('\r', '\n')
    }

    private fun stripExtension(name: String): String {
        val dotIndex = name.lastIndexOf('.')
        return if (dotIndex > 0) name.substring(0, dotIndex) else name
    }
}

data class ImportedTextFile(
    val id: String,
    val title: String,
    val filePath: String,
    val importedAt: Long,
)
