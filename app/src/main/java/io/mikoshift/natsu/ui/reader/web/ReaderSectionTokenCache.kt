package io.mikoshift.natsu.ui.reader.web

import io.mikoshift.natsu.domain.model.TextToken

internal class ReaderSectionTokenCache {
    private val bySection = linkedMapOf<String, MutableMap<String, List<TextToken>>>()

    fun warm(
        sectionId: String,
        paragraphs: List<String>,
        tokenize: (String) -> List<TextToken>,
    ) {
        val cache = linkedMapOf<String, List<TextToken>>()
        paragraphs.forEach { paragraph ->
            if (paragraph.isNotEmpty()) {
                cache[paragraph] = tokenize(paragraph)
            }
        }
        bySection[sectionId] = cache
    }

    fun tokens(
        sectionId: String,
        paragraphText: String,
        tokenize: (String) -> List<TextToken>,
    ): List<TextToken> {
        val section = bySection.getOrPut(sectionId) { linkedMapOf() }
        return section.getOrPut(paragraphText) { tokenize(paragraphText) }
    }

    fun clear() {
        bySection.clear()
    }

    fun removeSection(sectionId: String) {
        bySection.remove(sectionId)
    }
}
