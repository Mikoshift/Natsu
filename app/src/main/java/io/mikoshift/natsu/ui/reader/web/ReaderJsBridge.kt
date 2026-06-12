package io.mikoshift.natsu.ui.reader.web

import android.webkit.JavascriptInterface

class ReaderJsBridge(
    private val onWordTap: (text: String, rangeStart: Int, rangeEnd: Int) -> Unit,
    private val onScrollProgress: (ratio: Float) -> Unit,
    private val onChapterReady: () -> Unit,
) {
    @JavascriptInterface
    fun onWordTap(text: String, rangeStart: Int, rangeEnd: Int) {
        onWordTap.invoke(text, rangeStart, rangeEnd)
    }

    @JavascriptInterface
    fun onScrollProgress(ratio: Double) {
        onScrollProgress.invoke(ratio.toFloat().coerceIn(0f, 1f))
    }

    @JavascriptInterface
    fun onChapterReady() {
        onChapterReady.invoke()
    }
}
