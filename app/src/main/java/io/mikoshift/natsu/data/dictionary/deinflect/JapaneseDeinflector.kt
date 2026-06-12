package io.mikoshift.natsu.data.dictionary.deinflect

object JapaneseDeinflector {

    private data class SuffixRule(
        val suffix: String,
        val replacement: String,
        val ruleName: String,
        val conditionsOut: Set<String>,
    )

    private data class WholeWordRule(
        val inflected: String,
        val deinflected: String,
        val ruleName: String,
        val conditionsOut: Set<String>,
    )

    private val wholeWordRules = listOf(
        WholeWordRule("行った", "行く", "past", setOf("v5")),
        WholeWordRule("行って", "行く", "te-form", setOf("v5")),
        WholeWordRule("行かない", "行く", "negative", setOf("v5")),
        WholeWordRule("来た", "来る", "past", setOf("vk")),
        WholeWordRule("来て", "来る", "te-form", setOf("vk")),
        WholeWordRule("来ない", "来る", "negative", setOf("vk")),
    )

    private val suffixRules = listOf(
        // Ichidan (v1) — longer suffixes first
        SuffixRule("ません", "る", "negative-polite", setOf("v1")),
        SuffixRule("ました", "る", "past-polite", setOf("v1")),
        SuffixRule("ます", "る", "polite", setOf("v1")),
        SuffixRule("ない", "る", "negative", setOf("v1")),
        SuffixRule("た", "る", "past", setOf("v1")),
        SuffixRule("て", "る", "te-form", setOf("v1")),

        // Godan negative
        SuffixRule("わない", "う", "negative", setOf("v5")),
        SuffixRule("らない", "る", "negative", setOf("v5")),
        SuffixRule("まない", "む", "negative", setOf("v5")),
        SuffixRule("ばない", "ぶ", "negative", setOf("v5")),
        SuffixRule("なない", "ぬ", "negative", setOf("v5")),
        SuffixRule("たない", "つ", "negative", setOf("v5")),
        SuffixRule("さない", "す", "negative", setOf("v5")),
        SuffixRule("がない", "ぐ", "negative", setOf("v5")),
        SuffixRule("かない", "く", "negative", setOf("v5")),

        // Godan te-form
        SuffixRule("いて", "く", "te-form", setOf("v5")),
        SuffixRule("いで", "ぐ", "te-form", setOf("v5")),
        SuffixRule("して", "す", "te-form", setOf("v5")),
        SuffixRule("って", "う", "te-form", setOf("v5")),
        SuffixRule("って", "つ", "te-form", setOf("v5")),
        SuffixRule("って", "る", "te-form", setOf("v5")),
        SuffixRule("んで", "ぬ", "te-form", setOf("v5")),
        SuffixRule("んで", "ぶ", "te-form", setOf("v5")),
        SuffixRule("んで", "む", "te-form", setOf("v5")),

        // Godan past
        SuffixRule("いた", "く", "past", setOf("v5")),
        SuffixRule("いだ", "ぐ", "past", setOf("v5")),
        SuffixRule("した", "す", "past", setOf("v5")),
        SuffixRule("った", "う", "past", setOf("v5")),
        SuffixRule("った", "つ", "past", setOf("v5")),
        SuffixRule("った", "る", "past", setOf("v5")),
        SuffixRule("んだ", "ぬ", "past", setOf("v5")),
        SuffixRule("んだ", "ぶ", "past", setOf("v5")),
        SuffixRule("んだ", "む", "past", setOf("v5")),

        // Suru (vs)
        SuffixRule("しない", "する", "negative", setOf("vs")),
        SuffixRule("します", "する", "polite", setOf("vs")),
        SuffixRule("した", "する", "past", setOf("vs")),
        SuffixRule("して", "する", "te-form", setOf("vs")),

        // Kuru (vk)
        SuffixRule("こない", "くる", "negative", setOf("vk")),
        SuffixRule("きた", "くる", "past", setOf("vk")),
        SuffixRule("きて", "くる", "te-form", setOf("vk")),

        // i-adjective
        SuffixRule("くない", "い", "negative", setOf("adj-i")),
        SuffixRule("かった", "い", "past", setOf("adj-i")),
        SuffixRule("くて", "い", "te-form", setOf("adj-i")),

        // na-adjective / copula
        SuffixRule("じゃない", "だ", "negative", setOf("adj-na")),
        SuffixRule("ではない", "だ", "negative", setOf("adj-na")),
        SuffixRule("だった", "だ", "past", setOf("adj-na")),

        // Polite chain
        SuffixRule("ません", "ます", "negative-polite", setOf("-ます")),
        SuffixRule("ました", "ます", "past-polite", setOf("-ます")),
    ).sortedByDescending { it.suffix.length }

    fun deinflect(surface: String, reading: String = ""): List<DeinflectionCandidate> {
        if (surface.isBlank()) return emptyList()

        val results = deinflectSurface(surface).toMutableList()
        if (reading.isNotBlank() && reading != surface) {
            val seen = results.map { it.text }.toMutableSet()
            for (candidate in deinflectSurface(reading)) {
                if (candidate.text !in seen) {
                    seen += candidate.text
                    results += candidate
                }
            }
        }
        return results
    }

    private fun deinflectSurface(surface: String): List<DeinflectionCandidate> {
        val seen = linkedSetOf<String>()
        val results = mutableListOf<DeinflectionCandidate>()

        fun addCandidate(text: String, ruleName: String, conditionsOut: Set<String>) {
            if (text.isBlank() || text == surface || text == "*" || text in seen) return
            seen += text
            results += DeinflectionCandidate(
                text = text,
                ruleName = ruleName,
                conditionsOut = conditionsOut,
            )
        }

        for (rule in wholeWordRules) {
            if (surface == rule.inflected) {
                addCandidate(rule.deinflected, rule.ruleName, rule.conditionsOut)
            }
        }

        for (rule in suffixRules) {
            if (surface.endsWith(rule.suffix)) {
                val stem = surface.dropLast(rule.suffix.length)
                if (stem.isNotEmpty()) {
                    addCandidate(stem + rule.replacement, rule.ruleName, rule.conditionsOut)
                }
            }
        }

        return results
    }
}
