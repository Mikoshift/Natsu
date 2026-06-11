package io.mikoshift.natsu.domain.repository

import io.mikoshift.natsu.domain.model.TextToken

interface TextTokenizer {
    fun tokenize(text: String): List<TextToken>

    fun tokenizeParagraphs(paragraphs: List<String>): List<List<TextToken>>
}
