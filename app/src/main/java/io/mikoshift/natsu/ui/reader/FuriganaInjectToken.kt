package io.mikoshift.natsu.ui.reader

data class FuriganaInjectToken(
    val surface: String,
    val reading: String,
    /** Section-local layout-text start offset (inclusive). */
    val start: Int,
    /** Section-local layout-text end offset (exclusive). */
    val end: Int,
)
