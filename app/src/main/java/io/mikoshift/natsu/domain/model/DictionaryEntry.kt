package io.mikoshift.natsu.domain.model

data class DictionarySense(
    val dictionaryTitle: String,
    val kanji: List<String>,
    val readings: List<String>,
    val glosses: List<String>,
)

data class DictionaryEntry(
    val querySurface: String,
    val queryLemma: String,
    val queryReading: String,
    val senses: List<DictionarySense>,
)
