package io.mikoshift.natsu.domain.model

enum class ReaderTheme {
    LIGHT,
    DARK,
    SEPIA,
}

data class ReaderSettings(
    val fontSizeSp: Float = 16f,
    val lineSpacingMultiplier: Float = 1.8f,
    val theme: ReaderTheme = ReaderTheme.LIGHT,
)
