package io.mikoshift.natsu.data.dictionary

import io.mikoshift.natsu.domain.model.SenseBlock
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.zip.ZipInputStream

data class DictionaryArchiveIndex(
    val title: String,
    val revision: String,
)

class TermBankImporter {
    suspend fun importZip(
        zipFile: File,
        catalogId: String,
        onBatch: suspend (List<TermRecord>) -> Unit,
    ): DictionaryArchiveIndex {
        var index: DictionaryArchiveIndex? = null
        ZipInputStream(FileInputStream(zipFile)).use { zipInput ->
            while (true) {
                val entry = zipInput.nextEntry ?: break
                if (entry.isDirectory) {
                    zipInput.closeEntry()
                    continue
                }
                val entryName = entry.name.substringAfterLast('/')
                when {
                    entryName == "index.json" -> {
                        index = readIndex(zipInput)
                    }
                    entryName.startsWith("term_bank") && entryName.endsWith(".json") -> {
                        parseTermBankStream(zipInput, catalogId, onBatch)
                    }
                    else -> Unit
                }
                zipInput.closeEntry()
            }
        }
        return index ?: error("index.json not found in dictionary archive")
    }

    private fun readIndex(zipInput: ZipInputStream): DictionaryArchiveIndex {
        val json = JSONObject(String(zipInput.readBytes(), StandardCharsets.UTF_8))
        return DictionaryArchiveIndex(
            title = json.getString("title"),
            revision = json.optString("revision", "1"),
        )
    }

    private suspend fun parseTermBankStream(
        zipInput: ZipInputStream,
        catalogId: String,
        onBatch: suspend (List<TermRecord>) -> Unit,
    ) {
        val batch = ArrayList<TermRecord>(BATCH_SIZE)
        val reader = JsonReader(
            InputStreamReader(NonClosingInputStream(zipInput), StandardCharsets.UTF_8),
        )
        reader.use {
            it.beginArray()
            while (it.hasNext()) {
                parseTermEntry(it, catalogId)?.let { term -> batch.add(term) }
                if (batch.size >= BATCH_SIZE) {
                    onBatch(batch.toList())
                    batch.clear()
                }
            }
            it.endArray()
        }
        if (batch.isNotEmpty()) {
            onBatch(batch.toList())
        }
    }

    private fun parseTermEntry(reader: JsonReader, catalogId: String): TermRecord? {
        reader.beginArray()
        val entry = JSONArray()
        while (reader.hasNext()) {
            entry.put(readJsonValue(reader))
        }
        reader.endArray()

        if (entry.length() < 6) return null
        val expression = entry.optString(0)
        val reading = entry.optString(1)
        val score = entry.optInt(4, 0)
        val glossary = entry.opt(5)
        val senseContent = sanitizeSenseContent(
            when (glossary) {
                is JSONArray -> extractSenseContentFromGlossary(glossary)
                is String -> SenseContentData(
                    senseBlocks = listOf(
                        SenseBlock(definitions = listOf(glossary)),
                    ),
                )
                else -> extractLegacySenseContent(entry)
            },
        )
        if (expression.isBlank() || !senseContent.hasContent()) return null
        return TermRecord(
            dictionaryId = catalogId,
            expression = expression,
            reading = reading.ifBlank { expression },
            glossesJson = encodeSenseContent(senseContent),
            score = score,
        )
    }

    private fun readJsonValue(reader: JsonReader): Any? {
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
                    array.put(readJsonValue(reader))
                }
                reader.endArray()
                array
            }
            JsonToken.BEGIN_OBJECT -> {
                val obj = JSONObject()
                reader.beginObject()
                while (reader.hasNext()) {
                    obj.put(reader.nextName(), readJsonValue(reader))
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

    private fun extractLegacySenseContent(entry: JSONArray): SenseContentData {
        if (entry.length() <= 5) return SenseContentData()
        val definitions = buildList {
            for (index in 5 until entry.length()) {
                val item = entry.opt(index)
                if (item is String && item.isNotBlank() && !isJunkDefinition(item)) {
                    add(item)
                }
            }
        }
        return if (definitions.isEmpty()) {
            SenseContentData()
        } else {
            SenseContentData(
                senseBlocks = listOf(
                    SenseBlock(definitions = definitions),
                ),
            )
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

    companion object {
        private const val BATCH_SIZE = 500
    }
}

/**
 * Prevents nested readers (JsonReader, BufferedReader) from closing [ZipInputStream]
 * when they are closed at the end of an entry.
 */
private class NonClosingInputStream(
    private val delegate: InputStream,
) : InputStream() {
    override fun read(): Int = delegate.read()

    override fun read(b: ByteArray, off: Int, len: Int): Int = delegate.read(b, off, len)

    override fun available(): Int = delegate.available()

    override fun skip(n: Long): Long = delegate.skip(n)

    override fun close() = Unit
}
