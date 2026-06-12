package io.mikoshift.natsu.ui.reader.web

import io.mikoshift.natsu.domain.model.TextToken
import io.mikoshift.natsu.domain.model.reading.ReadingBlock
import io.mikoshift.natsu.domain.model.reading.SectionReadingContent
import io.mikoshift.natsu.domain.repository.TextTokenizer

internal class ReaderSectionTokenCache {
    private val bySection = linkedMapOf<String, MutableMap<Int, List<TextToken>>>()
    private val textFallbackBySection = linkedMapOf<String, MutableMap<String, List<TextToken>>>()

    fun warm(
        sectionId: String,
        sectionContent: SectionReadingContent,
        tokenizer: TextTokenizer,
    ) {
        val layout = sectionContent.layout
        val section = sectionContent.section
        val byIndex = linkedMapOf<Int, List<TextToken>>()

        layout.paragraphs.indices.forEach { paragraphIndex ->
            val blockIndex = layout.blockIndexByParagraph[paragraphIndex]
            val block = section.blocks[blockIndex]
            byIndex[paragraphIndex] = when (block) {
                is ReadingBlock.Paragraph -> tokenizer.tokenizeParagraph(block.spans)
                is ReadingBlock.Heading -> tokenizer.tokenize(block.text)
                is ReadingBlock.Image -> emptyList()
            }
        }

        bySection[sectionId] = byIndex
        textFallbackBySection[sectionId] = linkedMapOf()
    }

    fun tokens(sectionId: String, paragraphIndex: Int): List<TextToken>? {
        if (paragraphIndex < 0) return null
        return bySection[sectionId]?.get(paragraphIndex)
    }

    fun tokensByText(
        sectionId: String,
        paragraphText: String,
        tokenize: (String) -> List<TextToken>,
    ): List<TextToken> {
        val section = textFallbackBySection.getOrPut(sectionId) { linkedMapOf() }
        return section.getOrPut(paragraphText) { tokenize(paragraphText) }
    }

    fun clear() {
        bySection.clear()
        textFallbackBySection.clear()
    }

    fun removeSection(sectionId: String) {
        bySection.remove(sectionId)
        textFallbackBySection.remove(sectionId)
    }
}
