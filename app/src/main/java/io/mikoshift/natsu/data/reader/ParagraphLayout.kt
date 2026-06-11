package io.mikoshift.natsu.data.reader

data class ParagraphLayout(
    val paragraphs: List<String>,
    val startOffsets: List<Int>,
)

fun buildParagraphLayout(text: String): ParagraphLayout {
    if (text.isBlank()) return ParagraphLayout(emptyList(), emptyList())
    val paragraphs = mutableListOf<String>()
    val startOffsets = mutableListOf<Int>()
    var index = 0
    while (index < text.length) {
        while (index < text.length && text[index] == '\n') {
            index++
        }
        if (index >= text.length) break
        val start = index
        while (index < text.length && text[index] != '\n') {
            index++
        }
        val line = text.substring(start, index).trimEnd()
        if (line.isNotEmpty()) {
            paragraphs.add(line)
            startOffsets.add(start)
        }
        if (index < text.length) {
            index++
        }
    }
    return ParagraphLayout(paragraphs, startOffsets)
}

fun ParagraphLayout.paragraphIndexForCharOffset(charOffset: Int): Int {
    if (paragraphs.isEmpty()) return 0
    if (charOffset <= 0) return 0
    val index = startOffsets.indexOfLast { it <= charOffset }
    return if (index >= 0) index else 0
}

fun splitIntoParagraphs(text: String): List<String> = buildParagraphLayout(text).paragraphs
