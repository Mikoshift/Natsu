package io.mikoshift.natsu.data.dictionary

import io.mikoshift.natsu.domain.model.SenseBlock
import org.json.JSONArray
import org.json.JSONObject

data class SenseContentData(
    val partsOfSpeech: List<String> = emptyList(),
    val senseBlocks: List<SenseBlock> = emptyList(),
) {
    fun hasContent(): Boolean = senseBlocks.any { it.definitions.isNotEmpty() }

    fun merge(other: SenseContentData): SenseContentData = SenseContentData(
        partsOfSpeech = (partsOfSpeech + other.partsOfSpeech).distinct(),
        senseBlocks = senseBlocks + other.senseBlocks,
    )
}

object StructuredContentPlainText {

    fun extractSenseContent(content: Any?): SenseContentData {
        return when (content) {
            null, JSONObject.NULL -> SenseContentData()
            is String -> SenseContentData(
                senseBlocks = listOf(SenseBlock(definitions = listOf(content.trim()))),
            )
            is JSONArray -> {
                var result = SenseContentData()
                for (index in 0 until content.length()) {
                    result = result.merge(extractSenseContent(content.get(index)))
                }
                result
            }
            is JSONObject -> extractFromObject(content)
            else -> SenseContentData()
        }
    }

    private fun extractFromObject(obj: JSONObject): SenseContentData {
        when (obj.optString("type")) {
            "text" -> {
                val text = obj.optString("text").trim()
                return if (text.isBlank()) {
                    SenseContentData()
                } else {
                    SenseContentData(senseBlocks = listOf(SenseBlock(definitions = listOf(text))))
                }
            }
            "structured-content" -> return extractSenseContent(obj.opt("content"))
        }

        val dataContent = obj.optJSONObject("data")?.optString("content").orEmpty()
        when (dataContent) {
            "attribution", "attribution-footnote", "forms", "forms-label" -> return SenseContentData()
            "sense-groups" -> return extractSenseContent(obj.opt("content"))
            "sense-group" -> return extractSenseGroup(obj)
        }

        when (obj.optString("tag")) {
            "div" -> if (dataContent == "sense-group") return extractSenseGroup(obj)
            "ul" -> if (dataContent == "sense-groups") return extractSenseContent(obj.opt("content"))
        }

        return extractSenseContent(obj.opt("content"))
    }

    private fun extractSenseGroup(groupObj: JSONObject): SenseContentData {
        val partsOfSpeech = mutableListOf<String>()
        val senseBlocks = mutableListOf<SenseBlock>()

        fun walk(node: Any?) {
            when (node) {
                is JSONArray -> {
                    for (index in 0 until node.length()) {
                        walk(node.get(index))
                    }
                }
                is JSONObject -> {
                    val dataContent = node.optJSONObject("data")?.optString("content").orEmpty()
                    when (dataContent) {
                        "part-of-speech-info" -> {
                            joinTextContent(node.opt("content"))?.let(partsOfSpeech::add)
                            return
                        }
                        "sense" -> {
                            senseBlocks += extractSense(node)
                            return
                        }
                    }
                    if (node.has("tag")) {
                        walk(node.opt("content"))
                    }
                }
            }
        }

        walk(groupObj.opt("content"))
        return SenseContentData(
            partsOfSpeech = partsOfSpeech.distinct(),
            senseBlocks = senseBlocks,
        )
    }

    private fun extractSense(senseObj: JSONObject): SenseBlock {
        var definitions = emptyList<String>()
        var exampleJapanese: String? = null
        var exampleEnglish: String? = null

        fun walk(node: Any?) {
            when (node) {
                is JSONArray -> {
                    for (index in 0 until node.length()) {
                        walk(node.get(index))
                    }
                }
                is JSONObject -> {
                    when (node.optJSONObject("data")?.optString("content").orEmpty()) {
                        "glossary" -> {
                            definitions = extractGlossaryItems(node.opt("content"))
                            return
                        }
                        "example-sentence-a" -> {
                            exampleJapanese = joinTextContent(node.opt("content"))
                            return
                        }
                        "example-sentence-b" -> {
                            exampleEnglish = extractExampleTranslation(node.opt("content"))
                            return
                        }
                        "extra-info", "example-sentence" -> {
                            walk(node.opt("content"))
                            return
                        }
                    }
                    if (node.has("tag")) {
                        walk(node.opt("content"))
                    }
                }
            }
        }

        walk(senseObj.opt("content"))
        return SenseBlock(
            definitions = definitions,
            exampleJapanese = exampleJapanese?.takeIf { it.isNotBlank() },
            exampleEnglish = exampleEnglish?.takeIf { it.isNotBlank() },
        )
    }

