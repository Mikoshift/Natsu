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
import java.util.zip.ZipFile
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
        onImportProgress: (filesProcessed: Int, totalFiles: Int) -> Unit = { _, _ -> },
    ): DictionaryArchiveIndex {
        val totalTermBankFiles = countTermBankFiles(zipFile)
        var filesProcessed = 0
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
                        filesProcessed++
                        onImportProgress(filesProcessed, totalTermBankFiles)
                    }
                    else -> Unit
                }
                zipInput.closeEntry()
            }
        }
        return index ?: error("index.json not found in dictionary archive")
    }

    private fun countTermBankFiles(zipFile: File): Int =
        ZipFile(zipFile).use { zip ->
            zip.entries().asSequence().count { entry ->
                !entry.isDirectory &&
                    entry.name.substringAfterLast('/').let { name ->
                        name.startsWith("term_bank") && name.endsWith(".json")
                    }
            }.coerceAtLeast(1)
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
        private const val BATCH_SIZE = 2000
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
