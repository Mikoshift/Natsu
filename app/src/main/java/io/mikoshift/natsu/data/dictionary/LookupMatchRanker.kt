package io.mikoshift.natsu.data.dictionary

fun termMatchPriority(
    expression: String,
    reading: String,
    surface: String,
    lemma: String,
    queryReading: String,
): Int {
    val normalizedQueryReading = queryReading.takeIf { it.isNotBlank() }?.let(::toHiragana).orEmpty()
    val normalizedTermReading = toHiragana(reading)

    return when {
        expression == surface -> 0
        lemma != "*" && expression == lemma -> 1
        reading == surface || (lemma != "*" && reading == lemma) -> 2
        normalizedQueryReading.isNotBlank() && normalizedTermReading == normalizedQueryReading -> 3
        else -> 4
    }
}

fun dedupeTermKey(dictionaryId: String, expression: String, reading: String): String =
    "$dictionaryId:$expression:${toHiragana(reading)}"
