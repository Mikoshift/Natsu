package io.mikoshift.natsu.data.settings

data class ReaderSettingsSyncMetadata(
    val updatedAtMs: Long = 0L,
    val syncDirty: Boolean = false,
)
