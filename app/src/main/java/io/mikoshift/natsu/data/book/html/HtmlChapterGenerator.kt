package io.mikoshift.natsu.data.book.html

import org.commonmark.parser.Parser
import org.commonmark.renderer.html.HtmlRenderer

object HtmlChapterGenerator {
    private val markdownParser = Parser.builder().build()
    private val markdownRenderer = HtmlRenderer.builder().build()

    fun fromPlainText(text: String): String {
        val paragraphs = text.split("\n\n")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        val body = if (paragraphs.isEmpty()) {
            "<p></p>"
        } else {
            paragraphs.joinToString(separator = "\n") { paragraph ->
                "<p>${escapeHtml(paragraph).replace("\n", "<br>")}</p>"
            }
        }
        return wrapChapter(body)
    }

    fun fromMarkdown(markdown: String): String {
        val document = markdownParser.parse(markdown)
        return wrapChapter(markdownRenderer.render(document))
    }

    private fun wrapChapter(bodyHtml: String): String {
        return """
            <!DOCTYPE html>
            <html>
            <head>
              <meta charset="utf-8">
              <meta name="viewport" content="width=device-width, initial-scale=1">
            </head>
            <body>
            $bodyHtml
            </body>
            </html>
        """.trimIndent()
    }

    private fun escapeHtml(text: String): String {
        return buildString(text.length) {
            text.forEach { char ->
                when (char) {
                    '&' -> append("&amp;")
                    '<' -> append("&lt;")
                    '>' -> append("&gt;")
                    '"' -> append("&quot;")
                    '\'' -> append("&#39;")
                    else -> append(char)
                }
            }
        }
    }
}
