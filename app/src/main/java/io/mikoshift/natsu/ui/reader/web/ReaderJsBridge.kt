package io.mikoshift.natsu.ui.reader.web

import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface

class ReaderJsBridge(
    private val onWordTap: (paragraphText: String, charOffset: Int) -> Unit,
    private val onScrollProgress: (ratio: Float) -> Unit,
    private val onBridgeReady: () -> Unit,
    private val onChapterReady: () -> Unit,
) {
    private val mainHandler = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun onWordTap(text: String, charOffset: Int) {
        mainHandler.post {
            onWordTap.invoke(text, charOffset)
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
