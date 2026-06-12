package io.mikoshift.natsu.ui.reader.web

import android.webkit.WebView
import androidx.webkit.WebMessageCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.google.gson.Gson
import io.mikoshift.natsu.domain.model.ReaderSettings
import io.mikoshift.natsu.domain.model.ReaderTheme
import io.mikoshift.natsu.ui.reader.FuriganaInjectToken

class ReaderWebViewController {
    var webView: WebView? = null
        private set

    private val gson = Gson()
    private var pendingTheme: ReaderSettings? = null
    private var bridgeReady = false

    fun attach(webView: WebView) {
        this.webView = webView
        if (bridgeReady) {
            pendingTheme?.let { applyThemeImmediate(it) }
        }
    }

    fun detach() {
        webView = null
        bridgeReady = false
    }

    fun loadChapter(url: String) {
        bridgeReady = false
        webView?.loadUrl(url)
    }

    fun onBridgeReady() {
        bridgeReady = true
        pendingTheme?.let { applyThemeImmediate(it) }
    }

    fun applyTheme(settings: ReaderSettings) {
        pendingTheme = settings
        if (!bridgeReady) return
        applyThemeImmediate(settings)
    }

    fun highlightSearch(ranges: List<IntRange>) {
        val payload = ranges.map { mapOf("start" to it.first, "end" to it.last + 1) }
        postReaderCall("highlightSearch", payload)
    }

    fun injectRuby(tokens: List<FuriganaInjectToken>) {
        if (tokens.isEmpty()) return
        val payload = tokens.map {
            mapOf(
                "surface" to it.surface,
                "reading" to it.reading,
                "start" to it.start,
                "end" to it.end,
            )
        }
        postReaderCall("injectRuby", payload)
    }

    fun scrollToOffset(charOffset: Int) {
        postReaderCall("scrollToOffset", charOffset)
    }

    private fun applyThemeImmediate(settings: ReaderSettings) {
        val colors = themeColors(settings.theme)
        val payload = mapOf(
            "fontSizePx" to settings.fontSizeSp * 1.333f,
            "lineHeight" to settings.lineSpacingMultiplier,
            "backgroundColor" to colors.first,
            "textColor" to colors.second,
        )
        webView?.setBackgroundColor(android.graphics.Color.parseColor(colors.first))
        postReaderCall("applyTheme", payload)
    }

    private fun postReaderCall(method: String, vararg args: Any?) {
        val view = webView ?: return
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.POST_WEB_MESSAGE)) {
            return
        }
        val envelope = mapOf(
            "type" to ReaderBridgeContract.WEB_MESSAGE_TYPE,
            "method" to method,
            "args" to args.toList(),
        )
        WebViewCompat.postWebMessage(
            view,
            WebMessageCompat(gson.toJson(envelope)),
            ReaderWebUrls.webViewOrigin(),
        )
    }

    private fun themeColors(theme: ReaderTheme): Pair<String, String> {
        return when (theme) {
            ReaderTheme.LIGHT -> "#FFFFFF" to "#1C1B1F"
            ReaderTheme.DARK -> "#121212" to "#E6E1E5"
            ReaderTheme.SEPIA -> "#F4ECD8" to "#5B4636"
        }
    }
}
