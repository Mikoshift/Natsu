package io.mikoshift.natsu.data.book.load

import io.mikoshift.natsu.data.book.BookPathResolver
import io.mikoshift.natsu.data.book.epub.XhtmlToReadingBlocks
import io.mikoshift.natsu.domain.model.reading.BookFormat
import io.mikoshift.natsu.domain.model.reading.ManifestSection
import io.mikoshift.natsu.domain.model.reading.ReadingBlock
import io.mikoshift.natsu.domain.model.reading.ReadingSection
import java.io.File
import java.net.URI
import java.nio.charset.StandardCharsets

class EpubFormatLoader : FormatReadingLoader {
    override val format: BookFormat = BookFormat.Epub

    override suspend fun loadSection(bookDir: File, section: ManifestSection): ReadingSection {
        val xhtmlFile = BookPathResolver.resolveRelativePath(bookDir, section.path)
        require(xhtmlFile.exists()) {
            "Section content not found: ${section.path}"
        }
        val xhtml = xhtmlFile.readText(StandardCharsets.UTF_8)
        val blocks = XhtmlToReadingBlocks.parse(xhtml, baseHref = section.path)
            .map { block ->
                when (block) {
                    is ReadingBlock.Image -> block.copy(
                        relativePath = resolveImagePath(
                            spineHref = section.path,
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

    internal fun resolveImagePath(spineHref: String, destination: String): String {
        require(!destination.startsWith("http://", ignoreCase = true) &&
            !destination.startsWith("https://", ignoreCase = true)
        ) {
            "Remote image URLs are not supported: $destination"
        }
        val base = spineHref.substringBeforeLast('/', missingDelimiterValue = "")
        val combined = if (base.isEmpty()) destination else "$base/$destination"
        return URI(combined).normalize().path.removePrefix("/")
    }
}
