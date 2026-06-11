package io.mikoshift.natsu.data.reader

import com.atilika.kuromoji.ipadic.Token
import com.atilika.kuromoji.ipadic.Tokenizer
import io.mikoshift.natsu.domain.model.TextToken
import io.mikoshift.natsu.domain.repository.TextTokenizer

class KuromojiTokenizer : TextTokenizer {
    private val tokenizer = Tokenizer.Builder().build()

    override fun tokenize(text: String): List<TextToken> {
        if (text.isEmpty()) return emptyList()
        return tokenizer.tokenize(text).map { it.toTextToken() }
    }

    override fun tokenizeParagraphs(paragraphs: List<String>): List<List<TextToken>> =
        paragraphs.map { tokenize(it) }

    private fun Token.toTextToken(): TextToken {
        val pos = partOfSpeechLevel1
        val clickable = pos != "記号" && pos != "空白"
        return TextToken(
            surface = surface,
            reading = reading,
            lemma = baseForm,
            partOfSpeech = partOfSpeechLevel1,
            isClickable = clickable,
        )
    }
}

fun splitIntoParagraphs(text: String): List<String> {
    if (text.isBlank()) return emptyList()
    return text.split("\n")
        .map { it.trimEnd() }
        .filter { it.isNotEmpty() }
}
