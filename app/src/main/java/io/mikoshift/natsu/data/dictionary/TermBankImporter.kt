package io.mikoshift.natsu.data.dictionary

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
                        zipInput.closeEntry()
                    }
                    entryName.startsWith("term_bank") && entryName.endsWith(".json") -> {
                        parseTermBankStream(zipInput, catalogId, onBatch)
                        zipInput.closeEntry()
                    }
                    else -> zipInput.closeEntry()
                }
            }
        }
        return index ?: error("index.json not found in dictionary archive")
    }

    private fun readIndex(input: InputStream): DictionaryArchiveIndex {
        val json = JSONObject(input.bufferedReader().readText())
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
        JsonReader(InputStreamReader(input, StandardCharsets.UTF_8)).use { reader ->
            reader.beginArray()
            while (reader.hasNext()) {
                parseTermEntry(reader, catalogId)?.let { batch.add(it) }
                if (batch.size >= BATCH_SIZE) {
                    onBatch(batch.toList())
                    batch.clear()
                }
            }
            reader.endArray()
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
        val glosses = when (glossary) {
            is JSONArray -> extractGlosses(glossary)
            else -> extractLegacyGlosses(entry)
        }
        if (expression.isBlank() || glosses.isEmpty()) return null
        return TermRecord(
            dictionaryId = catalogId,
            expression = expression,
            reading = reading.ifBlank { expression },
            glossesJson = glossesToJson(glosses),
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

    private fun extractLegacyGlosses(entry: JSONArray): List<String> {
        if (entry.length() <= 5) return emptyList()
        val glosses = mutableListOf<String>()
        for (index in 5 until entry.length()) {
            val item = entry.opt(index)
            if (item is String && item.isNotBlank()) {
                glosses.add(item)
            }
        }
        return glosses
    }

    private fun extractGlosses(glossary: Any?): List<String> {
        if (glossary == null) return emptyList()
        return when (glossary) {
            is JSONArray -> extractGlossesFromArray(glossary)
            is String -> listOf(glossary)
            else -> emptyList()
        }
    }

    private fun extractGlossesFromArray(array: JSONArray): List<String> {
        val glosses = mutableListOf<String>()
        for (index in 0 until array.length()) {
            when (val item = array.get(index)) {
                is String -> glosses.add(item)
                is JSONObject -> glosses.addAll(extractGlossFromObject(item))
            }
        }
        return glosses.filter { it.isNotBlank() }
    }

    private fun extractGlossFromObject(item: JSONObject): List<String> {
        return when (item.optString("type")) {
            "text" -> listOfNotNull(item.optString("text").takeIf { it.isNotBlank() })
            "structured-content" -> extractStructuredContent(item.opt("content"))
            else -> emptyList()
        }
    }

    private fun extractStructuredContent(content: Any?): List<String> {
        return when (content) {
            is String -> listOfNotNull(content.takeIf { it.isNotBlank() })
            is JSONObject -> {
                val text = content.optString("content").takeIf { it.isNotBlank() }
                    ?: content.optString("text").takeIf { it.isNotBlank() }
                listOfNotNull(text)
            }
            is JSONArray -> {
                buildList {
                    for (index in 0 until content.length()) {
                        addAll(extractStructuredContent(content.get(index)))
                    }
                }
            }
            else -> emptyList()
        }
    }

    companion object {
        private const val BATCH_SIZE = 500
    }
}
