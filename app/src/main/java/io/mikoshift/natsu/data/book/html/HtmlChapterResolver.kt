package io.mikoshift.natsu.data.book.html

import io.mikoshift.natsu.data.book.BookPathResolver
import io.mikoshift.natsu.data.book.BookStorage
import io.mikoshift.natsu.domain.model.reading.ManifestSection
import java.io.File
import java.nio.charset.StandardCharsets

object HtmlChapterResolver {
    fun resolveRelativePath(bookDir: File, section: ManifestSection): String {
        val path = section.path
        if (isHtmlPath(path)) return path

        val htmlPath = htmlPathForSource(path)
        val htmlFile = BookPathResolver.resolveRelativePath(bookDir, htmlPath)
        if (!htmlFile.exists()) {
            val sourceFile = BookPathResolver.resolveRelativePath(bookDir, path)
            require(sourceFile.exists()) { "Section content not found: $path" }
            val sourceText = sourceFile.readText(StandardCharsets.UTF_8)
            val html = when {
                path.endsWith(".md", ignoreCase = true) ||
                    path.endsWith(".markdown", ignoreCase = true) -> {
                    HtmlChapterGenerator.fromMarkdown(sourceText)
                }
                else -> HtmlChapterGenerator.fromPlainText(sourceText)
            }
            htmlFile.parentFile?.mkdirs()
            htmlFile.writeText(html, StandardCharsets.UTF_8)
        }
        return htmlPath
    }

    private fun isHtmlPath(path: String): Boolean {
        val lower = path.lowercase()
        return lower.endsWith(".html") || lower.endsWith(".htm") || lower.endsWith(".xhtml")
    }

    private fun htmlPathForSource(path: String): String {
        return when {
            path.endsWith(".txt", ignoreCase = true) ||
                path.endsWith(".text", ignoreCase = true) ||
                path.endsWith(".md", ignoreCase = true) ||
                path.endsWith(".markdown", ignoreCase = true) -> BookStorage.HTML_CONTENT_PATH
            else -> path.substringBeforeLast('.') + ".html"
        }
    }
}
