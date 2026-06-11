package io.mikoshift.natsu.data.dictionary

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.util.zip.ZipInputStream

data class YomitanDictionaryIndex(
    val title: String,
    val revision: String,
)

class YomitanTermBankImporter {
    fun importZip(zipFile: File, catalogId: String): ImportResult {
        val tempDir = createTempDirectory(zipFile)
        try {
            unzip(zipFile, tempDir)
            val index = readIndex(tempDir)
            val terms = parseTermBanks(tempDir, catalogId)
            return ImportResult(
                catalogId = catalogId,
                index = index,
                terms = terms,
            )
        } finally {
            tempDir.deleteRecursively()
        }
    }

    private fun createTempDirectory(zipFile: File): File {
        val tempDir = File(zipFile.parentFile, "import_${zipFile.nameWithoutExtension}_${System.currentTimeMillis()}")
        tempDir.mkdirs()
        return tempDir
    }

    private fun unzip(zipFile: File, destination: File) {
        ZipInputStream(FileInputStream(zipFile)).use { zipInput ->
            while (true) {
                val entry = zipInput.nextEntry ?: break
                if (entry.isDirectory) {
                    zipInput.closeEntry()
                    continue
                }
                val outputFile = File(destination, entry.name.substringAfterLast('/'))
                outputFile.parentFile?.mkdirs()
                outputFile.outputStream().use { output ->
                    zipInput.copyTo(output)
                }
                zipInput.closeEntry()
            }
        }
    }

    private fun readIndex(tempDir: File): YomitanDictionaryIndex {
        val indexFile = tempDir.walkTopDown().first { it.name == "index.json" }
        val json = JSONObject(indexFile.readText())
        return YomitanDictionaryIndex(
            title = json.getString("title"),
            revision = json.optString("revision", "1"),
        )
    }

    private fun parseTermBanks(tempDir: File, catalogId: String): List<TermRecord> {
        val termFiles = tempDir.walkTopDown()
            .filter { it.isFile && it.name.startsWith("term_bank") && it.extension == "json" }
            .toList()
        val terms = mutableListOf<TermRecord>()
        termFiles.forEach { file ->
            val entries = JSONArray(file.readText())
            for (index in 0 until entries.length()) {
                val entry = entries.getJSONArray(index)
                if (entry.length() < 6) continue
                val expression = entry.optString(0)
                val reading = entry.optString(1)
                val score = entry.optInt(4, 0)
                val glossary = entry.opt(5)
                val glosses = when (glossary) {
                    is JSONArray -> extractGlosses(glossary)
                    else -> extractLegacyGlosses(entry)
                }
                if (expression.isBlank() || glosses.isEmpty()) continue
                terms.add(
                    TermRecord(
                        dictionaryId = catalogId,
                        expression = expression,
                        reading = reading.ifBlank { expression },
                        glossesJson = glossesToJson(glosses),
                        score = score,
                    ),
                )
            }
        }
        return terms
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
}

data class ImportResult(
    val catalogId: String,
    val index: YomitanDictionaryIndex,
    val terms: List<TermRecord>,
)

private val File.nameWithoutExtension: String
    get() = name.substringBeforeLast('.')

private val File.extension: String
    get() = name.substringAfterLast('.', "")
