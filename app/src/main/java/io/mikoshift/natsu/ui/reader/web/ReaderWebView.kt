package io.mikoshift.natsu.ui.reader.web

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import io.mikoshift.natsu.domain.model.FuriganaMode
import io.mikoshift.natsu.domain.model.ReaderSettings
import io.mikoshift.natsu.ui.reader.FuriganaInjectToken
import java.io.File

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun ReaderWebView(
    bookDir: File,
    documentId: String,
    chapterUrl: String?,
    readerSettings: ReaderSettings,
    scrollToCharOffset: Int?,
    scrollRequestId: Long,
    searchHighlightRanges: List<IntRange>,
    furiganaTokens: List<FuriganaInjectToken>,
    controller: ReaderWebViewController,
    onWordTap: (text: String, rangeStart: Int, rangeEnd: Int) -> Unit,
    onScrollProgress: (ratio: Float) -> Unit,
    onChapterReady: () -> Unit,
    onChapterLink: (relativePath: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val assetLoader = remember(bookDir, documentId) {
        BookWebViewAssetLoader(
            context = context.applicationContext,
            bookDir = bookDir,
            documentId = documentId,
        )
    }
    val jsBridge = remember(onWordTap, onScrollProgress, onChapterReady) {
        ReaderJsBridge(
            onWordTap = onWordTap,
            onScrollProgress = onScrollProgress,
            onChapterReady = onChapterReady,
        )
    }

    AndroidView(
        modifier = modifier,
        onRelease = { webView ->
            webView.stopLoading()
            webView.removeJavascriptInterface(ReaderBridgeContract.JS_INTERFACE_NAME)
            webView.destroy()
            controller.detach()
        },
        factory = { factoryContext ->
            WebView(factoryContext).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                settings.loadsImagesAutomatically = true
                settings.useWideViewPort = true
                settings.loadWithOverviewMode = true
                settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                addJavascriptInterface(jsBridge, ReaderBridgeContract.JS_INTERFACE_NAME)
                webViewClient = assetLoader.createWebViewClient(
                    onChapterLink = onChapterLink,
                    onPageFinished = { controller.injectReaderAssets(onComplete = {}) },
                )
                controller.attach(this)
            }
        },
        update = { webView ->
            controller.attach(webView)
        },
    )

    LaunchedEffect(chapterUrl) {
        val url = chapterUrl ?: return@LaunchedEffect
        controller.loadChapter(url)
    }

    LaunchedEffect(readerSettings, chapterUrl) {
        if (chapterUrl == null) return@LaunchedEffect
        controller.applyTheme(readerSettings)
    }

    LaunchedEffect(scrollRequestId, scrollToCharOffset, chapterUrl) {
        val offset = scrollToCharOffset ?: return@LaunchedEffect
        if (chapterUrl == null) return@LaunchedEffect
        controller.scrollToOffset(offset)
    }

    LaunchedEffect(searchHighlightRanges, chapterUrl) {
        if (chapterUrl == null) return@LaunchedEffect
        controller.highlightSearch(searchHighlightRanges)
    }

    LaunchedEffect(furiganaTokens, readerSettings.furiganaMode, chapterUrl) {
        if (chapterUrl == null) return@LaunchedEffect
        if (readerSettings.furiganaMode == FuriganaMode.OFF) return@LaunchedEffect
        controller.injectRuby(furiganaTokens)
    }
}
