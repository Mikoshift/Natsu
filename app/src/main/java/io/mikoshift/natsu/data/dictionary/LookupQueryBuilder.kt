package io.mikoshift.natsu.data.dictionary

fun buildLookupQueries(surface: String, lemma: String, reading: String): List<String> {
    val queries = linkedSetOf<String>()

    addBaseQuery(queries, surface)
    if (lemma != surface) {
        addBaseQuery(queries, lemma)
    }
    addReadingQueries(queries, reading)
    addKanaVariants(queries, surface)
    if (lemma != surface) {
        addKanaVariants(queries, lemma)
    }

    return queries.filter { it.isNotBlank() && it != "*" }.toList()
}

private fun addBaseQuery(queries: MutableSet<String>, value: String) {
    if (value.isNotBlank()) {
        queries.add(value)
    }
}

private fun addReadingQueries(queries: MutableSet<String>, reading: String) {
    if (reading.isBlank()) return

    queries.add(reading)
    val hiragana = toHiragana(reading)
    if (hiragana != reading) {
        queries.add(hiragana)
    }
    val katakana = toKatakana(reading)
    if (katakana != reading) {
        queries.add(katakana)
    }
}

private fun addKanaVariants(queries: MutableSet<String>, value: String) {
    if (!containsKana(value)) return

    val hiragana = toHiragana(value)
    if (hiragana != value) {
        queries.add(hiragana)
    }
    val katakana = toKatakana(value)
    if (katakana != value) {
        queries.add(katakana)
    }
}
