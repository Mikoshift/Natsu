package io.mikoshift.natsu.data.book.epub

import io.mikoshift.natsu.domain.model.reading.ManifestSection
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Publication

object EpubSpineMapper {
    fun mapSpineToManifestSections(publication: Publication): List<ManifestSection> {
        val titlesByHref = flattenTableOfContents(publication.tableOfContents)
        return publication.readingOrder
            .filter(::isHtmlSpineItem)
            .mapIndexed { index, link ->
                val href = normalizePublicationHref(link.url().toString())
                ManifestSection(
                    id = "spine-$index",
                    title = titlesByHref[href] ?: link.title,
                    path = href,
                )
            }
    }

    internal fun normalizePublicationHref(href: String): String {
        val withoutFragment = href.substringBefore('#')
        val path = when {
            withoutFragment.contains("://") -> {
                java.net.URI(withoutFragment).path.orEmpty()
            }
            else -> withoutFragment
        }
        return path.removePrefix("/")
    }

    private fun isHtmlSpineItem(link: Link): Boolean {
        val href = link.url().toString().lowercase()
        return href.endsWith(".xhtml") ||
            href.endsWith(".html") ||
            href.endsWith(".htm") ||
            link.mediaType?.isHtml == true
    }

    private fun flattenTableOfContents(links: List<Link>): Map<String, String> {
        val titles = linkedMapOf<String, String>()
        fun walk(nodes: List<Link>) {
            nodes.forEach { link ->
                val href = normalizePublicationHref(link.url().toString())
                val title = link.title?.takeIf { it.isNotBlank() }
                if (title != null && href !in titles) {
                    titles[href] = title
                }
                walk(link.children)
            }
        }
        walk(links)
        return titles
    }
}
