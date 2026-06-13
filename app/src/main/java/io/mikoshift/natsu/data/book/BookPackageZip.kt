package io.mikoshift.natsu.data.book

import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object BookPackageZip {
    fun zipBookDir(bookDir: File): File {
        require(bookDir.isDirectory) { "Book directory does not exist: ${bookDir.absolutePath}" }

        val zipFile = File.createTempFile("natsu_pkg_", ".zip")
        ZipOutputStream(FileOutputStream(zipFile)).use { zipOut ->
            bookDir.walkTopDown()
                .filter { it.isFile }
                .forEach { file ->
                    val relative = file.relativeTo(bookDir).path.replace('\\', '/')
                    zipOut.putNextEntry(ZipEntry(relative))
                    FileInputStream(file).use { input ->
                        input.copyTo(zipOut)
                    }
                    zipOut.closeEntry()
                }
        }
        return zipFile
    }

    fun unzipToBookDir(zipFile: File, bookDir: File) {
        require(zipFile.isFile) { "Package zip does not exist: ${zipFile.absolutePath}" }
        if (bookDir.exists()) {
            bookDir.deleteRecursively()
        }
        bookDir.mkdirs()

        ZipInputStream(BufferedInputStream(FileInputStream(zipFile))).use { zipIn ->
            var entry = zipIn.nextEntry
            while (entry != null) {
                val normalized = entry.name.replace('\\', '/').removePrefix("/")
                if (normalized.isNotBlank() && !normalized.contains("..")) {
                    val target = File(bookDir, normalized)
                    if (entry.isDirectory) {
                        target.mkdirs()
                    } else {
                        target.parentFile?.mkdirs()
                        FileOutputStream(target).use { output ->
                            zipIn.copyTo(output)
                        }
                    }
                }
                zipIn.closeEntry()
                entry = zipIn.nextEntry
            }
        }
    }

    fun hasManifest(bookDir: File): Boolean {
        return File(bookDir, BookStorage.MANIFEST_FILE_NAME).exists()
    }
}
