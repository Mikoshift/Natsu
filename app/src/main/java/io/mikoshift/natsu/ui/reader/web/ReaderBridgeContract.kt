package io.mikoshift.natsu.ui.reader.web

/**
 * Contract between [bridge.js] (WebView) and Kotlin ([ReaderJsBridge], [ReaderWebViewController]).
 *
 * ## JS -> Kotlin ([ReaderJsBridge])
 * - [ReaderJsBridge.onWordTap] — paragraph text + char offset; Kotlin picks token and opens dictionary.
 * - [ReaderJsBridge.onScrollProgress] — chapter scroll ratio in `[0, 1]` for progress save.
 * - [ReaderJsBridge.onChapterReady] — chapter DOM loaded; Kotlin applies theme, furigana, scroll.
 *
 * ## Kotlin -> JS (`window.NatsuReader.*` via [ReaderWebViewController])
 * - `NatsuReader.init()` — install listeners; called after bridge.js loads.
 * - `NatsuReader.applyTheme(vars)` — CSS variables: fontSizePx, lineHeight, backgroundColor, textColor.
 * - `NatsuReader.highlightSearch(ranges)` — `[{start, end}]` section-local char offsets.
 * - `NatsuReader.injectRuby(tokens)` — `[{surface, reading}]` for Kuromoji furigana.
 * - `NatsuReader.scrollToOffset(charOffset)` — scroll to section-local char offset.
 *
 * Kuromoji tokenization stays in Kotlin. JS only handles DOM extraction and decoration.
 */
object ReaderBridgeContract {
    const val JS_INTERFACE_NAME = "NatsuBridge"
    const val READER_JS_GLOBAL = "NatsuReader"
    const val ASSET_HOST = "appassets.androidplatform.net"
    const val BOOKS_URL_PREFIX = "https://$ASSET_HOST/books/"
    const val READER_ASSETS_URL_PREFIX = "https://$ASSET_HOST/reader/"
    const val BRIDGE_ASSET_PATH = "reader/bridge.js"
    const val THEME_ASSET_PATH = "reader/theme.css"
}
