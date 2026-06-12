package io.mikoshift.natsu.ui.reader

import io.mikoshift.natsu.data.reader.layoutParagraphStart
import io.mikoshift.natsu.domain.model.reading.ReadingBookOutline

object ReaderDisplayBuilder {
    fun buildSectionNav(outline: ReadingBookOutline): List<ReaderSectionNav> {
        if (outline.manifest.sections.size <= 1) return emptyList()
        return outline.manifest.sections.map { section ->
            ReaderSectionNav(
                id = section.id,
                title = section.title?.takeIf { it.isNotBlank() } ?: section.id,
                startLayoutParagraphIndex = outline.searchIndex.layoutParagraphStart(section.id),
            )
        }
    }
}
