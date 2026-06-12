package io.mikoshift.natsu.data.book.load

import io.mikoshift.natsu.domain.model.reading.ReadingBlock
import io.mikoshift.natsu.domain.model.reading.ReadingBook
import org.junit.Assert.assertTrue

fun assertReadingBookInvariants(book: ReadingBook) {
    assertTrue("book id must not be blank", book.id.isNotBlank())
    assertTrue("book title must not be blank", book.title.isNotBlank())
    assertTrue("book must contain at least one section", book.sections.isNotEmpty())

    book.sections.forEach { section ->
        assertTrue("section id must not be blank", section.id.isNotBlank())
        section.blocks.forEach { block ->
            when (block) {
                is ReadingBlock.Paragraph -> {
                    assertTrue("paragraph must contain spans", block.spans.isNotEmpty())
                    val text = block.spans.joinToString(separator = "") { it.text }
                    assertTrue("paragraph text must not be empty", text.isNotEmpty())
                }
                is ReadingBlock.Heading -> {
                    assertTrue("heading text must not be blank", block.text.isNotBlank())
                    assertTrue("heading level must be positive", block.level > 0)
                }
                is ReadingBlock.Image -> {
                    assertTrue("image path must not be blank", block.relativePath.isNotBlank())
                }
            }
        }
    }
}