    private fun extractGlossaryItems(content: Any?): List<String> {
        val items = mutableListOf<String>()
        fun walk(node: Any?) {
            when (node) {
                is JSONArray -> {
                    for (index in 0 until node.length()) {
                        walk(node.get(index))
                    }
                }
                is JSONObject -> {
                    if (node.optString("tag") == "li") {
                        joinTextContent(node.opt("content"))?.let(items::add)
                    } else if (node.has("tag")) {
                        walk(node.opt("content"))
                    }
                }
            }
        }
        walk(content)
        return items.filter { it.isNotBlank() }
    }

    private fun extractExampleTranslation(content: Any?): String? {
        when (content) {
            is JSONArray -> {
                for (index in 0 until content.length()) {
                    when (val item = content.get(index)) {
                        is JSONObject -> {
                            if (item.optJSONObject("data")?.optString("content") == "attribution-footnote") {
                                continue
                            }
                            if (item.optString("lang") == "en") {
                                return joinTextContent(item.opt("content"))
                            }
                        }
                    }
                }
            }
            is JSONObject -> {
                if (content.optJSONObject("data")?.optString("content") == "attribution-footnote") {
                    return null
                }
                if (content.optString("lang") == "en") {
                    return joinTextContent(content.opt("content"))
                }
            }
        }
        return joinTextContent(content)
    }

    private fun joinTextContent(content: Any?): String? {
        return when (content) {
            null, JSONObject.NULL -> null
            is String -> content
            is JSONArray -> buildString {
                for (index in 0 until content.length()) {
                    joinTextContent(content.get(index))?.let(::append)
                }
            }.takeIf { it.isNotBlank() }
            is JSONObject -> joinTextContent(content.opt("content"))
            else -> null
        }
    }
}

fun encodeSenseContent(data: SenseContentData): String {
    val obj = JSONObject()
    obj.put("v", 2)
    obj.put("pos", JSONArray(data.partsOfSpeech))
    val senses = JSONArray()
    data.senseBlocks.forEach { block ->
        val sense = JSONObject()
        sense.put("defs", JSONArray(block.definitions))
        block.exampleJapanese?.let { sense.put("exJa", it) }
        block.exampleEnglish?.let { sense.put("exEn", it) }
        senses.put(sense)
    }
    obj.put("senses", senses)
    return obj.toString()
}

fun parseSenseContentJson(json: String): SenseContentData {
    val trimmed = json.trim()
    if (trimmed.startsWith("{")) {
        runCatching {
            val obj = JSONObject(trimmed)
            if (obj.optInt("v") == 2) {
                return decodeSenseContentV2(obj)
            }
        }
    }
    return parseLegacyGlossesArray(trimmed)
}

private fun decodeSenseContentV2(obj: JSONObject): SenseContentData {
    val pos = buildList {
        val array = obj.optJSONArray("pos") ?: JSONArray()
        for (index in 0 until array.length()) {
            add(array.getString(index))
        }
    }
    val blocks = buildList {
        val array = obj.optJSONArray("senses") ?: JSONArray()
        for (index in 0 until array.length()) {
            val sense = array.getJSONObject(index)
            val defs = buildList {
                val defsArray = sense.optJSONArray("defs") ?: JSONArray()
                for (defIndex in 0 until defsArray.length()) {
                    add(defsArray.getString(defIndex))
                }
            }
            if (defs.isNotEmpty()) {
                add(
                    SenseBlock(
                        definitions = defs,
                        exampleJapanese = sense.optString("exJa").takeIf { it.isNotBlank() },
                        exampleEnglish = sense.optString("exEn").takeIf { it.isNotBlank() },
                    ),
                )
            }
        }
    }
    return SenseContentData(partsOfSpeech = pos, senseBlocks = blocks)
}

