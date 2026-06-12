package io.mikoshift.natsu.data.book.epub.spike

import org.junit.Assert.assertEquals
import org.junit.Test

class DraftEpubLoaderTest {

    @Test
    fun resolveImagePath_resolvesFromSpineDirectory() {
        assertEquals(
            "OEBPS/images/cover.png",
            DraftEpubLoader.resolveImagePath(
                spineHref = "OEBPS/chapter.xhtml",
                destination = "images/cover.png",
            ),
        )
    }

    @Test
    fun resolveImagePath_handlesSpineAtRoot() {
        assertEquals(
            "images/cover.png",
            DraftEpubLoader.resolveImagePath(
                spineHref = "chapter.xhtml",
                destination = "images/cover.png",
            ),
        )
    }
}
