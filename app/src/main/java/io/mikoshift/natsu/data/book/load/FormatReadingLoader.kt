package io.mikoshift.natsu.data.book.load

import io.mikoshift.natsu.domain.model.reading.BookFormat
import io.mikoshift.natsu.domain.model.reading.ManifestSection
import io.mikoshift.natsu.domain.model.reading.ReadingSection
import java.io.File

interface FormatReadingLoader {
    val format: BookFormat

    suspend fun loadSection(bookDir: File, section: ManifestSection): ReadingSection
}
