package io.mikoshift.natsu.data.book.load

import io.mikoshift.natsu.domain.model.reading.BookFormat
import io.mikoshift.natsu.domain.model.reading.ManifestSection
import io.mikoshift.natsu.domain.model.reading.ReadingBlock
import io.mikoshift.natsu.domain.model.reading.ReadingSection
import java.io.File
import java.nio.charset.StandardCharsets

class MarkdownFormatLoader : FormatReadingLoader {
    override val format: BookFormat = BookFormat.Markdown

    override suspend fun loadSection(bookDir: File, section: ManifestSection): ReadingSection {
        val contentFile = File(bookDir, section.path)
        require(contentFile.exists()) {
            "Section content not found: ${section.path}"
        }
        val markdown = contentFile.readText(StandardCharsets.UTF_8)
        val blocks = MarkdownToReadingBlocks.parse(markdown).map { block ->
            when (block) {
                is ReadingBlock.Image -> block.copy(
                    relativePath = resolveAssetPath(
                        bookDir = bookDir,
                        sectionPath = section.path,
                        destination = block.relativePath,
                    ),
                )
                else -> block
            }
        }
        return ReadingSection(
            id = section.id,
            title = section.title,
            blocks = blocks,
        )
    }

    internal fun resolveAssetPath(bookDir: File, sectionPath: String, destination: String): String {
        if (destination.startsWith("http://") || destination.startsWith("https://")) {
            return destination
        }
        val sectionFile = File(bookDir, sectionPath)
        val sectionDir = sectionFile.parentFile ?: bookDir
        val resolved = File(sectionDir, destination).canonicalFile
        val bookCanonical = bookDir.canonicalFile
        require(resolved.path.startsWith(bookCanonical.path)) {
            "Image path escapes book directory: $destination"
        }
        return resolved.relativeTo(bookCanonical).path.replace('\\', '/')
    }
}
