package io.mikoshift.natsu.domain.model.reading

data class ReadingBook(
    val id: String,
    val title: String,
    val sections: List<ReadingSection>,
)

data class ReadingSection(
    val id: String,
    val title: String?,
    val blocks: List<ReadingBlock>,
)
