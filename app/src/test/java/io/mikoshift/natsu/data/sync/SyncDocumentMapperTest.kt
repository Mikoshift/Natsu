package io.mikoshift.natsu.data.sync

import io.mikoshift.natsu.data.remote.dto.DocumentDto
import io.mikoshift.natsu.domain.model.Document
import io.mikoshift.natsu.domain.model.reading.BookFormat
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncDocumentMapperTest {
    @Test
    fun shouldApplyRemote_prefersNewerTimestamp() {
        val local = sampleDocument(updatedAtMs = 2_000L)
        val remote = sampleDto(updatedAtMs = 3_000L)

        assertTrue(SyncDocumentMapper.shouldApplyRemote(local, remote))
    }

    @Test
    fun shouldApplyRemote_rejectsOlderTimestamp() {
        val local = sampleDocument(updatedAtMs = 5_000L)
        val remote = sampleDto(updatedAtMs = 1_000L)

        assertFalse(SyncDocumentMapper.shouldApplyRemote(local, remote))
    }

    @Test
    fun shouldApplyRemote_ignoresDeletedWhenLocalMissing() {
        val remote = sampleDto(updatedAtMs = 1_000L, deleted = true)

        assertFalse(SyncDocumentMapper.shouldApplyRemote(local = null, remote = remote))
    }

    private fun sampleDocument(updatedAtMs: Long): Document = Document(
        id = "550e8400-e29b-41d4-a716-446655440000",
        title = "Local",
        storagePath = "550e8400-e29b-41d4-a716-446655440000",
        sourceFormat = BookFormat.Epub,
        importedAt = 1_000L,
        charCount = 10,
        lastReadCharOffset = 0,
        lastReadParagraphIndex = 0,
        updatedAtMs = updatedAtMs,
    )

    private fun sampleDto(
        updatedAtMs: Long,
        deleted: Boolean = false,
    ): DocumentDto = DocumentDto(
        id = "550e8400-e29b-41d4-a716-446655440000",
        title = "Remote",
        source_format = "epub",
        imported_at = 1_000L,
        char_count = 10,
        last_read_char_offset = 0,
        last_read_paragraph_index = 0,
        last_read_section_id = null,
        last_read_block_index = 0,
        last_read_block_char_offset = 0,
        updated_at_ms = updatedAtMs,
        deleted = deleted,
    )
}
