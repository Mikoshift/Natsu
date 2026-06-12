package io.mikoshift.natsu.data.book.epub.spike

import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Publication

data class EpubSpineItem(
    val id: String,
    val href: String,
    val title: String?,
    val link: Link,
)

object EpubSpineMapper {
    fun mapSpine(publication: Publication): List<EpubSpineItem> {
        val titlesByHref = flattenTableOfContents(publication.tableOfContents)
        return publication.readingOrder
            .filter(::isHtmlSpineItem)
            .mapIndexed { index, link ->
                val href = link.url().toString()
                EpubSpineItem(
                    id = link.title?.takeIf { it.isNotBlank() } ?: "spine-$index",
                    href = href,
                    title = titlesByHref[href] ?: link.title,
                    link = link,
                )
            }
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
                val href = link.url().toString()
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
