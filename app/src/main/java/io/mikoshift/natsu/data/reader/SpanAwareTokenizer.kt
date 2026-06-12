package io.mikoshift.natsu.data.reader

import io.mikoshift.natsu.domain.model.TextToken
import io.mikoshift.natsu.domain.model.reading.TextSpan

object SpanAwareTokenizer {
    fun tokenizeParagraph(
        spans: List<TextSpan>,
        tokenizeText: (String) -> List<TextToken>,
    ): List<TextToken> {
        if (spans.isEmpty()) return emptyList()
        return spans.flatMap { span ->
            if (span.reading != null) {
                listOf(
                    TextToken(
                        surface = span.text,
                        reading = span.reading,
                        lemma = span.text,
                        partOfSpeech = "名詞",
                        isClickable = span.text.isNotBlank(),
                    ),
                )
            } else {
                tokenizeText(span.text)
            }
        }
    }
}
