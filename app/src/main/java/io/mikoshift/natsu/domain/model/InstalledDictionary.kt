package io.mikoshift.natsu.domain.model

enum class DictionaryInstallState {
    NotInstalled,
    Downloading,
    Installed,
}

data class InstalledDictionary(
    val catalogId: String,
    val name: String,
    val description: String,
    val sizeHintMb: Int,
    val title: String?,
    val enabled: Boolean,
    val priority: Int,
    val installState: DictionaryInstallState,
    val downloadProgress: Float,
)
