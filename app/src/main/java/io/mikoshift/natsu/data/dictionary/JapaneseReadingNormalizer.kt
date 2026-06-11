package io.mikoshift.natsu.data.dictionary

fun toHiragana(text: String): String =
    text.map { char -> char.toHiraganaChar() }.joinToString("")

fun toKatakana(text: String): String =
    text.map { char -> char.toKatakanaChar() }.joinToString("")

fun containsKana(text: String): Boolean =
    text.any { it.isHiragana() || it.isKatakana() }

private fun Char.toHiraganaChar(): Char = when {
    this == 'ヴ' -> 'ゔ'
    this in KATAKANA_START..KATAKANA_END -> (code - KATAKANA_TO_HIRAGANA_OFFSET).toChar()
    else -> this
}

private fun Char.toKatakanaChar(): Char = when {
    this == 'ゔ' -> 'ヴ'
    this in HIRAGANA_START..HIRAGANA_END -> (code + KATAKANA_TO_HIRAGANA_OFFSET).toChar()
    else -> this
}

private fun Char.isHiragana(): Boolean =
    this == 'ゔ' || this in HIRAGANA_START..HIRAGANA_END

private fun Char.isKatakana(): Boolean =
    this == 'ヴ' || this in KATAKANA_START..KATAKANA_END

private const val HIRAGANA_START = '\u3041'
private const val HIRAGANA_END = '\u3096'
private const val KATAKANA_START = '\u30A1'
private const val KATAKANA_END = '\u30F6'
private const val KATAKANA_TO_HIRAGANA_OFFSET = 0x60
