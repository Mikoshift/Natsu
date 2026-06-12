package io.mikoshift.natsu.data.dictionary

import io.mikoshift.natsu.data.dictionary.deinflect.JapaneseDeinflector
import io.mikoshift.natsu.domain.model.LookupMatchKind

object LookupCandidateGenerator {

    fun generate(
        surface: String,
        lemma: String,
        reading: String,
        compoundSurfaces: List<String> = emptyList(),
    ): List<LookupCandidate> {
        val candidates = linkedMapOf<String, LookupCandidate>()

        fun add(text: String, kind: LookupMatchKind, ruleName: String? = null, conditionsOut: Set<String> = emptySet()) {
            if (text.isBlank() || text == "*") return
            val existing = candidates[text]
            if (existing == null || matchKindPriority(kind) < matchKindPriority(existing.kind)) {
                candidates[text] = LookupCandidate(
                    text = text,
                    kind = kind,
                    ruleName = ruleName,
                    conditionsOut = conditionsOut,
                )
            }
        }

        add(surface, LookupMatchKind.Direct)
        addKanaVariants(surface).forEach { add(it, LookupMatchKind.Direct) }

        if (lemma.isNotBlank() && lemma != "*" && lemma != surface) {
            add(lemma, LookupMatchKind.Lemma)
            addKanaVariants(lemma).forEach { add(it, LookupMatchKind.Lemma) }
        }

        addReadingVariants(reading).forEach { add(it, LookupMatchKind.Direct) }

        for (deinflected in JapaneseDeinflector.deinflect(surface, reading)) {
            add(
                text = deinflected.text,
                kind = LookupMatchKind.Deinflection,
                ruleName = deinflected.ruleName,
                conditionsOut = deinflected.conditionsOut,
            )
            addKanaVariants(deinflected.text).forEach {
                add(
                    text = it,
                    kind = LookupMatchKind.Deinflection,
                    ruleName = deinflected.ruleName,
                    conditionsOut = deinflected.conditionsOut,
                )
            }
        }

        for (compound in compoundSurfaces) {
            if (compound.isNotBlank() && compound != surface) {
                add(compound, LookupMatchKind.Compound)
                addKanaVariants(compound).forEach { add(it, LookupMatchKind.Compound) }
            }
        }

        return candidates.values.sortedWith(
            compareBy<LookupCandidate> { matchKindPriority(it.kind) }
                .thenBy { it.text.length },
        )
    }

    private fun addReadingVariants(reading: String): List<String> {
        if (reading.isBlank()) return emptyList()
        val variants = linkedSetOf(reading)
        val hiragana = toHiragana(reading)
        if (hiragana != reading) variants += hiragana
        val katakana = toKatakana(reading)
        if (katakana != reading) variants += katakana
        return variants.filter { it.isNotBlank() && it != "*" }.toList()
    }

    private fun addKanaVariants(value: String): List<String> {
        if (!containsKana(value)) return emptyList()
        val variants = linkedSetOf<String>()
        val hiragana = toHiragana(value)
        if (hiragana != value) variants += hiragana
        val katakana = toKatakana(value)
        if (katakana != value) variants += katakana
        return variants.toList()
    }
}
