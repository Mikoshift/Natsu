package io.mikoshift.natsu.data.book.load

import io.mikoshift.natsu.domain.model.reading.ReadingBlock
import io.mikoshift.natsu.domain.model.reading.TextSpan
import org.commonmark.node.BlockQuote
import org.commonmark.node.BulletList
import org.commonmark.node.FencedCodeBlock
import org.commonmark.node.Heading
import org.commonmark.node.Image
import org.commonmark.node.ListItem
import org.commonmark.node.Node
import org.commonmark.node.OrderedList
import org.commonmark.node.Paragraph
import org.commonmark.node.Text
import org.commonmark.parser.Parser

object MarkdownToReadingBlocks {
    private val parser = Parser.builder().build()

    fun parse(markdown: String): List<ReadingBlock> {
        val document = parser.parse(markdown)
        val blocks = mutableListOf<ReadingBlock>()
        var node = document.firstChild
        while (node != null) {
            blocks.addAll(parseNode(node))
            node = node.next
        }
        return blocks
    }

    private fun parseNode(node: Node): List<ReadingBlock> =
        when (node) {
            is Heading -> {
                val text = extractText(node)
                if (text.isBlank()) emptyList()
                else listOf(ReadingBlock.Heading(text = text, level = node.level))
            }
            is Paragraph -> paragraphToBlocks(node)
            is FencedCodeBlock -> {
                val literal = node.literal?.trimEnd().orEmpty()
                if (literal.isBlank()) emptyList()
                else listOf(ReadingBlock.Paragraph(listOf(TextSpan(text = literal))))
            }
            is BlockQuote -> {
                val blocks = mutableListOf<ReadingBlock>()
                var child = node.firstChild
                while (child != null) {
                    blocks.addAll(parseNode(child))
                    child = child.next
                }
                blocks
            }
            is BulletList, is OrderedList -> extractListItems(node)
            else -> emptyList()
        }

    private fun extractListItems(listNode: Node): List<ReadingBlock> {
        val blocks = mutableListOf<ReadingBlock>()
        var item = listNode.firstChild
        while (item != null) {
            if (item is ListItem) {
                var child = item.firstChild
                while (child != null) {
                    blocks.addAll(parseNode(child))
                    child = child.next
                }
            }
            item = item.next
        }
        return blocks
    }

    private fun paragraphToBlocks(paragraph: Paragraph): List<ReadingBlock> {
        val blocks = mutableListOf<ReadingBlock>()
        val textBuilder = StringBuilder()
        var child = paragraph.firstChild
        while (child != null) {
            when (child) {
                is Image -> {
                    flushParagraphText(textBuilder, blocks)
                    val destination = child.destination?.trim().orEmpty()
                    if (destination.isNotEmpty()) {
                        blocks.add(
                            ReadingBlock.Image(
                                relativePath = destination,
                                alt = imageAlt(child),
                            ),
                        )
                    }
                }
                else -> textBuilder.append(extractText(child))
            }
            child = child.next
        }
        flushParagraphText(textBuilder, blocks)
        return blocks
    }

    private fun flushParagraphText(textBuilder: StringBuilder, blocks: MutableList<ReadingBlock>) {
        val text = textBuilder.toString()
        if (text.isNotEmpty()) {
            blocks.add(ReadingBlock.Paragraph(listOf(TextSpan(text = text))))
        }
        textBuilder.clear()
    }

    private fun imageAlt(image: Image): String? {
        val child = image.firstChild
        return if (child is Text) child.literal else null
    }

    private fun extractText(node: Node): String {
        if (node is Text) return node.literal.orEmpty()
        val builder = StringBuilder()
        var child = node.firstChild
        while (child != null) {
            builder.append(extractText(child))
            child = child.next
        }
        return builder.toString()
    }
}
