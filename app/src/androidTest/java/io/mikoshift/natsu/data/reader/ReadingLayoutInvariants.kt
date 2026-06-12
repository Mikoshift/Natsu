package io.mikoshift.natsu.data.reader

import io.mikoshift.natsu.domain.model.reading.ReadingLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

fun assertReadingLayoutInvariants(layout: ReadingLayout) {
    assertEquals(
        "paragraphs and paragraphStartOffsets must have the same size",
        layout.paragraphs.size,
        layout.paragraphStartOffsets.size,
    )

    if (layout.paragraphs.isEmpty()) {
        assertTrue("canonicalText must be empty when there are no paragraphs", layout.canonicalText.isEmpty())
        assertTrue("paragraphStartOffsets must be empty when there are no paragraphs", layout.paragraphStartOffsets.isEmpty())
        assertTrue("sectionBoundaries must be empty when there are no paragraphs", layout.sectionBoundaries.isEmpty())
        return
    }

    assertEquals(
        "canonicalText must be paragraphs joined by a single newline",
        layout.paragraphs.joinToString(separator = "\n"),
        layout.canonicalText,
    )

    assertEquals("first paragraph must start at offset 0", 0, layout.paragraphStartOffsets.first())

    layout.paragraphStartOffsets.zipWithNext { current, next ->
        assertTrue(
            "paragraphStartOffsets must be strictly increasing (found $current then $next)",
            current < next,
        )
    }

    layout.paragraphs.forEachIndexed { index, paragraph ->
        val start = layout.paragraphStartOffsets[index]
        val end = if (index < layout.paragraphs.lastIndex) {
            layout.paragraphStartOffsets[index + 1] - 1
        } else {
            layout.canonicalText.length
        }
        assertEquals(
            "paragraph text must match canonicalText substring at offset $start",
            paragraph,
            layout.canonicalText.substring(start, end),
        )
    }

    layout.sectionBoundaries.zipWithNext { current, next ->
        assertTrue(
            "sectionBoundaries must be strictly increasing (found $current then $next)",
            current < next,
        )
    }
    layout.sectionBoundaries.forEach { boundary ->
        assertTrue(
            "section boundary $boundary must point to a paragraph after the first",
            boundary in 1..layout.paragraphs.lastIndex,
        )
    }
}

fun assertSearchOffsetsAreValid(layout: ReadingLayout, query: String) {
    if (query.isEmpty()) return

    val matches = findMatchOffsets(layout.canonicalText, query)
    matches.forEach { matchOffset ->
        assertTrue(
            "match offset $matchOffset must be within canonicalText",
            matchOffset in 0 until layout.canonicalText.length,
        )

        val paragraphIndex = layout.paragraphIndexForCharOffset(matchOffset)
        assertTrue(
            "paragraph index $paragraphIndex must be valid for match at $matchOffset",
            paragraphIndex in layout.paragraphs.indices,
        )

        val paragraphStart = layout.paragraphStartOffsets[paragraphIndex]
        val localRange = localHighlightRange(matchOffset, query.length, paragraphStart)
        val paragraphLength = layout.paragraphs[paragraphIndex].length
        assertTrue(
            "local highlight range $localRange must fit paragraph length $paragraphLength",
            localRange.first >= 0 && localRange.last <= paragraphLength,
        )
    }
}
