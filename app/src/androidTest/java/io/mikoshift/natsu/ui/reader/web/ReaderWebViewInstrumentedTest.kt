package io.mikoshift.natsu.ui.reader.web

import android.webkit.WebChromeClient
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.mikoshift.natsu.data.book.BookStorage
import io.mikoshift.natsu.data.book.epub.EpubPublicationOpener
import io.mikoshift.natsu.data.book.epub.EpubSpineMapper
import io.mikoshift.natsu.domain.model.reading.BookFormat
import io.mikoshift.natsu.domain.model.reading.BookManifest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class ReaderWebViewInstrumentedTest {

    @Test
    fun loadsEpubChapterInWebView() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        val (bookDir, documentId, chapterPath) = runBlocking {
            setupEpubBookFromAsset(
                appContext = appContext,
                assetPath = EPUB_ASSET_PATH,
            )
        }
        val chapterUrl = ReaderWebUrls.chapterUrl(documentId, chapterPath)
        assertTrue(
            "Chapter file must exist in book package",
            File(bookDir, chapterPath).exists(),
        )

        val latch = CountDownLatch(1)
        val bodyTextLength = AtomicInteger(0)

        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val controller = ReaderWebViewController()
                val assetLoader = BookWebViewAssetLoader(
                    context = appContext,
                    bookDir = bookDir,
                    documentId = documentId,
                )
                val webView = WebView(activity).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    webViewClient = assetLoader.createWebViewClient(
                        onChapterLink = {},
                        onPageFinished = { url ->
                            if (url == "about:blank") return@createWebViewClient
                            controller.injectReaderAssets {}
                        },
                    )
                    webChromeClient = object : WebChromeClient() {
                        override fun onProgressChanged(view: WebView?, newProgress: Int) {
                            if (newProgress < 100 || view == null) return
                            view.post {
                                view.evaluateJavascript(
                                    "(function(){return document.body ? document.body.textContent.trim().length : 0;})();",
                                ) { result ->
                                    bodyTextLength.set(
                                        result?.trim('"')?.toIntOrNull() ?: 0,
                                    )
                                    latch.countDown()
                                }
                            }
                        }
                    }
                }
                controller.attach(webView)
                activity.setContentView(webView)
                webView.loadUrl(chapterUrl)
            }

            assertTrue(
                "WebView did not finish loading chapter within timeout",
                latch.await(30, TimeUnit.SECONDS),
            )
        }

        assertTrue(
            "Expected non-empty chapter body text, got length=${bodyTextLength.get()}",
            bodyTextLength.get() > 0,
        )
    }

    private suspend fun setupEpubBookFromAsset(
        appContext: android.content.Context,
        assetPath: String,
    ): EpubBookSetup {
        val bookStorage = BookStorage(appContext)
        val bookDir = bookStorage.createBookDirectory()
        val bytes = testAssets().open(assetPath).use { it.readBytes() }
        bookStorage.writeBinaryFile(
            bookDir = bookDir,
            relativePath = BookStorage.SOURCE_EPUB_PATH,
            bytes = bytes,
        )
        bookStorage.unzipEpubToBookDir(bookDir, bytes)
        val epubFile = File(bookDir, BookStorage.SOURCE_EPUB_PATH)
        val publication = EpubPublicationOpener(appContext).open(epubFile)
        val sections = EpubSpineMapper.mapSpineToManifestSections(publication)
        check(sections.isNotEmpty()) { "EPUB fixture has no readable spine sections" }
        bookStorage.writeManifest(
            bookDir = bookDir,
            manifest = BookManifest(
                version = 1,
                format = BookFormat.Epub,
                title = "Test EPUB",
                sections = sections,
            ),
        )
        return EpubBookSetup(
            bookDir = bookDir,
            documentId = bookDir.name,
            chapterPath = sections.first().path,
        )
    }

    private fun testAssets() = InstrumentationRegistry.getInstrumentation().context.assets

    private data class EpubBookSetup(
        val bookDir: File,
        val documentId: String,
        val chapterPath: String,
    )

    companion object {
        private const val EPUB_ASSET_PATH = "spike/minimal-ruby.epub"
    }
}
