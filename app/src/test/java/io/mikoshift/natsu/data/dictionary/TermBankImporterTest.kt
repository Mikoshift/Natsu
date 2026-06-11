package io.mikoshift.natsu.data.dictionary

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class TermBankImporterTest {

    private val importer = TermBankImporter()

    @Test
    fun importZip_parsesSimpleStringGlossaryEntries() = runBlocking {
        val zipFile = createZip(
            "index.json" to """{"title":"Test Dict","revision":"1"}""",
            "term_bank_1.json" to """
                [
                  ["東京", "とうきょう", "", 0, 100, ["Tokyo"]],
                  ["大阪", "おおさか", "", 0, 90, ["Osaka"]]
                ]
            """.trimIndent(),
        )
        val terms = mutableListOf<TermRecord>()
        var progressCalls = 0

        val index = importer.importZip(
            zipFile = zipFile,
            catalogId = "test",
            onBatch = { terms += it },
            onImportProgress = { processed, total ->
                progressCalls++
                assertEquals(1, total)
                assertEquals(1, processed)
            },
        )

        assertEquals("Test Dict", index.title)
        assertEquals(2, terms.size)
        assertEquals("東京", terms[0].expression)
        assertEquals("とうきょう", terms[0].reading)
        assertEquals(100, terms[0].score)
        assertTrue(terms[0].glossesJson.contains("Tokyo"))
        assertEquals("大阪", terms[1].expression)
        assertEquals(1, progressCalls)
    }

    @Test
    fun importZip_parsesStructuredContentGlossary() = runBlocking {
        val zipFile = createZip(
            "index.json" to """{"title":"Structured Dict","revision":"2"}""",
            "term_bank_1.json" to """
                [
                  ["test", "test", "", 0, 100, [{"type":"text","text":"structured definition"}]]
                ]
            """.trimIndent(),
        )
        val terms = mutableListOf<TermRecord>()

        importer.importZip(
            zipFile = zipFile,
            catalogId = "structured",
            onBatch = { terms += it },
        )

        assertEquals(1, terms.size)
        assertEquals("test", terms[0].expression)
        assertTrue(terms[0].glossesJson.contains("structured definition"))
    }

    private fun createZip(vararg entries: Pair<String, String>): File {
        val file = File.createTempFile("term_bank_test", ".zip")
        file.deleteOnExit()
        ZipOutputStream(file.outputStream()).use { zip ->
            entries.forEach { (name, content) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray())
                zip.closeEntry()
            }
        }
        return file
    }
}
