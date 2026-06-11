package io.mikoshift.natsu.domain.model

data class TextToken(
    val surface: String,
    val reading: String,
    val lemma: String,
    val partOfSpeech: String,
    val isClickable: Boolean,
)
