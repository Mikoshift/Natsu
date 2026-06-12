package io.mikoshift.natsu.data.book

import java.io.File
import java.nio.file.Path

object BookPathResolver {
    fun resolveRelativePath(root: File, relativePath: String): File {
        require(relativePath.isNotBlank()) { "Relative path must not be blank" }
        require(!isAbsolutePath(relativePath)) {
            "Absolute paths are not allowed: $relativePath"
        }
        require(!containsParentReference(relativePath)) {
            "Path traversal is not allowed: $relativePath"
        }

        val rootPath = root.canonicalFile.toPath()
        val resolved = rootPath.resolve(relativePath).normalize()
        require(isUnderRoot(resolved, rootPath)) {
            "Path escapes root: $relativePath"
        }
        return resolved.toFile()
    }

    fun requireUnderBooksRoot(booksRoot: File, path: String): File {
        val rootPath = booksRoot.canonicalFile.toPath()
        val resolved = File(path).canonicalFile.toPath()
        if (!isUnderRoot(resolved, rootPath)) {
            throw SecurityException("Path is not under books root: $path")
        }
        return resolved.toFile()
    }

    private fun isUnderRoot(resolved: Path, root: Path): Boolean {
        return resolved.startsWith(root.resolve(""))
    }

    private fun isAbsolutePath(relativePath: String): Boolean {
        return relativePath.startsWith("/") ||
            relativePath.startsWith("\\") ||
            relativePath.matches(Regex("^[A-Za-z]:[/\\\\].*"))
    }

    private fun containsParentReference(relativePath: String): Boolean {
        return relativePath.split('/', '\\').any { it == ".." }
    }
}
