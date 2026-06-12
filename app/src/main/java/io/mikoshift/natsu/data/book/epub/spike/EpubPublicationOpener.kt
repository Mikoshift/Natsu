package io.mikoshift.natsu.data.book.epub.spike

import android.content.Context
import java.io.File
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.util.asset.AssetRetriever
import org.readium.r2.shared.util.getOrElse
import org.readium.r2.shared.util.http.DefaultHttpClient
import org.readium.r2.shared.util.toUrl
import org.readium.r2.streamer.PublicationOpener
import org.readium.r2.streamer.parser.DefaultPublicationParser

class EpubPublicationOpener(
    context: Context,
) {
    private val httpClient = DefaultHttpClient()
    private val assetRetriever = AssetRetriever(
        contentResolver = context.contentResolver,
        httpClient = httpClient,
    )
    private val publicationOpener = PublicationOpener(
        publicationParser = DefaultPublicationParser(
            context = context,
            httpClient = httpClient,
            assetRetriever = assetRetriever,
            pdfFactory = null,
        ),
        contentProtections = emptyList(),
    )

    suspend fun open(epubFile: File): Publication {
        require(epubFile.isFile) { "EPUB path is not a file: ${epubFile.absolutePath}" }
        val url = epubFile.toUrl()
        val asset = assetRetriever.retrieve(url).getOrElse { error ->
            throw IllegalStateException("Failed to retrieve EPUB asset: $error")
        }
        return publicationOpener.open(asset, allowUserInteraction = false).getOrElse { error ->
            throw IllegalStateException("Failed to open EPUB publication: $error")
        }
    }
}
