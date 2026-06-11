package io.mikoshift.natsu.data.reader

fun findMatchOffsets(text: String, query: String): List<Int> {
    if (query.isEmpty()) return emptyList()
    val matches = mutableListOf<Int>()
    var start = 0
    while (start <= text.length - query.length) {
        val index = text.indexOf(query, start)
        if (index < 0) break
        matches.add(index)
        start = index + 1
    }
    return matches
}

fun ParagraphLayout.paragraphIndexForMatch(charOffset: Int): Int =
    paragraphIndexForCharOffset(charOffset)

fun localHighlightRange(
    matchOffset: Int,
    queryLength: Int,
    paragraphStartOffset: Int,
): IntRange {
    val localStart = matchOffset - paragraphStartOffset
    return localStart until (localStart + queryLength)
}
