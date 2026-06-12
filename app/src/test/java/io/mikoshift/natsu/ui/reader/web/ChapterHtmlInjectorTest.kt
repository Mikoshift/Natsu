package io.mikoshift.natsu.ui.reader.web

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChapterHtmlInjectorTest {

    @Test
    fun inject_addsViewportThemeAndBridgeScript() {
        val html = """
            <html><head><title>Chapter</title></head><body><p>Text</p></body></html>
        """.trimIndent()

        val result = ChapterHtmlInjector.inject(html, isXhtml = false)

        assertTrue(result.contains("""meta name="viewport""""))
        assertTrue(result.contains(ReaderWebUrls.themeStylesheetUrl()))
        assertTrue(result.contains("""data-natsu-theme="true""""))
        assertTrue(result.contains(ReaderWebUrls.bridgeScriptUrl()))
        assertTrue(result.contains("data-natsu-bridge"))
        assertTrue(result.contains("defer"))
    }

    @Test
    fun inject_isIdempotent() {
        val html = """
            <html><head>
            <meta name="viewport" content="width=device-width">
            <link rel="stylesheet" href="${ReaderWebUrls.themeStylesheetUrl()}" data-natsu-theme="true">
            <script src="${ReaderWebUrls.bridgeScriptUrl()}" data-natsu-bridge="true" defer="defer"></script>
            </head><body><p>Text</p></body></html>
        """.trimIndent()

        val result = ChapterHtmlInjector.inject(html, isXhtml = false)

        assertTrue(result.indexOf("data-natsu-theme") == result.lastIndexOf("data-natsu-theme"))
        assertTrue(result.indexOf("data-natsu-bridge") == result.lastIndexOf("data-natsu-bridge"))
    }

    @Test
    fun isInjectable_matchesHtmlFamily() {
        assertTrue(ChapterHtmlInjector.isInjectable("chapter.xhtml"))
        assertTrue(ChapterHtmlInjector.isInjectable("note.html"))
        assertFalse(ChapterHtmlInjector.isInjectable("image.png"))
    }
}
