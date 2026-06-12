package io.mikoshift.natsu.domain.model.reading

enum class BookFormat(val manifestValue: String) {
    PlainText("plain_text"),
    Markdown("markdown"),
    Epub("epub"),
    ;

    companion object {
        fun fromManifestValue(value: String): BookFormat =
            entries.firstOrNull { it.manifestValue == value }
                ?: throw IllegalArgumentException("Unknown book format: $value")
    }
}
