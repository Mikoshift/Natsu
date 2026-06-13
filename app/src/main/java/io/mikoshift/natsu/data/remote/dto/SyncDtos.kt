package io.mikoshift.natsu.data.remote.dto

data class DocumentDto(
    val id: String,
    val title: String,
    val source_format: String,
    val imported_at: Long,
    val char_count: Int,
    val last_read_char_offset: Int,
    val last_read_paragraph_index: Int,
    val last_read_section_id: String?,
    val last_read_block_index: Int,
    val last_read_block_char_offset: Int,
    val updated_at_ms: Long,
    val deleted: Boolean,
    val has_package: Boolean = false,
    val package_sha256: String? = null,
    val package_updated_at_ms: Long = 0,
)

data class DocumentsResponseDto(
    val documents: List<DocumentDto>,
    val server_time_ms: Long,
)

data class SyncDocumentsRequestDto(
    val documents: List<DocumentDto>,
)

data class ReaderSettingsDto(
    val font_size_sp: Float,
    val line_spacing_multiplier: Float,
    val theme: String,
    val furigana_mode: String,
    val updated_at_ms: Long,
)

data class ReaderSettingsResponseDto(
    val settings: ReaderSettingsDto,
    val server_time_ms: Long,
)

data class UpdateReaderSettingsRequestDto(
    val font_size_sp: Float,
    val line_spacing_multiplier: Float,
    val theme: String,
    val furigana_mode: String,
    val updated_at_ms: Long,
)
