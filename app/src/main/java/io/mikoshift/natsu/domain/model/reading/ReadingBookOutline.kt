package io.mikoshift.natsu.domain.model.reading

data class ReadingBookOutline(
    val id: String,
    val title: String,
    val manifest: BookManifest,
    val searchIndex: SearchIndex,
)

data class SectionReadingContent(
    val section: ReadingSection,
    val layout: SectionLayout,
)
