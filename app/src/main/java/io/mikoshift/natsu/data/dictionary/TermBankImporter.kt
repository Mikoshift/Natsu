package io.mikoshift.natsu.data.dictionary

import io.mikoshift.natsu.domain.model.SenseBlock
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

data class DictionaryArchiveIndex(
    val title: String,
    val revision: String,
)

class TermBankImporter {
    suspend fun importZip(
        zipFile: File,
        catalogId: String,
        onBatch: suspend (List<TermRecord>) -> Unit,
        onImportProgress: (filesProcessed: Int, totalFiles: Int) -> Unit = { _, _ -> },
    ): DictionaryArchiveIndex {
        val limits = ZipImportLimits()
        ZipFile(zipFile).use { zip ->
            val fileEntries = zip.entries().asSequence()
                .filter { !it.isDirectory }
                .toList()

            val indexEntry = fileEntries.firstOrNull { entry ->
                entry.name.substringAfterLast('/') == INDEX_JSON
            } ?: error("index.json not found in dictionary archive")

            val index = zip.getInputStream(indexEntry).use { input ->
                readIndex(limits.entryInputStream(input))
            }

            val termBankEntries = fileEntries.filter { entry ->
                entry.name.substringAfterLast('/').let { name ->
                    name.startsWith(TERM_BANK_PREFIX) && name.endsWith(".json")
                }
            }
            val totalTermBankFiles = termBankEntries.size.coerceAtLeast(1)
            var filesProcessed = 0

            for (entry in termBankEntries) {
                zip.getInputStream(entry).use { input ->
                    parseTermBankStream(limits.entryInputStream(input), catalogId, onBatch)
                }
                filesProcessed++
                onImportProgress(filesProcessed, totalTermBankFiles)
            }

            return index
        }
    }

    private fun readIndex(input: InputStream): DictionaryArchiveIndex {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var totalRead = 0
        while (true) {
            val toRead = minOf(buffer.size, MAX_INDEX_JSON_BYTES - totalRead + 1)
            if (toRead <= 0) {
                error("index.json exceeds maximum size ($MAX_INDEX_JSON_BYTES bytes)")
            }
            val read = input.read(buffer, 0, toRead)
            if (read == -1) break
            output.write(buffer, 0, read)
            totalRead += read
            if (totalRead > MAX_INDEX_JSON_BYTES) {
                error("index.json exceeds maximum size ($MAX_INDEX_JSON_BYTES bytes)")
            }
        }
        val json = JSONObject(output.toString(StandardCharsets.UTF_8.name()))
        return DictionaryArchiveIndex(
            title = json.getString("title"),
            revision = json.optString("revision", "1"),
        )
    }

    private suspend fun parseTermBankStream(
        input: InputStream,
        catalogId: String,
        onBatch: suspend (List<TermRecord>) -> Unit,
    ) {
        val batch = ArrayList<TermRecord>(BATCH_SIZE)
        val reader = JsonReader(InputStreamReader(input, StandardCharsets.UTF_8))
        reader.use {
            it.beginArray()
            while (it.hasNext()) {
                parseTermEntry(it, catalogId)?.let { term -> batch.add(term) }
                if (batch.size >= BATCH_SIZE) {
                    onBatch(batch)
                    batch.clear()
                }
            }
            it.endArray()
        }
        if (batch.isNotEmpty()) {
            onBatch(batch)
        }
    }

    private fun parseTermEntry(reader: JsonReader, catalogId: String): TermRecord? {
        reader.beginArray()
        if (!reader.hasNext()) {
            reader.endArray()
            return null
        }

        val expression = reader.nextString()
        val reading = if (reader.hasNext()) reader.nextString() else ""
        if (reader.hasNext()) skipOneValue(reader)
        if (reader.hasNext()) skipOneValue(reader)
        val score = if (reader.hasNext()) readScoreValue(reader) else 0

        var senseContent = if (reader.hasNext()) {
            readGlossaryField(reader)
        } else {
            SenseContentData()
        }

        val legacyDefinitions = mutableListOf<String>()
        while (reader.hasNext()) {
            when (val item = readJsonValue(reader)) {
                is String -> {
                    if (item.isNotBlank() && !isJunkDefinition(item)) {
                        legacyDefinitions += item
                    }
                }
            }
        }
        reader.endArray()

        if (!senseContent.hasContent() && legacyDefinitions.isNotEmpty()) {
            senseContent = SenseContentData(
                senseBlocks = listOf(
                    SenseBlock(definitions = legacyDefinitions),
                ),
            )
        }

        senseContent = sanitizeSenseContent(senseContent)
        if (expression.isBlank() || !senseContent.hasContent()) return null
        return TermRecord(
            dictionaryId = catalogId,
            expression = expression,
            reading = reading.ifBlank { expression },
            glossesJson = encodeSenseContent(senseContent),
            score = score,
        )
    }

    private fun readGlossaryField(reader: JsonReader): SenseContentData {
        return when (reader.peek()) {
            JsonToken.STRING -> {
                val text = reader.nextString()
                if (text.isBlank() || isJunkDefinition(text)) {
                    SenseContentData()
                } else {
                    SenseContentData(
                        senseBlocks = listOf(
                            SenseBlock(definitions = listOf(text)),
                        ),
                    )
                }
            }
            JsonToken.BEGIN_ARRAY -> {
                val glossary = readJsonValue(reader) as? JSONArray ?: return SenseContentData()
                extractSenseContentFromGlossary(glossary)
            }
            else -> {
                readJsonValue(reader)
                SenseContentData()
            }
        }
    }

