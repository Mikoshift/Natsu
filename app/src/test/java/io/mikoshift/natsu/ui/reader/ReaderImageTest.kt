package io.mikoshift.natsu.ui.reader

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

class ReaderImageTest {

    @Test
    fun resolveLocalImageFile_allowsFileUnderBookRoot() {
        val root = createTempDir(prefix = "book-").apply { deleteOnExit() }
        val image = File(root, "images/cover.png").apply {
            parentFile?.mkdirs()
            writeBytes(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47))
        }

        val resolved = resolveLocalImageFile(root.absolutePath, "images/cover.png")

        assertNotNull(resolved)
        assert(resolved!!.canonicalPath == image.canonicalPath)
    }

    @Test
    fun resolveLocalImageFile_rejectsParentTraversal() {
        val root = createTempDir(prefix = "book-").apply { deleteOnExit() }
        File(root, "secret.txt").writeText("secret")

        assertNull(resolveLocalImageFile(root.absolutePath, "../secret.txt"))
        assertNull(resolveLocalImageFile(root.absolutePath, "images/../../secret.txt"))
    }

    @Test
    fun resolveLocalImageFile_rejectsPrefixCollisionSiblingDirectory() {
        val booksRoot = createTempDir(prefix = "books-").apply { deleteOnExit() }
        val bookDir = File(booksRoot, "abc").apply { mkdirs() }
        val siblingDir = File(booksRoot, "abc_extra").apply { mkdirs() }
        val siblingFile = File(siblingDir, "leak.png").apply { writeBytes(byteArrayOf(1, 2, 3)) }

        assertNull(resolveLocalImageFile(bookDir.absolutePath, "../abc_extra/leak.png"))

        siblingFile.delete()
        siblingDir.delete()
    }
}
