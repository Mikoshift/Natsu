package io.mikoshift.natsu.ui.reader.web

import android.webkit.WebView
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

    fun injectReaderAssets() {
        val view = webView ?: return
        val themeUrl = ReaderWebUrls.themeStylesheetUrl()
        val bridgeUrl = ReaderWebUrls.bridgeScriptUrl()
        val script = """
            (function() {
              function whenDomReady(fn) {
                if (document.readyState === 'loading') {
                  document.addEventListener('DOMContentLoaded', fn);
                } else {
                  fn();
                }
              }
              function ensureViewport(head) {
                if (!document.querySelector('meta[name="viewport"]')) {
                  var meta = document.createElement('meta');
                  meta.name = 'viewport';
                  meta.content = 'width=device-width, initial-scale=1';
                  head.appendChild(meta);
                }
              }
              function initBridge() {
                if (!window.NatsuReader) return;
                if (window.NatsuReader.init) {
                  window.NatsuReader.init();
                }
              }
              function loadBridge(head) {
                if (window.NatsuReader) {
                  initBridge();
                  return;
                }
                var script = document.createElement('script');
                script.src = '$bridgeUrl';
                script.onload = initBridge;
                head.appendChild(script);
              }
              whenDomReady(function() {
                var head = document.head || document.getElementsByTagName('head')[0];
                if (!head) return;
                ensureViewport(head);
                if (!document.querySelector('link[data-natsu-theme]')) {
                  var link = document.createElement('link');
                  link.rel = 'stylesheet';
                  link.href = '$themeUrl';
                  link.setAttribute('data-natsu-theme', 'true');
                  link.onload = function() { loadBridge(head); };
                  link.onerror = function() { loadBridge(head); };
                  head.appendChild(link);
                } else {
                  loadBridge(head);
                }
              });
            })();
        """.trimIndent()
        view.evaluateJavascript(script, null)
    }

    fun applyTheme(settings: ReaderSettings) {
        pendingTheme = settings
        if (!bridgeReady) return
        applyThemeImmediate(settings)
    }

    fun highlightSearch(ranges: List<IntRange>) {
        val payload = ranges.map { mapOf("start" to it.first, "end" to it.last + 1) }
        evaluate("window.$READER_JS_GLOBAL.highlightSearch(${gson.toJson(payload)})")
    }

    fun injectRuby(tokens: List<FuriganaInjectToken>) {
        if (tokens.isEmpty()) return
        val payload = tokens.map { mapOf("surface" to it.surface, "reading" to it.reading) }
        evaluate("window.$READER_JS_GLOBAL.injectRuby(${gson.toJson(payload)})")
    }

    fun scrollToOffset(charOffset: Int) {
        evaluate("window.$READER_JS_GLOBAL.scrollToOffset($charOffset)")
    }

    private fun applyThemeImmediate(settings: ReaderSettings) {
        val colors = themeColors(settings.theme)
        val payload = mapOf(
            "fontSizePx" to settings.fontSizeSp * 1.333f,
            "lineHeight" to settings.lineSpacingMultiplier,
            "backgroundColor" to colors.first,
            "textColor" to colors.second,
        )
        val script = """
            (function() {
              if (!window.NatsuReader || !window.NatsuReader.applyTheme) return;
              window.NatsuReader.applyTheme(${gson.toJson(payload)});
            })();
        """.trimIndent()
        webView?.setBackgroundColor(android.graphics.Color.parseColor(colors.first))
        evaluate(script)
    }

    private fun evaluate(script: String) {
        webView?.evaluateJavascript(script, null)
    }

    private fun themeColors(theme: ReaderTheme): Pair<String, String> {
        return when (theme) {
            ReaderTheme.LIGHT -> "#FFFFFF" to "#1C1B1F"
            ReaderTheme.DARK -> "#121212" to "#E6E1E5"
            ReaderTheme.SEPIA -> "#F4ECD8" to "#5B4636"
        }
    }

    companion object {
        private const val READER_JS_GLOBAL = ReaderBridgeContract.READER_JS_GLOBAL
    }
}
