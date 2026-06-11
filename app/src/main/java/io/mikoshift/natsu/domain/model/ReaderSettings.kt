package io.mikoshift.natsu.domain.model

enum class ReaderTheme {
    LIGHT,
    DARK,
    SEPIA,
}

enum class FuriganaMode {
    OFF,
    ALWAYS,
}

data class ReaderSettings(
    val fontSizeSp: Float = 16f,
    val lineSpacingMultiplier: Float = 1.8f,
    val theme: ReaderTheme = ReaderTheme.LIGHT,
    val furiganaMode: FuriganaMode = FuriganaMode.OFF,
)
