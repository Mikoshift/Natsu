package io.mikoshift.natsu.ui.reader.web

/**
 * Contract between reader JS ([bridge.js]) and Kotlin ([ReaderJsBridge], [ReaderWebViewController]).
 *
 * ## JS -> Kotlin ([ReaderJsBridge])
 * - [ReaderJsBridge.onWordTap] — paragraph layout text + char offset; Kotlin picks the Kuromoji token at that position.
 * - [ReaderJsBridge.onScrollProgress] — chapter scroll ratio in `[0, 1]` for progress save.
 * - [ReaderJsBridge.onBridgeReady] — bridge.js loaded and `init()` ran; Kotlin may apply pending theme.
 * - [ReaderJsBridge.onChapterReady] — chapter DOM ready; Kotlin applies theme, furigana, scroll.
 *
 * ## Kotlin -> JS ([ReaderWebViewController] via [WebViewCompat.postWebMessage])
 * Chapter HTML is served with theme.css and bridge.js injected by [ChapterHtmlInjector].
 * Commands use [WEB_MESSAGE_TYPE] envelopes: `{ type, method, args }`.
 *
 * - `NatsuReader.init()` — install listeners; runs after bridge.js loads.
 * - `NatsuReader.applyTheme(vars)` — CSS variables: fontSizePx, lineHeight, backgroundColor, textColor.
 * - `NatsuReader.highlightSearch(ranges)` — `[{start, end}]` section-local char offsets.
 * - `NatsuReader.injectRuby(tokens)` — `[{surface, reading, start, end}]` section-local layout offsets.
 * - `NatsuReader.scrollToOffset(charOffset)` — scroll to section-local char offset.
 *
 * Kuromoji tokenization stays in Kotlin. JS only handles DOM extraction and decoration.
 */
object ReaderBridgeContract {
    const val JS_INTERFACE_NAME = "NatsuBridge"
    const val READER_JS_GLOBAL = "NatsuReader"
    const val WEB_MESSAGE_TYPE = "natsu-reader-call"
    const val ASSET_HOST = "appassets.androidplatform.net"
    const val BOOKS_URL_PREFIX = "https://$ASSET_HOST/books/"
    const val READER_ASSETS_URL_PREFIX = "https://$ASSET_HOST/reader/"
    const val BRIDGE_ASSET_PATH = "bridge.js"
    const val THEME_ASSET_PATH = "theme.css"
}
