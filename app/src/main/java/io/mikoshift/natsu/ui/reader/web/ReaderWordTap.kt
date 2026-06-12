package io.mikoshift.natsu.ui.reader.web

import io.mikoshift.natsu.domain.model.TextToken

internal object ReaderWordTap {
    private const val MAX_NEIGHBOR_DISTANCE = 2

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

    fun nearestClickableToken(tokens: List<TextToken>, charOffset: Int): TextToken? {
        if (tokens.isEmpty()) return null
        val safeOffset = charOffset.coerceAtLeast(0)
        return findNearestClickable(tokens, safeOffset, preferForward = true)
            ?: findNearestClickable(tokens, safeOffset, preferForward = false)
    }

    fun resolveTapToken(tokens: List<TextToken>, charOffset: Int): TextToken? {
        val direct = tokenAtCharOffset(tokens, charOffset)
        if (direct?.isClickable == true) {
            return direct
        }
        return nearestClickableToken(tokens, charOffset)
    }

    private fun findNearestClickable(
        tokens: List<TextToken>,
        charOffset: Int,
        preferForward: Boolean,
    ): TextToken? {
        var position = 0
        var best: TextToken? = null
        var bestDistance = Int.MAX_VALUE

        for (token in tokens) {
            val start = position
            val end = position + token.surface.length
            if (token.isClickable) {
                val distance = distanceToRange(charOffset, start until end)
                val isCandidate = if (preferForward) {
                    start >= charOffset
                } else {
                    start < charOffset
                }
                if (isCandidate && distance <= MAX_NEIGHBOR_DISTANCE && distance < bestDistance) {
                    bestDistance = distance
                    best = token
                }
            }
            position = end
        }

        return best
    }

    private fun distanceToRange(offset: Int, range: IntRange): Int {
        if (offset in range) return 0
        return if (offset < range.first) {
            range.first - offset
        } else {
            offset - range.last
        }
    }
}
