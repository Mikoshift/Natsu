package io.mikoshift.natsu.ui.reader.web

import io.mikoshift.natsu.domain.model.TextToken

internal object ReaderWordTap {
    fun tokenAtCharOffset(tokens: List<TextToken>, charOffset: Int): TextToken? {
        if (tokens.isEmpty()) return null
        val safeOffset = charOffset.coerceAtLeast(0)
        var position = 0
        for (token in tokens) {
            val end = position + token.surface.length
            if (safeOffset in position until end) {
                return token
            }
            position = end
        }
        return null
    }
}
