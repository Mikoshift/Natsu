package io.mikoshift.natsu.data.book.load

import io.mikoshift.natsu.data.book.BookPathResolver
import io.mikoshift.natsu.data.reader.buildParagraphLayout
import io.mikoshift.natsu.domain.model.reading.BookFormat
import io.mikoshift.natsu.domain.model.reading.ManifestSection
import io.mikoshift.natsu.domain.model.reading.ReadingBlock
import io.mikoshift.natsu.domain.model.reading.ReadingSection
import io.mikoshift.natsu.domain.model.reading.TextSpan
import java.io.File
import java.nio.charset.StandardCharsets

class PlainTextFormatLoader : FormatReadingLoader {
    override val format: BookFormat = BookFormat.PlainText

    override suspend fun loadSection(bookDir: File, section: ManifestSection): ReadingSection {
        val contentFile = BookPathResolver.resolveRelativePath(bookDir, section.path)
        require(contentFile.exists()) {
            "Section content not found: ${section.path}"
        }
        if (isHtmlPath(section.path)) {
            return HtmlSectionBlocks.loadSection(
                bookDir = bookDir,
                section = section,
                contentFile = contentFile,
            )
        }
        val text = contentFile.readText(StandardCharsets.UTF_8)
        val layout = buildParagraphLayout(text)
        val blocks = layout.paragraphs.map { paragraph ->
            ReadingBlock.Paragraph(listOf(TextSpan(text = paragraph)))
        }
        return ReadingSection(
            id = section.id,
            title = section.title,
            blocks = blocks,
        )
    }

    private fun isHtmlPath(path: String): Boolean {
        val lower = path.lowercase()
        return lower.endsWith(".html") || lower.endsWith(".htm") || lower.endsWith(".xhtml")
    }
}
