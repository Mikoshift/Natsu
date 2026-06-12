package io.mikoshift.natsu.ui.reader.web

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.parser.Parser

object ChapterHtmlInjector {
    private val injectableExtensions = setOf("html", "htm", "xhtml")

    fun isInjectable(fileName: String): Boolean {
        return fileName.substringAfterLast('.', "").lowercase() in injectableExtensions
    }

    fun inject(html: String, isXhtml: Boolean): String {
        val document = if (isXhtml) {
            Jsoup.parse(html, "", Parser.xmlParser())
        } else {
            Jsoup.parse(html)
        }
        injectIntoHead(document)
        return serialize(document, isXhtml)
    }

    private fun injectIntoHead(document: Document) {
        val head = document.head()
        if (head.select("meta[name=viewport]").isEmpty()) {
            head.appendElement("meta")
                .attr("name", "viewport")
                .attr("content", "width=device-width, initial-scale=1")
        }
        if (head.select("link[data-natsu-theme]").isEmpty()) {
            head.appendElement("link")
                .attr("rel", "stylesheet")
                .attr("href", ReaderWebUrls.themeStylesheetUrl())
                .attr("data-natsu-theme", "true")
        }
        if (head.select("script[data-natsu-bridge]").isEmpty()) {
            head.appendElement("script")
                .attr("src", ReaderWebUrls.bridgeScriptUrl())
                .attr("data-natsu-bridge", "true")
                .attr("defer", "defer")
        }
    }

    private fun serialize(document: Document, isXhtml: Boolean): String {
        if (isXhtml) {
            document.outputSettings().syntax(org.jsoup.nodes.Document.OutputSettings.Syntax.xml)
        }
        return document.outerHtml()
    }
}
