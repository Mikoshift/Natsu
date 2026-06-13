package io.mikoshift.natsu.data.sync

import io.mikoshift.natsu.data.remote.dto.DocumentDto
import io.mikoshift.natsu.domain.model.Document
import io.mikoshift.natsu.domain.model.reading.ReadingLocator

object SyncDocumentMapper {
    fun toDto(document: Document): DocumentDto = DocumentDto(
        id = document.id,
        title = document.title,
        source_format = document.sourceFormat.manifestValue,
        imported_at = document.importedAt,
        char_count = document.charCount,
        last_read_char_offset = document.lastReadCharOffset,
        last_read_paragraph_index = document.lastReadParagraphIndex,
        last_read_section_id = document.lastReadLocator?.sectionId,
        last_read_block_index = document.lastReadLocator?.blockIndex ?: 0,
        last_read_block_char_offset = document.lastReadLocator?.charOffset ?: 0,
        updated_at_ms = document.updatedAtMs,
        deleted = document.deleted,
    )

    fun toLocal(
        dto: DocumentDto,
        existing: Document?,
    ): Document {
        val locator = dto.last_read_section_id?.takeIf { it.isNotBlank() }?.let {
            ReadingLocator(
                sectionId = it,
                blockIndex = dto.last_read_block_index,
                charOffset = dto.last_read_block_char_offset,
            )
        }
        return Document(
            id = dto.id,
            title = dto.title,
            storagePath = existing?.storagePath ?: dto.id,
            sourceFormat = io.mikoshift.natsu.domain.model.reading.BookFormat.fromManifestValue(
                dto.source_format,
            ),
            importedAt = dto.imported_at,
            charCount = dto.char_count,
            lastReadCharOffset = dto.last_read_char_offset,
            lastReadParagraphIndex = dto.last_read_paragraph_index,
            lastReadLocator = locator,
            updatedAtMs = dto.updated_at_ms,
            syncDirty = false,
            deleted = dto.deleted,
            packageSha256 = existing?.packageSha256,
        )
    }

    fun shouldApplyRemote(local: Document?, remote: DocumentDto): Boolean {
        if (local == null) {
            return !remote.deleted
        }
        return remote.updated_at_ms >= local.updatedAtMs
    }
}
