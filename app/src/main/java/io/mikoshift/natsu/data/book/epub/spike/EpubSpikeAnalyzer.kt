package io.mikoshift.natsu.data.book.epub.spike

import io.mikoshift.natsu.data.reader.ReadingLayoutBuilder
import io.mikoshift.natsu.domain.model.reading.ReadingBlock
import io.mikoshift.natsu.domain.model.reading.ReadingBook
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Publication

data class EpubSpikeReport(
    val title: String,
    val readingOrderSize: Int,
    val htmlSpineSize: Int,
    val sectionCount: Int,
    val tocEntryCount: Int,
    val nestedTocDepth: Int,
    val paragraphCount: Int,
    val rubyParagraphCount: Int,
    val rubySpanCount: Int,
    val footnoteAsideCount: Int,
    val imageBlockCount: Int,
    val canonicalTextLength: Int,
) {
    val rubyParagraphRatio: Double =
        if (paragraphCount == 0) 0.0 else rubyParagraphCount.toDouble() / paragraphCount
}

object EpubSpikeAnalyzer {
    fun analyzePublication(publication: Publication, book: ReadingBook): EpubSpikeReport {
        val layout = ReadingLayoutBuilder().build(book)
        val paragraphs = book.sections.flatMap { section ->
            section.blocks.filterIsInstance<ReadingBlock.Paragraph>()
        }
        val rubyParagraphCount = paragraphs.count { paragraph ->
            paragraph.spans.any { it.reading != null }
        }
        val rubySpanCount = paragraphs.sumOf { paragraph ->
            paragraph.spans.count { it.reading != null }
        }

        return EpubSpikeReport(
            title = publication.metadata.title ?: book.title,
            readingOrderSize = publication.readingOrder.size,
            htmlSpineSize = EpubSpineMapper.mapSpine(publication).size,
            sectionCount = book.sections.size,
            tocEntryCount = countTocEntries(publication.tableOfContents),
            nestedTocDepth = maxTocDepth(publication.tableOfContents),
            paragraphCount = paragraphs.size,
            rubyParagraphCount = rubyParagraphCount,
            rubySpanCount = rubySpanCount,
            footnoteAsideCount = countFootnoteMarkers(book),
            imageBlockCount = book.sections.sumOf { section ->
                section.blocks.count { it is ReadingBlock.Image }
            },
            canonicalTextLength = layout.canonicalText.length,
        )
    }

    fun formatReport(report: EpubSpikeReport): String = buildString {
        appendLine("title=${report.title}")
        appendLine("readingOrderSize=${report.readingOrderSize}")
        appendLine("htmlSpineSize=${report.htmlSpineSize}")
        appendLine("sectionCount=${report.sectionCount}")
        appendLine("tocEntryCount=${report.tocEntryCount}")
        appendLine("nestedTocDepth=${report.nestedTocDepth}")
        appendLine("paragraphCount=${report.paragraphCount}")
        appendLine("rubyParagraphCount=${report.rubyParagraphCount}")
        appendLine("rubySpanCount=${report.rubySpanCount}")
        appendLine("rubyParagraphRatio=${"%.2f".format(report.rubyParagraphRatio)}")
        appendLine("footnoteAsideCount=${report.footnoteAsideCount}")
        appendLine("imageBlockCount=${report.imageBlockCount}")
        appendLine("canonicalTextLength=${report.canonicalTextLength}")
    }

    private fun countTocEntries(links: List<Link>): Int {
        var count = 0
        fun walk(nodes: List<Link>) {
            nodes.forEach { link ->
                count++
                walk(link.children)
            }
        }
        walk(links)
        return count
    }

    private fun maxTocDepth(links: List<Link>): Int {
        if (links.isEmpty()) return 0
        return links.maxOf { link -> 1 + maxTocDepth(link.children) }
    }

    private fun countFootnoteMarkers(book: ReadingBook): Int {
        return book.sections.sumOf { section ->
            section.blocks.count { block ->
                block is ReadingBlock.Paragraph &&
                    block.spans.any { span -> span.text.contains("脚注") || span.text.contains("footnote", ignoreCase = true) }
            }
        }
    }
}
