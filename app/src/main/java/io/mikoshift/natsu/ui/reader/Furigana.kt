package io.mikoshift.natsu.ui.reader

import io.mikoshift.natsu.data.dictionary.toHiragana
import io.mikoshift.natsu.domain.model.TextToken

private val KANJI_REGEX = Regex("""[\u3400-\u4DBF\u4E00-\u9FFF\uF900-\uFAFF]""")

fun containsKanji(text: String): Boolean = KANJI_REGEX.containsMatchIn(text)

fun shouldShowFurigana(token: TextToken): Boolean =
    containsKanji(token.surface) &&
        token.reading.isNotBlank() &&
        toHiragana(token.reading) != toHiragana(token.surface)

fun furiganaReading(token: TextToken): String = toHiragana(token.reading)
