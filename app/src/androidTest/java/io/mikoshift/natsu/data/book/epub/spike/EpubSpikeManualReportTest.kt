package io.mikoshift.natsu.data.book.epub.spike

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EpubSpikeManualReportTest {

    @Test
    fun logSpikeReports_forBundledAndDownloadedSamples() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val loader = DraftEpubLoader(context)
        val opener = EpubPublicationOpener(context)
        val samples = listOf(
            Sample("minimal-ruby", copyAssetToCache(context.cacheDir, "spike/minimal-ruby.epub")),
        ) + discoverSpikeSamples()

        assertTrue("Expected at least one EPUB sample", samples.isNotEmpty())

        samples.forEach { sample ->
            val publication = opener.open(sample.file)
            val book = loader.loadFromPublication(
                publication = publication,
                bookId = sample.name,
                title = sample.name,
            )
            val report = EpubSpikeAnalyzer.analyzePublication(publication, book)
            Log.i(TAG, "sample=${sample.name}\n${EpubSpikeAnalyzer.formatReport(report)}")
        }
    }

    private fun discoverSpikeSamples(): List<Sample> {
        val candidates = listOf(
            File("/sdcard/Download"),
            File("/storage/emulated/0/Download"),
        )
        val projectSamples = sequenceOf(
            File("spike-samples"),
            File("/data/local/tmp/spike-samples"),
        ).map { directory ->
            if (!directory.isDirectory) emptyList()
            else directory.listFiles { file -> file.extension.equals("epub", ignoreCase = true) }
                ?.map { file -> Sample(file.nameWithoutExtension, file) }
                .orEmpty()
        }

        return candidates.asSequence()
            .flatMap { directory ->
                if (!directory.isDirectory) emptySequence()
                else directory.listFiles()
                    ?.asSequence()
                    ?.filter { it.extension.equals("epub", ignoreCase = true) }
                    ?.map { file -> Sample(file.nameWithoutExtension, file) }
                    .orEmpty()
            }
            .plus(projectSamples.flatten())
            .distinctBy { it.file.absolutePath }
            .toList()
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

    private data class Sample(
        val name: String,
        val file: File,
    )

    companion object {
        private const val TAG = "EpubSpikeReport"
    }
}
