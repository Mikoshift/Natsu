package io.mikoshift.natsu.data.book.epub.spike

import android.content.Context
import io.mikoshift.natsu.domain.model.reading.ReadingBlock
import io.mikoshift.natsu.domain.model.reading.ReadingBook
import io.mikoshift.natsu.domain.model.reading.ReadingSection
import java.io.File
import java.net.URI
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.util.getOrElse
import org.readium.r2.shared.util.resource.Resource

class DraftEpubLoader(
    private val publicationOpener: EpubPublicationOpener,
) {
    constructor(context: Context) : this(EpubPublicationOpener(context))

    suspend fun load(
        epubFile: File,
        bookId: String = "spike",
    ): ReadingBook {
        val publication = publicationOpener.open(epubFile)
        return loadFromPublication(
            publication = publication,
            bookId = bookId,
            title = publication.metadata.title?.takeIf { it.isNotBlank() } ?: epubFile.nameWithoutExtension,
        )
    }

    suspend fun loadFromPublication(
        publication: Publication,
        bookId: String,
        title: String,
    ): ReadingBook {
        val spine = EpubSpineMapper.mapSpine(publication)
        require(spine.isNotEmpty()) { "EPUB has no HTML spine items" }

        val sections = spine.map { item ->
            val resource = publication.get(item.link)
                ?: throw IllegalStateException("Spine resource not found: ${item.href}")
            val xhtml = resource.readXhtmlString()
            val blocks = XhtmlToReadingBlocks.parse(xhtml, baseHref = item.href)
                .map { block ->
                    when (block) {
                        is ReadingBlock.Image -> block.copy(
                            relativePath = resolveImagePath(
                                spineHref = item.href,
                                destination = block.relativePath,
                            ),
                        )
                        else -> block
                    }
                }
            ReadingSection(
                id = item.id,
                title = item.title,
                blocks = blocks,
            )
        }

        return ReadingBook(
            id = bookId,
            title = title,
            sections = sections,
        )
    }

    private suspend fun Resource.readXhtmlString(): String {
        val bytes = read().getOrElse { error ->
            throw IllegalStateException("Failed to read resource: $error")
        }
        return String(bytes, Charsets.UTF_8)
    }

    companion object {
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
}
