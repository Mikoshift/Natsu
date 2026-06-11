package io.mikoshift.natsu.ui.reader

import io.mikoshift.natsu.data.dictionary.toHiragana
import io.mikoshift.natsu.domain.model.TextToken

fun shouldShowFurigana(token: TextToken): Boolean =
    token.reading.isNotBlank() &&
        toHiragana(token.reading) != toHiragana(token.surface)

fun furiganaReading(token: TextToken): String = toHiragana(token.reading)
