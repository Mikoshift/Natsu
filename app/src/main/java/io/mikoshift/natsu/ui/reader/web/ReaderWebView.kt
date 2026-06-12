package io.mikoshift.natsu.ui.reader.web

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import io.mikoshift.natsu.domain.model.FuriganaMode
import io.mikoshift.natsu.domain.model.ReaderSettings
import io.mikoshift.natsu.ui.reader.FuriganaInjectToken
import io.mikoshift.natsu.ui.reader.TapHighlightRequest
import java.io.File

@SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
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
    layoutParagraphs: List<String>,
    tapHighlightRequest: TapHighlightRequest?,
    tapHighlightRequestId: Long,
    controller: ReaderWebViewController,
    onWordTap: (paragraphIndex: Int, charOffset: Int, paragraphText: String) -> Unit,
    onScrollProgress: (ratio: Float) -> Unit,
    onChapterReady: () -> Unit,
    onChapterLink: (relativePath: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val currentOnWordTap by rememberUpdatedState(onWordTap)
    val currentOnScrollProgress by rememberUpdatedState(onScrollProgress)
    val currentOnChapterReady by rememberUpdatedState(onChapterReady)
    val assetLoader = remember(bookDir, documentId) {
        BookWebViewAssetLoader(
            context = context.applicationContext,
            bookDir = bookDir,
            documentId = documentId,
        )
    }
    val jsBridge = remember(controller) {
        ReaderJsBridge(
            onWordTap = { paragraphIndex, charOffset, paragraphText ->
                currentOnWordTap(paragraphIndex, charOffset, paragraphText)
            },
            onScrollProgress = { ratio -> currentOnScrollProgress(ratio) },
            onBridgeReady = { controller.onBridgeReady() },
            onChapterReady = { currentOnChapterReady() },
        )
    }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner, controller) {
        val observer = LifecycleEventObserver { _, event ->
            val webView = controller.webView ?: return@LifecycleEventObserver
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    webView.onPause()
                    webView.pauseTimers()
                }
                Lifecycle.Event.ON_RESUME -> {
                    webView.onResume()
                    webView.resumeTimers()
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
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
                settings.useWideViewPort = false
                settings.loadWithOverviewMode = false
                settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                if (WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK)) {
                    WebSettingsCompat.setForceDark(settings, WebSettingsCompat.FORCE_DARK_OFF)
                }
                addJavascriptInterface(jsBridge, ReaderBridgeContract.JS_INTERFACE_NAME)
                setOnLongClickListener { true }
                webViewClient = assetLoader.createWebViewClient(
                    onChapterLink = onChapterLink,
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

    LaunchedEffect(readerSettings) {
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

    LaunchedEffect(layoutParagraphs, chapterUrl) {
        if (chapterUrl == null) return@LaunchedEffect
        controller.tagParagraphs(layoutParagraphs)
    }

    LaunchedEffect(tapHighlightRequestId, chapterUrl) {
        val request = tapHighlightRequest ?: return@LaunchedEffect
        if (chapterUrl == null) return@LaunchedEffect
        controller.highlightTapToken(request.paragraphIndex, request.start, request.end)
    }

    LaunchedEffect(furiganaTokens, readerSettings.furiganaMode, chapterUrl) {
        if (chapterUrl == null) return@LaunchedEffect
        if (readerSettings.furiganaMode == FuriganaMode.OFF) return@LaunchedEffect
        controller.injectRuby(furiganaTokens)
    }
}
