package io.mikoshift.natsu.domain.repository

import io.mikoshift.natsu.domain.model.TextToken
import io.mikoshift.natsu.domain.model.reading.TextSpan

interface TextTokenizer {
    fun tokenize(text: String): List<TextToken>

    fun tokenizeParagraph(spans: List<TextSpan>): List<TextToken> =
        tokenize(spans.joinToString(separator = "") { it.text })

    fun tokenizeParagraphs(paragraphs: List<String>): List<List<TextToken>>
}
