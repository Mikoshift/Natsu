package io.mikoshift.natsu.data.dictionary

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StructuredContentPlainTextTest {

    @Test
    fun extractSimpleSenseGroup() {
        val structuredContent = JSONObject(
            """
            {
              "type": "structured-content",
              "content": [{
                "tag": "div",
                "data": {"content": "sense-group"},
                "content": [{
                  "tag": "span",
                  "title": "noun (common) (futsuumeishi)",
                  "data": {"class": "tag", "code": "n", "content": "part-of-speech-info"},
                  "content": "noun"
                }, {
                  "tag": "div",
                  "data": {"content": "sense"},
                  "content": {
                    "tag": "ul",
                    "data": {"content": "glossary"},
                    "content": [
                      {"tag": "li", "content": "napa cabbage (Brassica rapa subsp. pekinensis)"},
                      {"tag": "li", "content": "nappa cabbage"},
                      {"tag": "li", "content": "Chinese cabbage"}
                    ]
                  }
                }]
              }, {
                "tag": "div",
                "data": {"content": "attribution"},
                "content": {"tag": "a", "href": "https://example.com", "content": "JMdict"}
              }]
            }
            """.trimIndent(),
        )

        val content = StructuredContentPlainText.extractSenseContent(structuredContent)

        assertEquals(listOf("noun"), content.partsOfSpeech)
        assertEquals(1, content.senseBlocks.size)
        assertEquals(
            listOf(
                "napa cabbage (Brassica rapa subsp. pekinensis)",
                "nappa cabbage",
                "Chinese cabbage",
            ),
            content.senseBlocks.first().definitions,
        )
        assertTrue(content.senseBlocks.none { it.exampleJapanese != null })
    }

    @Test
    fun extractJitendexEntryWithExamples() {
        val structuredContent = JSONObject(
            """
            {
              "type": "structured-content",
              "content": [{
                "tag": "ul",
                "data": {"content": "sense-groups"},
                "content": [{
                  "tag": "li",
                  "data": {"content": "sense-group"},
                  "content": [{
                    "tag": "span",
                    "data": {"class": "tag", "code": "n", "content": "part-of-speech-info"},
                    "content": "noun"
                  }, {
                    "tag": "span",
                    "data": {"class": "tag", "code": "vs", "content": "part-of-speech-info"},
                    "content": "suru"
                  }, {
                    "tag": "ol",
                    "content": [{
                      "tag": "li",
                      "data": {"content": "sense"},
                      "content": [{
                        "tag": "ul",
                        "data": {"content": "glossary"},
                        "content": [
                          {"tag": "li", "content": "test (of ability, knowledge, etc.)"},
                          {"tag": "li", "content": "exam"}
                        ]
                      }, {
                        "tag": "div",
                        "data": {"content": "extra-info"},
                        "content": {
                          "tag": "div",
                          "data": {"content": "example-sentence"},
                          "content": [{
                            "tag": "div",
                            "data": {"content": "example-sentence-a"},
                            "content": {
                              "tag": "span",
                              "lang": "ja",
                              "content": ["私は数学の", {"tag": "span", "content": "テスト"}, "の結果に満足しています。"]
                            }
                          }, {
                            "tag": "div",
                            "data": {"content": "example-sentence-b"},
                            "content": [
                              {"tag": "span", "lang": "en", "content": "I am satisfied with the result of my math test."},
                              {"tag": "span", "data": {"content": "attribution-footnote"}, "content": "[1]"}
                            ]
                          }]
                        }
                      }]
                    }]
                  }]
                }, {
                  "tag": "div",
                  "data": {"content": "attribution"},
                  "content": [{"tag": "a", "content": "JMdict"}, " | Tatoeba ", {"tag": "a", "content": "[1]"}]
                }]
              }]
            }
            """.trimIndent(),
        )

        val content = StructuredContentPlainText.extractSenseContent(structuredContent)

        assertEquals(listOf("noun", "suru"), content.partsOfSpeech)
        assertEquals(1, content.senseBlocks.size)
        val block = content.senseBlocks.first()
        assertEquals(
            listOf("test (of ability, knowledge, etc.)", "exam"),
            block.definitions,
        )
        assertEquals("私は数学のテストの結果に満足しています。", block.exampleJapanese)
        assertEquals("I am satisfied with the result of my math test.", block.exampleEnglish)
    }

    @Test
    fun parseLegacyFlatGlossesFiltersJunkAndGroupsExamples() {
        val legacyJson = """
            ["noun", "suru", "transitive", "test (of ability, knowledge, etc.)", "exam", "examination", "quiz", "私は数学の", "テスト", "の結果に満足しています。", "I am satisfied with the result of my math test.", "[1]", "JMdict"]
        """.trimIndent()

        val content = parseSenseContentJson(legacyJson)

        assertEquals(listOf("noun", "suru", "transitive"), content.partsOfSpeech)
        assertEquals(1, content.senseBlocks.size)
        val block = content.senseBlocks.first()
        assertEquals(
            listOf("test (of ability, knowledge, etc.)", "exam", "examination", "quiz"),
            block.definitions,
        )
        assertEquals("私は数学のテストの結果に満足しています。", block.exampleJapanese)
        assertEquals("I am satisfied with the result of my math test.", block.exampleEnglish)
    }
}
