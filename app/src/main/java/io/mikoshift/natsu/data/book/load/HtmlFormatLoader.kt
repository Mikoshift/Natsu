package io.mikoshift.natsu.data.book.load

import io.mikoshift.natsu.data.book.BookPathResolver
import io.mikoshift.natsu.domain.model.reading.BookFormat
import io.mikoshift.natsu.domain.model.reading.ManifestSection
import io.mikoshift.natsu.domain.model.reading.ReadingSection
import java.io.File

class HtmlFormatLoader : FormatReadingLoader {
    override val format: BookFormat = BookFormat.Html

    override suspend fun loadSection(bookDir: File, section: ManifestSection): ReadingSection {
        val contentFile = BookPathResolver.resolveRelativePath(bookDir, section.path)
        require(contentFile.exists()) { "Section content not found: ${section.path}" }
        return HtmlSectionBlocks.loadSection(
            bookDir = bookDir,
            section = section,
            contentFile = contentFile,
        )
    }
}
