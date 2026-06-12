package io.mikoshift.natsu.data.reader

import io.mikoshift.natsu.domain.model.reading.ReadingLocator
import io.mikoshift.natsu.domain.model.reading.SearchIndex
import io.mikoshift.natsu.domain.model.reading.SearchMatch

fun SearchIndex.findMatches(query: String): List<SearchMatch> {
    if (query.isEmpty()) return emptyList()

    val matches = mutableListOf<SearchMatch>()
    paragraphs.forEach { paragraph ->
        var startIndex = 0
        while (true) {
            val matchStart = paragraph.text.indexOf(query, startIndex, ignoreCase = false)
            if (matchStart < 0) break
            matches.add(
                SearchMatch(
                    locator = ReadingLocator(
                        sectionId = paragraph.sectionId,
                        blockIndex = paragraph.blockIndex,
                        charOffset = matchStart,
                    ),
                    globalCharOffset = paragraph.globalCharOffset + matchStart,
                    localRange = matchStart until (matchStart + query.length),
                ),
            )
            startIndex = matchStart + 1
        }
    }
    return matches
}

fun SearchIndex.globalCharOffsetForLocator(locator: ReadingLocator): Int? {
    val paragraph = paragraphs.firstOrNull {
        it.sectionId == locator.sectionId && it.blockIndex == locator.blockIndex
    } ?: return null
    if (locator.charOffset < 0 || locator.charOffset > paragraph.text.length) return null
    return paragraph.globalCharOffset + locator.charOffset
}

fun SearchIndex.sectionIdForGlobalCharOffset(globalCharOffset: Int): String? {
    if (globalCharOffset <= 0) return sectionOffsets.firstOrNull()?.sectionId
    return sectionOffsets.lastOrNull { it.globalCharOffset <= globalCharOffset }?.sectionId
        ?: sectionOffsets.firstOrNull()?.sectionId
}

fun SearchIndex.layoutParagraphIndexForGlobalOffset(globalCharOffset: Int): Int {
    if (paragraphs.isEmpty()) return 0
    if (globalCharOffset <= 0) return 0
    val index = paragraphs.indexOfLast { it.globalCharOffset <= globalCharOffset }
    return if (index >= 0) index else 0
}

fun SearchIndex.layoutParagraphIndexForLocator(locator: ReadingLocator): Int {
    return paragraphs.indexOfFirst {
        it.sectionId == locator.sectionId && it.blockIndex == locator.blockIndex
    }.coerceAtLeast(0)
}

fun SearchIndex.layoutParagraphIndexForMatch(match: SearchMatch): Int =
    layoutParagraphIndexForLocator(match.locator)

fun SearchIndex.layoutParagraphStart(sectionId: String): Int {
    return paragraphs.indexOfFirst { it.sectionId == sectionId }.coerceAtLeast(0)
}
