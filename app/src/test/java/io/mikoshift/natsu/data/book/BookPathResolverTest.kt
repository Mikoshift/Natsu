package io.mikoshift.natsu.data.book

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.File

class BookPathResolverTest {

    @Test
    fun resolveRelativePath_allowsValidRelativePath() {
        val root = createTempDirectory("books-root")

        val resolved = BookPathResolver.resolveRelativePath(root, "content.txt")

        assertEquals(File(root, "content.txt").canonicalFile, resolved)
    }

    @Test
    fun resolveRelativePath_allowsNestedRelativePath() {
        val root = createTempDirectory("books-root")

        val resolved = BookPathResolver.resolveRelativePath(root, "chapters/01/content.txt")

        assertEquals(File(root, "chapters/01/content.txt").canonicalFile, resolved)
    }

    @Test
    fun resolveRelativePath_rejectsParentTraversal() {
        val root = createTempDirectory("books-root")

        assertThrows(IllegalArgumentException::class.java) {
            BookPathResolver.resolveRelativePath(root, "../outside.txt")
        }
    }

    @Test
    fun resolveRelativePath_rejectsEmbeddedParentTraversal() {
        val root = createTempDirectory("books-root")

        assertThrows(IllegalArgumentException::class.java) {
            BookPathResolver.resolveRelativePath(root, "chapters/../outside.txt")
        }
    }

    @Test
    fun resolveRelativePath_rejectsAbsolutePath() {
        val root = createTempDirectory("books-root")

        assertThrows(IllegalArgumentException::class.java) {
            BookPathResolver.resolveRelativePath(root, "/etc/passwd")
        }
    }

    @Test
    fun resolveRelativePath_rejectsPrefixAttackSiblingDirectory() {
        val booksRoot = createTempDirectory("books-root")
        val sibling = File(booksRoot.parentFile, "${booksRoot.name}-evil").apply { mkdirs() }
        val target = File(sibling, "secret.txt").apply { writeText("secret") }

        assertThrows(IllegalArgumentException::class.java) {
            BookPathResolver.resolveRelativePath(booksRoot, "../${sibling.name}/secret.txt")
        }
        assertEquals("secret", target.readText())
    }

    @Test
    fun requireUnderBooksRoot_allowsPathInsideRoot() {
        val booksRoot = createTempDirectory("books-root")
        val bookDir = File(booksRoot, "book-1").apply { mkdirs() }

        val resolved = BookPathResolver.requireUnderBooksRoot(booksRoot, bookDir.absolutePath)

        assertEquals(bookDir.canonicalFile, resolved)
    }

    @Test
    fun requireUnderBooksRoot_rejectsPathOutsideRoot() {
        val booksRoot = createTempDirectory("books-root")
        val outside = createTempDirectory("outside-root")

        assertThrows(SecurityException::class.java) {
            BookPathResolver.requireUnderBooksRoot(booksRoot, outside.absolutePath)
        }
    }

    @Test
    fun requireUnderBooksRoot_rejectsPrefixAttackSiblingDirectory() {
        val booksRoot = createTempDirectory("books-root")
        val sibling = File(booksRoot.parentFile, "${booksRoot.name}-evil").apply { mkdirs() }

        assertThrows(SecurityException::class.java) {
            BookPathResolver.requireUnderBooksRoot(booksRoot, sibling.absolutePath)
        }
    }

    private fun createTempDirectory(prefix: String): File {
        val dir = File.createTempFile(prefix, null)
        check(dir.delete()) { "Failed to delete temp file" }
        check(dir.mkdirs()) { "Failed to create temp directory" }
        return dir
    }
}
