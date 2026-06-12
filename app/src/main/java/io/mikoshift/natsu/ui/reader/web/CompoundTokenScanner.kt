package io.mikoshift.natsu.ui.reader.web

import io.mikoshift.natsu.domain.model.TextToken

object CompoundTokenScanner {

    fun compoundCandidates(
        tokens: List<TextToken>,
        tapIndex: Int,
        maxTokens: Int = 4,
    ): List<String> {
        if (tapIndex !in tokens.indices) return emptyList()

        val clickableIndices = tokens.indices.filter { tokens[it].isClickable }
        if (tapIndex !in clickableIndices) return emptyList()

        val maxWindow = minOf(maxTokens, clickableIndices.size)
        val results = linkedSetOf<String>()

        for (size in maxWindow downTo 2) {
            for (start in 0..clickableIndices.size - size) {
                val window = clickableIndices.subList(start, start + size)
                if (tapIndex !in window) continue
                val compound = window.joinToString("") { tokens[it].surface }
                if (compound.isNotBlank()) {
                    results += compound
                }
            }
        }

        return results.sortedByDescending { it.length }
    }
}
