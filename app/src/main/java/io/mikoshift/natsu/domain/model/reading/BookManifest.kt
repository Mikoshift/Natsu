package io.mikoshift.natsu.domain.model.reading

data class BookManifest(
    val version: Int,
    val format: BookFormat,
    val title: String,
    val sections: List<ManifestSection>,
)

data class ManifestSection(
    val id: String,
    val title: String?,
    val path: String,
)
