package io.mikoshift.natsu.data.book.load

import io.mikoshift.natsu.data.book.BookPathResolver
import io.mikoshift.natsu.domain.model.reading.BookFormat
import io.mikoshift.natsu.domain.model.reading.ManifestSection
import io.mikoshift.natsu.domain.model.reading.ReadingBlock
import io.mikoshift.natsu.domain.model.reading.ReadingSection
import java.io.File
import java.nio.charset.StandardCharsets

class MarkdownFormatLoader : FormatReadingLoader {
    override val format: BookFormat = BookFormat.Markdown

    override suspend fun loadSection(bookDir: File, section: ManifestSection): ReadingSection {
        val contentFile = BookPathResolver.resolveRelativePath(bookDir, section.path)
        require(contentFile.exists()) {
            "Section content not found: ${section.path}"
        }
        val markdown = contentFile.readText(StandardCharsets.UTF_8)
        val blocks = MarkdownToReadingBlocks.parse(markdown).mapNotNull { block ->
            when (block) {
                is ReadingBlock.Image -> {
                    if (isRemoteUrl(block.relativePath)) {
                        null
                    } else {
                        block.copy(
                            relativePath = resolveAssetPath(
                                bookDir = bookDir,
                                sectionPath = section.path,
                                destination = block.relativePath,
                            ),
                        )
                    }
                }
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
        require(!isRemoteUrl(destination)) {
            "Remote image URLs are not allowed: $destination"
        }
        val sectionFile = BookPathResolver.resolveRelativePath(bookDir, sectionPath)
        val sectionDir = sectionFile.parentFile ?: bookDir
        val bookRootPath = bookDir.canonicalFile.toPath()
        val resolved = sectionDir.toPath().resolve(destination).normalize()
        require(resolved.startsWith(bookRootPath.resolve(""))) {
            "Image path escapes book directory: $destination"
        }
        return bookRootPath.relativize(resolved).toString().replace('\\', '/')
    }

    private fun isRemoteUrl(path: String): Boolean {
        return path.startsWith("http://", ignoreCase = true) ||
            path.startsWith("https://", ignoreCase = true)
    }
}
