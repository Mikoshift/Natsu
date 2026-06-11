package io.mikoshift.natsu.data.dictionary

import io.mikoshift.natsu.domain.model.SenseBlock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SenseContentSanitizeTest {

    @Test
    fun isJunkDefinition_filtersAttributionStrings() {
        assertTrue(isJunkDefinition("JMdict"))
        assertTrue(isJunkDefinition("JMDict"))
        assertTrue(isJunkDefinition("[1]"))
        assertTrue(isJunkDefinition("|"))
        assertTrue(isJunkDefinition("forms"))
        assertTrue(isJunkDefinition("JMdict | Tatoeba [1]"))
        assertFalse(isJunkDefinition("control (of a machine, etc.)"))
    }

    @Test
    fun sanitizeSenseContent_removesJunkDefinitions() {
        val sanitized = sanitizeSenseContent(
            SenseContentData(
                senseBlocks = listOf(
                    SenseBlock(definitions = listOf("control", "JMdict")),
                    SenseBlock(definitions = listOf("JMDict")),
                ),
            ),
        )

        assertEquals(1, sanitized.senseBlocks.size)
        assertEquals(listOf("control"), sanitized.senseBlocks.first().definitions)
    }

    @Test
    fun parseSenseContentJson_filtersJunkFromStoredV2() {
        val json = """
            {"v":2,"pos":[],"senses":[{"defs":["JMdict"]}]}
        """.trimIndent()

        val content = parseSenseContentJson(json)

        assertFalse(content.hasContent())
    }
}
