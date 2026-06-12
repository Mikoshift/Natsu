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
              <style>
                :root {
                  --natsu-font-size: 16px;
                  --natsu-line-height: 1.8;
                  --natsu-bg: #ffffff;
                  --natsu-text: #1c1b1f;
                }
                body.natsu-chapter {
                  margin: 0;
                  padding: 12px 16px 32px;
                  font-family: sans-serif;
                  font-size: var(--natsu-font-size);
                  line-height: var(--natsu-line-height);
                  color: var(--natsu-text);
                  background: var(--natsu-bg);
                  -webkit-text-size-adjust: 100%;
                }
                body.natsu-chapter p { margin: 0 0 1em; }
              </style>
            </head>
            <body class="natsu-chapter">
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
