package io.mikoshift.natsu.data.book.load

import io.mikoshift.natsu.data.book.epub.XhtmlToReadingBlocks
import io.mikoshift.natsu.domain.model.reading.ManifestSection
import io.mikoshift.natsu.domain.model.reading.ReadingBlock
import io.mikoshift.natsu.domain.model.reading.ReadingSection
import java.io.File
import java.nio.charset.StandardCharsets

internal object HtmlSectionBlocks {
    fun loadSection(
        bookDir: File,
        section: ManifestSection,
        contentFile: File,
        resolveImagePath: ((String) -> String)? = null,
    ): ReadingSection {
        val html = contentFile.readText(StandardCharsets.UTF_8)
        val baseHref = contentFile.parentFile?.toURI()?.toString() ?: bookDir.toURI().toString()
        val blocks = XhtmlToReadingBlocks.parse(html, baseHref).map { block ->
            when (block) {
                is ReadingBlock.Image -> {
                    if (resolveImagePath != null && !isRemoteUrl(block.relativePath)) {
                        block.copy(relativePath = resolveImagePath(block.relativePath))
                    } else {
                        block
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

    private fun isRemoteUrl(path: String): Boolean {
        return path.startsWith("http://", ignoreCase = true) ||
            path.startsWith("https://", ignoreCase = true)
    }
}
