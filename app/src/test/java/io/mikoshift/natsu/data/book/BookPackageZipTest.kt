package io.mikoshift.natsu.data.book

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class BookPackageZipTest {
    @Test
    fun zipAndUnzip_roundTripPreservesManifest() {
        val root = File(
            System.getProperty("java.io.tmpdir"),
            "natsu_pkg_${System.nanoTime()}",
        ).apply { mkdirs() }
        val sourceDir = File(root, "source").apply { mkdirs() }
        File(sourceDir, BookStorage.MANIFEST_FILE_NAME).writeText(
            """
            {
              "version": 2,
              "format": "plain_text",
              "title": "Test",
              "sections": [{"id": "main", "title": null, "path": "content.html"}]
            }
            """.trimIndent(),
        )
        File(sourceDir, "content.html").writeText("<html><body><p>Hello</p></body></html>")

        val zipFile = BookPackageZip.zipBookDir(sourceDir)
        val targetDir = File(root, "target")
        BookPackageZip.unzipToBookDir(zipFile, targetDir)

        assertTrue(BookPackageZip.hasManifest(targetDir))
        assertEquals(
            "Test",
            Regex(""""title"\s*:\s*"([^"]+)"""")
                .find(File(targetDir, BookStorage.MANIFEST_FILE_NAME).readText())
                ?.groupValues
                ?.get(1),
        )

        zipFile.delete()
        root.deleteRecursively()
    }
}
