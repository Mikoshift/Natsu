package io.mikoshift.natsu.domain.model.reading

/**
 * Flattened reading surface derived from [ReadingBook] for Compose rendering, search, and progress.
 *
 * Every [FormatReadingLoader] must produce a [ReadingBook] whose layout satisfies these invariants:
 *
 * **Structure**
 * - [paragraphs].size == [paragraphStartOffsets].size.
 * - When empty: [paragraphs], [paragraphStartOffsets], and [sectionBoundaries] are empty and
 *   [canonicalText] is `""`.
 *
 * **canonicalText**
 * - [paragraphs] joined with a single `\n` between entries (no trailing newline).
 * - Single coordinate space for text search match offsets and persisted `charOffset` progress.
 *
 * **paragraphStartOffsets**
 * - First offset is `0`.
 * - Strictly monotonically increasing.
 * - [paragraphStartOffsets][i] is the start index of [paragraphs][i] inside [canonicalText].
 * - [paragraphs][i] equals the substring from that offset up to the next `\n` or end of text.
 *
 * **sectionBoundaries**
 * - Paragraph indices (not character offsets) marking the first paragraph of each section after the first.
 * - Strictly monotonically increasing; each value is in `1..paragraphs.lastIndex`.
 *
 * **Search mapping**
 * - A match offset in [canonicalText] maps to a paragraph via `paragraphIndexForCharOffset`.
 * - Local highlight range = `matchOffset - paragraphStartOffsets[paragraphIndex]` .. `+ queryLength`.
 */
data class ReadingLayout(
    val paragraphs: List<String>,
    val canonicalText: String,
    val paragraphStartOffsets: List<Int>,
    val sectionBoundaries: List<Int>,
)
