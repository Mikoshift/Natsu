package io.mikoshift.natsu.data.book.epub.spike

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.mikoshift.natsu.data.book.load.LoaderContract
import io.mikoshift.natsu.domain.model.reading.ReadingBlock
import io.mikoshift.natsu.domain.model.reading.TextSpan
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EpubSpikeInstrumentedTest {

    @Test
    fun draftEpubLoader_minimalRubyFixture_satisfiesContract() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val epubFile = copyAssetToCache(context.cacheDir, "spike/minimal-ruby.epub")
        val book = DraftEpubLoader(context).load(
            epubFile = epubFile,
            bookId = "minimal-ruby",
        )

        LoaderContract.verify(
            book = book,
            searchQueries = listOf("第一章", "猫", "漢字"),
        )

        val paragraph = book.sections.single().blocks
            .filterIsInstance<ReadingBlock.Paragraph>()
            .first()
        assertEquals(TextSpan(text = "漢字", reading = "かんじ"), paragraph.spans.first())
        assertTrue(book.sections.single().blocks.any { it is ReadingBlock.Image })
    }

    private fun copyAssetToCache(cacheDir: File, assetPath: String): File {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val output = File(cacheDir, assetPath.substringAfterLast('/'))
        context.assets.open(assetPath).use { input ->
            output.outputStream().use { outputStream ->
                input.copyTo(outputStream)
            }
        }
        return output
    }
}
