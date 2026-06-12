package io.mikoshift.natsu.data.book

import android.content.Context
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import io.mikoshift.natsu.domain.model.reading.BookFormat
import io.mikoshift.natsu.domain.model.reading.BookManifest
import io.mikoshift.natsu.domain.model.reading.ManifestSection
import io.mikoshift.natsu.domain.model.reading.SearchIndex
import io.mikoshift.natsu.domain.model.reading.SearchIndexParagraph
import io.mikoshift.natsu.domain.model.reading.SectionCharOffset
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.UUID

class BookStorage private constructor(
    private val booksRoot: File,
    private val gson: Gson,
) {
    constructor(
        context: Context,
        gson: Gson = GsonBuilder().create(),
    ) : this(
        booksRoot = File(context.filesDir, "books").apply { mkdirs() },
        gson = gson,
    )

    internal constructor(booksRoot: File) : this(
        booksRoot = booksRoot.apply { mkdirs() },
        gson = GsonBuilder().create(),
    )

    fun createBookDirectory(): File {
        val bookDir = File(booksRoot, UUID.randomUUID().toString())
        bookDir.mkdirs()
        return bookDir
    }

    fun bookDirectory(id: String): File = File(booksRoot, id)

    fun validatedBookDirectory(documentId: String, storagePath: String): File {
        val bookDir = bookDirectory(documentId)
        BookPathResolver.requireUnderBooksRoot(booksRoot, storagePath)
        require(bookDir.canonicalFile == File(storagePath).canonicalFile) {
            "Storage path does not match document id: $documentId"
        }
        return bookDir
    }

    fun writeManifest(bookDir: File, manifest: BookManifest) {
        val manifestFile = File(bookDir, MANIFEST_FILE_NAME)
        manifestFile.writeText(gson.toJson(manifest.toJsonModel()), StandardCharsets.UTF_8)
    }

    fun readManifest(bookDir: File): BookManifest {
        val manifestFile = File(bookDir, MANIFEST_FILE_NAME)
        require(manifestFile.exists()) { "manifest.json not found in ${bookDir.absolutePath}" }
        val json = manifestFile.readText(StandardCharsets.UTF_8)
        return gson.fromJson(json, ManifestJson::class.java).toDomain(bookDir)
    }

    fun writeContentFile(bookDir: File, relativePath: String, content: String) {
        val bytes = content.toByteArray(StandardCharsets.UTF_8)
        require(bytes.size <= MAX_CONTENT_BYTES) {
            "Content exceeds maximum size of $MAX_CONTENT_BYTES bytes"
        }
        val contentFile = BookPathResolver.resolveRelativePath(bookDir, relativePath)
        contentFile.parentFile?.mkdirs()
        contentFile.writeText(content, StandardCharsets.UTF_8)
    }

    fun writeSearchIndex(bookDir: File, searchIndex: SearchIndex) {
        val indexFile = File(bookDir, SEARCH_INDEX_FILE_NAME)
        indexFile.writeText(gson.toJson(searchIndex.toJsonModel()), StandardCharsets.UTF_8)
    }

    fun readSearchIndex(bookDir: File): SearchIndex? {
        val indexFile = File(bookDir, SEARCH_INDEX_FILE_NAME)
        if (!indexFile.exists()) return null
        val json = indexFile.readText(StandardCharsets.UTF_8)
        return gson.fromJson(json, SearchIndexJson::class.java)?.toDomain()
    }

    fun deleteBookPackage(storagePath: String) {
        val bookDir = BookPathResolver.requireUnderBooksRoot(booksRoot, storagePath)
        bookDir.deleteRecursively()
    }

    companion object {
        const val MANIFEST_FILE_NAME = "manifest.json"
        const val SEARCH_INDEX_FILE_NAME = "search_index.json"
        const val PLAIN_TEXT_CONTENT_PATH = "content.txt"
        const val MARKDOWN_CONTENT_PATH = "content.md"
        const val MAX_CONTENT_BYTES = 50 * 1024 * 1024
    }
}

private data class ManifestJson(
    val version: Int,
    val format: String,
    val title: String,
    val sections: List<ManifestSectionJson>,
)

private data class ManifestSectionJson(
    val id: String,
    val title: String?,
    val path: String,
)

private fun BookManifest.toJsonModel(): ManifestJson =
    ManifestJson(
        version = version,
        format = format.manifestValue,
        title = title,
        sections = sections.map { section ->
            ManifestSectionJson(
                id = section.id,
                title = section.title,
                path = section.path,
            )
        },
    )

private data class SearchIndexJson(
    val version: Int,
    val totalCharCount: Int,
    val sectionOffsets: List<SectionCharOffsetJson>,
    val paragraphs: List<SearchIndexParagraphJson>,
)

private data class SectionCharOffsetJson(
    val sectionId: String,
    val globalCharOffset: Int,
    val charCount: Int,
)

private data class SearchIndexParagraphJson(
    val sectionId: String,
    val blockIndex: Int,
    val globalCharOffset: Int,
    val text: String,
)

private fun SearchIndex.toJsonModel(): SearchIndexJson =
    SearchIndexJson(
        version = version,
        totalCharCount = totalCharCount,
        sectionOffsets = sectionOffsets.map { offset ->
            SectionCharOffsetJson(
                sectionId = offset.sectionId,
                globalCharOffset = offset.globalCharOffset,
                charCount = offset.charCount,
            )
        },
        paragraphs = paragraphs.map { paragraph ->
            SearchIndexParagraphJson(
                sectionId = paragraph.sectionId,
                blockIndex = paragraph.blockIndex,
                globalCharOffset = paragraph.globalCharOffset,
                text = paragraph.text,
            )
        },
    )

private fun SearchIndexJson.toDomain(): SearchIndex =
    SearchIndex(
        version = version,
        totalCharCount = totalCharCount,
        sectionOffsets = sectionOffsets.map { offset ->
            SectionCharOffset(
                sectionId = offset.sectionId,
                globalCharOffset = offset.globalCharOffset,
                charCount = offset.charCount,
            )
        },
        paragraphs = paragraphs.map { paragraph ->
            SearchIndexParagraph(
                sectionId = paragraph.sectionId,
                blockIndex = paragraph.blockIndex,
                globalCharOffset = paragraph.globalCharOffset,
                text = paragraph.text,
            )
        },
    )

private fun ManifestJson.toDomain(bookDir: File): BookManifest =
    BookManifest(
        version = version,
        format = BookFormat.fromManifestValue(format),
        title = title,
        sections = sections.map { section ->
            BookPathResolver.resolveRelativePath(bookDir, section.path)
            ManifestSection(
                id = section.id,
                title = section.title,
                path = section.path,
            )
        },
    )
