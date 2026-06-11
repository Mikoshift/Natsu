package io.mikoshift.natsu.domain.model

data class DictionaryCatalogItem(
    val id: String,
    val name: String,
    val description: String,
    val downloadUrl: String,
    val sizeHintMb: Int,
)