private fun parseLegacyGlossesArray(json: String): SenseContentData {
    val array = JSONArray(json)
    for (index in 0 until array.length()) {
        val raw = array.optString(index).trim()
        if (raw.startsWith("{")) {
            runCatching {
                val extracted = StructuredContentPlainText.extractSenseContent(JSONObject(raw))
                if (extracted.hasContent()) return extracted
            }
        }
    }

    val lines = buildList {
        for (index in 0 until array.length()) {
            addAll(parseLegacyGlossLine(array.optString(index)))
        }
    }.filterNot(::isJunkLine)

    if (lines.isEmpty()) return SenseContentData()

    val (pos, remainder) = splitLegacyPosAndDefinitions(lines)
    return SenseContentData(
        partsOfSpeech = pos,
        senseBlocks = buildLegacySenseBlocks(remainder),
    )
}

private fun buildLegacySenseBlocks(lines: List<String>): List<SenseBlock> {
    if (lines.isEmpty()) return emptyList()

    val blocks = mutableListOf<SenseBlock>()
    var currentDefinitions = mutableListOf<String>()
    var index = 0

    while (index < lines.size) {
        if (isLegacyJapaneseFragment(lines[index])) {
            val japaneseParts = mutableListOf<String>()
            while (index < lines.size && isLegacyJapaneseFragment(lines[index])) {
                japaneseParts += lines[index]
                index++
            }
            val exampleJapanese = japaneseParts.joinToString("")
            val exampleEnglish = if (index < lines.size && isLegacyEnglishSentence(lines[index])) {
                lines[index++]
            } else {
                null
            }
            blocks += SenseBlock(
                definitions = currentDefinitions.toList(),
                exampleJapanese = exampleJapanese,
                exampleEnglish = exampleEnglish,
            )
            currentDefinitions = mutableListOf()
        } else {
            currentDefinitions += lines[index++]
        }
    }

    if (currentDefinitions.isNotEmpty()) {
        blocks += SenseBlock(definitions = currentDefinitions)
    }

    return blocks.ifEmpty { listOf(SenseBlock(definitions = lines)) }
}

private fun isLegacyJapaneseFragment(line: String): Boolean {
    if (line.isBlank()) return false
    if (line.any { it.isLetter() && it.code < 128 } && line.contains(' ')) return false
    return line.any { char ->
        char in '\u3040'..'\u309F' || char in '\u30A0'..'\u30FF' || char in '\u4E00'..'\u9FFF'
    }
}

private fun isLegacyEnglishSentence(line: String): Boolean {
    if (line.isBlank()) return false
    val latinLetters = line.count { it.isLetter() && it.code < 128 }
    return latinLetters > 0 && (line.endsWith('.') || line.count { it == ' ' } >= 3)
}

private fun parseLegacyGlossLine(raw: String): List<String> {
    val trimmed = raw.trim()
    if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
        runCatching {
            val extracted = when {
                trimmed.startsWith("{") -> StructuredContentPlainText.extractSenseContent(JSONObject(trimmed))
                else -> StructuredContentPlainText.extractSenseContent(JSONArray(trimmed))
            }
            if (extracted.hasContent()) {
                return extracted.senseBlocks.flatMap { it.definitions } + extracted.partsOfSpeech
            }
        }
    }
    return listOfNotNull(trimmed.takeIf { it.isNotBlank() })
}

private fun isJunkLine(line: String): Boolean {
    val text = line.trim()
    if (text.isBlank()) return true
    if (text.matches(Regex("\\[\\d+\\]"))) return true
    if (text.equals("JMdict", ignoreCase = true)) return true
    if (text.contains("Tatoeba", ignoreCase = true)) return true
    if (text == "|") return true
    if (text.equals("forms", ignoreCase = true)) return true
    return false
}

private val legacyPosTokens = setOf(
    "noun", "suru", "transitive", "intransitive", "na-adj", "adj-i", "adj-na",
    "adverb", "verb", "interjection", "prefix", "suffix", "counter", "expression",
    "pronoun", "conjunction", "auxiliary", "numeral", "particle", "copula",
)

private fun splitLegacyPosAndDefinitions(lines: List<String>): Pair<List<String>, List<String>> {
    val pos = mutableListOf<String>()
    val definitions = mutableListOf<String>()
    var seenDefinition = false

    lines.forEach { line ->
        val token = line.trim()
        if (!seenDefinition && token.lowercase() in legacyPosTokens) {
            pos += token
        } else {
            seenDefinition = true
            if (!isJunkLine(token)) {
                definitions += token
            }
        }
    }

    return pos.distinct() to definitions
}
