package io.mikoshift.natsu.data.book.epub

import io.mikoshift.natsu.domain.model.reading.ReadingBlock
import io.mikoshift.natsu.domain.model.reading.TextSpan
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import org.jsoup.parser.Parser

object XhtmlToReadingBlocks {
    private val SKIPPED_TAGS = setOf("aside", "nav", "script", "style", "header", "footer")
    private val HEADING_TAGS = setOf("h1", "h2", "h3", "h4", "h5", "h6")
    private val BLOCK_CONTAINER_TAGS = setOf("body", "section", "article", "div", "main")

    fun parse(xhtml: String, baseHref: String = ""): List<ReadingBlock> {
        val document = Jsoup.parse(xhtml, baseHref, Parser.xmlParser())
        document.select("script, style").remove()
        val root = document.body() ?: document
        return parseBlockChildren(root)
    }

    private fun parseBlockChildren(container: Element): List<ReadingBlock> {
        val blocks = mutableListOf<ReadingBlock>()
        container.childNodes().forEach { node ->
            when (node) {
                is Element -> blocks.addAll(parseBlockElement(node))
                is TextNode -> {
                    val text = node.text().trim()
                    if (text.isNotEmpty()) {
                        blocks.add(ReadingBlock.Paragraph(listOf(TextSpan(text = text))))
                    }
                }
            }
        }
        return blocks
    }

    private fun parseBlockElement(element: Element): List<ReadingBlock> {
        val tag = element.tagName().lowercase()
        if (tag in SKIPPED_TAGS) return emptyList()

        return when (tag) {
            in HEADING_TAGS -> {
                val text = textWithoutRubyAnnotations(element)
                if (text.isBlank()) emptyList()
                else listOf(ReadingBlock.Heading(text = text, level = tag.removePrefix("h").toInt()))
            }
            "p" -> paragraphToBlocks(element)
            "img" -> imageBlock(element)?.let { listOf(it) } ?: emptyList()
            in BLOCK_CONTAINER_TAGS -> {
                if (element.selectFirst(BLOCK_CONTAINER_TAGS.joinToString(",") { it }) != null ||
                    element.selectFirst(HEADING_TAGS.joinToString(",") { it }) != null ||
                    element.selectFirst("p, img") != null
                ) {
                    parseBlockChildren(element)
                } else {
                    paragraphBlock(element)?.let { listOf(it) } ?: emptyList()
                }
            }
            else -> {
                if (element.selectFirst("p, h1, h2, h3, h4, h5, h6, img") != null) {
                    parseBlockChildren(element)
                } else {
                    paragraphBlock(element)?.let { listOf(it) } ?: emptyList()
                }
            }
        }
    }

    private fun paragraphToBlocks(element: Element): List<ReadingBlock> {
        val blocks = mutableListOf<ReadingBlock>()
        val spans = mutableListOf<TextSpan>()
        element.childNodes().forEach { node ->
            when (node) {
                is Element -> if (node.tagName().equals("img", ignoreCase = true)) {
                    flushParagraph(spans, blocks)
                    imageBlock(node)?.let { blocks.add(it) }
                } else {
                    walkInline(node, spans)
                }
                is TextNode -> walkInline(node, spans)
            }
        }
        flushParagraph(spans, blocks)
        return blocks
    }

    private fun paragraphBlock(element: Element): ReadingBlock.Paragraph? {
        val spans = mutableListOf<TextSpan>()
        element.childNodes().forEach { walkInline(it, spans) }
        val merged = mergeAdjacentPlainSpans(spans)
        if (merged.isEmpty()) return null
        return ReadingBlock.Paragraph(merged)
    }

    private fun flushParagraph(spans: MutableList<TextSpan>, blocks: MutableList<ReadingBlock>) {
        val merged = mergeAdjacentPlainSpans(spans)
        if (merged.isNotEmpty()) {
            blocks.add(ReadingBlock.Paragraph(merged))
        }
        spans.clear()
    }

    private fun imageBlock(element: Element): ReadingBlock.Image? {
        val src = element.attr("src").trim()
        if (src.isEmpty() || isRemoteUrl(src)) return null
        val alt = element.attr("alt").takeIf { it.isNotBlank() }
        return ReadingBlock.Image(relativePath = src, alt = alt)
    }

    private fun walkInline(node: Node, spans: MutableList<TextSpan>) {
        when (node) {
            is TextNode -> {
                val text = node.text()
                if (text.isNotEmpty()) spans.add(TextSpan(text = text))
            }
            is Element -> when (node.tagName().lowercase()) {
                "ruby" -> spans.add(rubyToSpan(node))
                "br" -> spans.add(TextSpan(text = "\n"))
                else -> node.childNodes().forEach { walkInline(it, spans) }
            }
        }
    }

    private fun rubyToSpan(ruby: Element): TextSpan {
        val reading = ruby.selectFirst("rt")?.text()?.takeIf { it.isNotEmpty() }
        val baseText = textWithoutRubyAnnotations(ruby)
        return TextSpan(text = baseText, reading = reading)
    }

    private fun textWithoutRubyAnnotations(element: Element): String {
        val clone = element.clone()
        clone.select("rt, rp").remove()
        return clone.text()
    }

    private fun mergeAdjacentPlainSpans(spans: List<TextSpan>): List<TextSpan> {
        if (spans.isEmpty()) return spans
        val merged = mutableListOf<TextSpan>()
        spans.forEach { span ->
            val last = merged.lastOrNull()
            if (last != null && last.reading == null && span.reading == null) {
                merged[merged.lastIndex] = last.copy(text = last.text + span.text)
            } else {
                merged.add(span)
            }
        }
        return merged
    }

    private fun isRemoteUrl(path: String): Boolean {
        return path.startsWith("http://", ignoreCase = true) ||
            path.startsWith("https://", ignoreCase = true)
    }
}
