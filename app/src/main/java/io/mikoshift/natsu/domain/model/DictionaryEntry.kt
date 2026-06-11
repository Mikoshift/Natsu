package io.mikoshift.natsu.domain.model

data class SenseBlock(
    val definitions: List<String>,
    val exampleJapanese: String? = null,
    val exampleEnglish: String? = null,
)

data class DictionarySense(
    val dictionaryTitle: String,
    val kanji: List<String>,
    val readings: List<String>,
    val partsOfSpeech: List<String>,
    val senseBlocks: List<SenseBlock>,
)

data class DictionaryEntry(
    val querySurface: String,
    val queryLemma: String,
    val queryReading: String,
    val senses: List<DictionarySense>,
)
