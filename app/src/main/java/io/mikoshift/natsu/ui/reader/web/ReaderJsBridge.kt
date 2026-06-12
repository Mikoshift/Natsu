package io.mikoshift.natsu.ui.reader.web

import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface

class ReaderJsBridge(
    private val onWordTap: (paragraphIndex: Int, charOffset: Int, paragraphText: String) -> Unit,
    private val onScrollProgress: (ratio: Float) -> Unit,
    private val onBridgeReady: () -> Unit,
    private val onChapterReady: () -> Unit,
) {
    private val mainHandler = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun onWordTap(paragraphIndex: Int, charOffset: Int, paragraphText: String) {
        mainHandler.post {
            onWordTap.invoke(paragraphIndex, charOffset, paragraphText)
        }
    }

    @JavascriptInterface
    fun onScrollProgress(ratio: Double) {
        mainHandler.post {
            onScrollProgress.invoke(ratio.toFloat().coerceIn(0f, 1f))
        }
    }

    @JavascriptInterface
    fun onBridgeReady() {
        mainHandler.post {
            onBridgeReady.invoke()
        }
    }

    @JavascriptInterface
    fun onChapterReady() {
        mainHandler.post {
            onChapterReady.invoke()
        }
    }
}