    private fun readScoreValue(reader: JsonReader): Int {
        return when (reader.peek()) {
            JsonToken.NUMBER -> {
                val raw = reader.nextString()
                raw.toIntOrNull() ?: raw.toDoubleOrNull()?.toInt() ?: 0
            }
            JsonToken.STRING -> reader.nextString().toIntOrNull() ?: 0
            else -> {
                reader.skipValue()
                0
            }
        }
    }

    private fun skipOneValue(reader: JsonReader) {
        readJsonValue(reader)
    }

    private fun readJsonValue(reader: JsonReader, depth: Int = 0): Any? {
        if (depth > MAX_JSON_DEPTH) {
            error("JSON nesting exceeds maximum depth ($MAX_JSON_DEPTH)")
        }
        return when (reader.peek()) {
            JsonToken.NULL -> {
                reader.nextNull()
                JSONObject.NULL
            }
            JsonToken.BOOLEAN -> reader.nextBoolean()
            JsonToken.NUMBER -> {
                val raw = reader.nextString()
                raw.toIntOrNull() ?: raw.toLongOrNull() ?: raw.toDoubleOrNull() ?: raw
            }
            JsonToken.STRING -> reader.nextString()
            JsonToken.BEGIN_ARRAY -> {
                val array = JSONArray()
                reader.beginArray()
                while (reader.hasNext()) {
                    array.put(readJsonValue(reader, depth + 1))
                }
                reader.endArray()
                array
            }
            JsonToken.BEGIN_OBJECT -> {
                val obj = JSONObject()
                reader.beginObject()
                while (reader.hasNext()) {
                    obj.put(reader.nextName(), readJsonValue(reader, depth + 1))
                }
                reader.endObject()
                obj
            }
            else -> {
                reader.skipValue()
                null
            }
        }
    }

    private fun extractSenseContentFromGlossary(glossary: JSONArray): SenseContentData {
        var result = SenseContentData()
        for (index in 0 until glossary.length()) {
            result = result.merge(
                when (val item = glossary.get(index)) {
                    is String -> {
                        if (isJunkDefinition(item)) {
                            SenseContentData()
                        } else {
                            SenseContentData(
                                senseBlocks = listOf(
                                    SenseBlock(definitions = listOf(item)),
                                ),
                            )
                        }
                    }
                    is JSONObject -> extractSenseContentFromObject(item)
                    else -> SenseContentData()
                },
            )
        }
        return result
    }

    private fun extractSenseContentFromObject(item: JSONObject): SenseContentData {
        return when (item.optString("type")) {
            "text" -> {
                val text = item.optString("text").trim()
                if (text.isBlank() || isJunkDefinition(text)) {
                    SenseContentData()
                } else {
                    SenseContentData(
                        senseBlocks = listOf(
                            SenseBlock(definitions = listOf(text)),
                        ),
                    )
                }
            }
            "structured-content" -> StructuredContentPlainText.extractSenseContent(item.opt("content"))
            else -> {
                if (item.has("tag")) {
                    StructuredContentPlainText.extractSenseContent(item)
                } else {
                    SenseContentData()
                }
            }
        }
    }

    private class ZipImportLimits {
        private var totalUncompressedBytes = 0L

        fun entryInputStream(delegate: InputStream): InputStream =
            LimitedInputStream(delegate, MAX_ZIP_ENTRY_BYTES) { bytesRead ->
                totalUncompressedBytes += bytesRead
                if (totalUncompressedBytes > MAX_TOTAL_UNCOMPRESSED_BYTES) {
                    error(
                        "Total uncompressed size exceeds maximum " +
                            "($MAX_TOTAL_UNCOMPRESSED_BYTES bytes)",
                    )
                }
            }
    }

    companion object {
        private const val BATCH_SIZE = 2000
        private const val INDEX_JSON = "index.json"
        private const val TERM_BANK_PREFIX = "term_bank"
        const val MAX_ZIP_ENTRY_BYTES = 10L * 1024 * 1024
        const val MAX_INDEX_JSON_BYTES = 64 * 1024
        const val MAX_JSON_DEPTH = 32
        const val MAX_TOTAL_UNCOMPRESSED_BYTES = 150L * 1024 * 1024
    }
}

private class LimitedInputStream(
    private val delegate: InputStream,
    private val maxBytes: Long,
    private val onBytesRead: (Int) -> Unit,
) : InputStream() {
    private var entryBytesRead = 0L

    override fun read(): Int {
        if (entryBytesRead >= maxBytes) {
            error("ZIP entry exceeds maximum size ($maxBytes bytes)")
        }
        val value = delegate.read()
        if (value >= 0) {
            entryBytesRead++
            onBytesRead(1)
        }
        return value
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (entryBytesRead >= maxBytes) {
            error("ZIP entry exceeds maximum size ($maxBytes bytes)")
        }
        val cappedLen = minOf(len.toLong(), maxBytes - entryBytesRead).toInt()
        if (cappedLen <= 0) {
            error("ZIP entry exceeds maximum size ($maxBytes bytes)")
        }
        val read = delegate.read(b, off, cappedLen)
        if (read > 0) {
            entryBytesRead += read
            onBytesRead(read)
        }
        return read
    }

    override fun available(): Int = delegate.available()

    override fun skip(n: Long): Long = delegate.skip(n)

    override fun close() {
        delegate.close()
    }
}
