package io.mikoshift.natsu.ui.reader.web

import java.net.URLDecoder
import java.nio.charset.StandardCharsets

object ReaderWebUrls {
    fun chapterUrl(documentId: String, relativePath: String): String {
        val encodedPath = relativePath.split('/')
            .filter { it.isNotEmpty() }
            .joinToString("/") { segment -> encodePathSegment(segment) }
        return "${ReaderBridgeContract.BOOKS_URL_PREFIX}$documentId/$encodedPath"
    }

    fun readerAssetUrl(assetPath: String): String {
        val normalized = assetPath.removePrefix("/")
        return "${ReaderBridgeContract.READER_ASSETS_URL_PREFIX}$normalized"
    }

    fun bridgeScriptUrl(): String = readerAssetUrl(ReaderBridgeContract.BRIDGE_ASSET_PATH)

    fun themeStylesheetUrl(): String = readerAssetUrl(ReaderBridgeContract.THEME_ASSET_PATH)

    fun isBookChapterUrl(url: String, documentId: String): Boolean {
        return url.startsWith("${ReaderBridgeContract.BOOKS_URL_PREFIX}$documentId/")
    }

    fun relativePathFromChapterUrl(url: String, documentId: String): String? {
        val prefix = "${ReaderBridgeContract.BOOKS_URL_PREFIX}$documentId/"
        if (!url.startsWith(prefix)) return null
        return decodePath(url.removePrefix(prefix))
    }

    private fun encodePathSegment(segment: String): String {
        return segment.toByteArray(StandardCharsets.UTF_8)
            .joinToString("") { byte ->
                val value = byte.toInt() and 0xFF
                when {
                    value in 'a'.code..'z'.code ||
                        value in 'A'.code..'Z'.code ||
                        value in '0'.code..'9'.code ||
                        value in setOf('-'.code, '_'.code, '.'.code, '~'.code) -> value.toChar().toString()
                    else -> "%%%02X".format(value)
                }
            }
    }

    private fun decodePath(path: String): String {
        return URLDecoder.decode(path, StandardCharsets.UTF_8)
    }
}
