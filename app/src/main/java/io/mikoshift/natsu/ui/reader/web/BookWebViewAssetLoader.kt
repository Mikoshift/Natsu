package io.mikoshift.natsu.ui.reader.web

import android.content.Context
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewClientCompat
import java.io.File
import java.io.FileInputStream

class BookWebViewAssetLoader(
    context: Context,
    private val bookDir: File,
    private val documentId: String,
) {
    private val assetLoader = WebViewAssetLoader.Builder()
        .addPathHandler("/books/$documentId/") { path ->
            serveBookFile(path)
        }
        .addPathHandler("/reader/") { path ->
            serveAppAsset(context, path)
        }
        .build()

    fun createWebViewClient(
        onChapterLink: (relativePath: String) -> Unit,
        onPageFinished: (url: String) -> Unit,
    ): WebViewClientCompat {
        return object : WebViewClientCompat() {
            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest,
            ): WebResourceResponse? {
                return assetLoader.shouldInterceptRequest(request.url)
            }

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val url = request.url.toString()
                val relativePath = ReaderWebUrls.relativePathFromChapterUrl(url, documentId)
                if (relativePath != null) {
                    onChapterLink(relativePath)
                    return true
                }
                return false
            }

            override fun onPageFinished(view: WebView, url: String?) {
                if (url != null) {
                    onPageFinished(url)
                }
            }
        }
    }

    private fun serveBookFile(path: String): WebResourceResponse? {
        val relativePath = path.trimStart('/')
        if (relativePath.isEmpty()) return null
        val target = File(bookDir, relativePath).canonicalFile
        val bookRoot = bookDir.canonicalFile
        if (!target.path.startsWith(bookRoot.path)) return null
        if (!target.exists() || !target.isFile) return null
        val mimeType = guessMimeType(target.name)
        return WebResourceResponse(
            mimeType,
            null,
            FileInputStream(target),
        )
    }

    private fun serveAppAsset(context: Context, path: String): WebResourceResponse? {
        val assetPath = "reader/${path.trimStart('/')}"
        return runCatching {
            val mimeType = guessMimeType(assetPath)
            WebResourceResponse(
                mimeType,
                null,
                context.assets.open(assetPath),
            )
        }.getOrNull()
    }

    private fun guessMimeType(fileName: String): String {
        return when (fileName.substringAfterLast('.').lowercase()) {
            "html", "htm" -> "text/html"
            "xhtml" -> "application/xhtml+xml"
            "css" -> "text/css"
            "js" -> "text/javascript"
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            "svg" -> "image/svg+xml"
            "woff", "woff2" -> "font/woff2"
            "ttf" -> "font/ttf"
            "otf" -> "font/otf"
            else -> "application/octet-stream"
        }
    }
}
