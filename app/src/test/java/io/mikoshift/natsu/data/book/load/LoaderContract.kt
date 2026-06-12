package io.mikoshift.natsu.data.book.load

import io.mikoshift.natsu.data.reader.ReadingLayoutBuilder
import io.mikoshift.natsu.data.reader.assertReadingLayoutInvariants
import io.mikoshift.natsu.data.reader.assertSearchOffsetsAreValid
import io.mikoshift.natsu.domain.model.reading.ReadingBook
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

object LoaderContract {
    private val DEFAULT_SEARCH_QUERIES = listOf("the", "は", "Line")

    fun verify(
        book: ReadingBook,
        searchQueries: List<String> = DEFAULT_SEARCH_QUERIES,
    ) {
        assertReadingBookInvariants(book)

        val layout = ReadingLayoutBuilder().build(book)
        assertReadingLayoutInvariants(layout)

        val reconstructedLength = layout.paragraphs
            .joinToString(separator = "\n") { it }
            .length
        assertEquals(
            "canonicalText.length must match reconstructed paragraph text",
            reconstructedLength,
            layout.canonicalText.length,
        )

        if (layout.paragraphStartOffsets.isNotEmpty()) {
            assertEquals(0, layout.paragraphStartOffsets.first())
            layout.paragraphStartOffsets.zipWithNext { current, next ->
                assertTrue(
                    "paragraphStartOffsets must be strictly increasing (found $current then $next)",
                    current < next,
                )
            }
        }

        searchQueries.forEach { query ->
            if (layout.canonicalText.contains(query, ignoreCase = false)) {
                assertSearchOffsetsAreValid(layout, query)
            }
        }
    }
}
