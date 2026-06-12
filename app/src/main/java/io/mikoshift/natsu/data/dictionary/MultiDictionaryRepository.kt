package io.mikoshift.natsu.data.dictionary

import io.mikoshift.natsu.domain.model.DictionaryEntry
import io.mikoshift.natsu.domain.model.DictionarySense
import io.mikoshift.natsu.domain.model.LookupMatchKind
import io.mikoshift.natsu.domain.repository.DictionaryRepository

class MultiDictionaryRepository(
    private val localStore: DictionaryLocalStore,
) : DictionaryRepository {

    private val cache = LruLookupCache()
    private var cachedGeneration = -1

    override suspend fun lookup(
        surface: String,
        lemma: String,
        reading: String,
        compoundSurfaces: List<String>,
    ): DictionaryEntry? {
        if (!localStore.hasEnabledDictionaries()) return null

        val cacheKey = LookupCacheKey(
            surface = surface,
            lemma = lemma,
            reading = reading,
            compoundSurfaces = compoundSurfaces,
        )
        invalidateCacheIfNeeded()
        if (cache.contains(cacheKey)) {
            return cache.get(cacheKey)
        }

        val candidates = LookupCandidateGenerator.generate(
            surface = surface,
            lemma = lemma,
            reading = reading,
            compoundSurfaces = compoundSurfaces,
        )
        if (candidates.isEmpty()) {
            cache.put(cacheKey, null)
            return null
        }

        val queries = candidates.map { it.text }
        val rows = localStore.lookupTerms(queries)
        if (rows.isEmpty()) {
            cache.put(cacheKey, null)
            return null
        }

        val candidateByText = candidates.associateBy { it.text }
        val matches = rows
            .mapNotNull { row -> matchRowToCandidate(row, candidateByText, surface, lemma, reading) }
            .sortedWith(
                compareBy<TermMatch> { it.rank }
                    .thenBy { it.row.dictionaryPriority }
                    .thenByDescending { it.row.score },
            )

        if (matches.isEmpty()) {
            cache.put(cacheKey, null)
            return null
        }

        val bestMatch = matches.first()
        val senses = matches
            .filter { it.candidate.kind == bestMatch.candidate.kind }
            .mapNotNull { match ->
                val content = parseSenseContentJson(match.row.glossesJson)
                if (!content.hasContent()) return@mapNotNull null
                DictionarySense(
                    dictionaryTitle = match.row.dictionaryTitle,
                    kanji = listOf(match.row.expression),
                    readings = listOf(match.row.reading),
                    partsOfSpeech = content.partsOfSpeech,
                    senseBlocks = content.senseBlocks,
                )
            }

        if (senses.isEmpty()) {
            cache.put(cacheKey, null)
            return null
        }

        val entry = DictionaryEntry(
            querySurface = surface,
            queryLemma = lemma,
            queryReading = reading,
            senses = senses,
            matchKind = bestMatch.candidate.kind,
            matchedExpression = bestMatch.row.expression,
            matchedReading = bestMatch.row.reading,
            deinflectionRuleName = bestMatch.candidate.ruleName,
        )
        cache.put(cacheKey, entry)
        return entry
    }

    override suspend fun hasEnabledDictionaries(): Boolean =
        localStore.hasEnabledDictionaries()

    private fun invalidateCacheIfNeeded() {
        val generation = localStore.currentChangeGeneration()
        if (generation != cachedGeneration) {
            cache.clear()
            cachedGeneration = generation
        }
    }

    private data class TermMatch(
        val row: TermLookupRow,
        val candidate: LookupCandidate,
        val rank: Int,
    )

    private fun matchRowToCandidate(
        row: TermLookupRow,
        candidateByText: Map<String, LookupCandidate>,
        surface: String,
        lemma: String,
        reading: String,
    ): TermMatch? {
        val matchedCandidate = findMatchingCandidate(row, candidateByText) ?: return null
        if (matchedCandidate.kind == LookupMatchKind.Deinflection &&
            !ruleTagsMatch(row.ruleTags, matchedCandidate.conditionsOut)
        ) {
            return null
        }

        val rank = matchKindPriority(matchedCandidate.kind) * 100 +
            termMatchPriority(
                expression = row.expression,
                reading = row.reading,
                surface = surface,
                lemma = lemma,
                queryReading = reading,
            )

        return TermMatch(
            row = row,
            candidate = matchedCandidate,
            rank = rank,
        )
    }

    private fun findMatchingCandidate(
        row: TermLookupRow,
        candidateByText: Map<String, LookupCandidate>,
    ): LookupCandidate? {
        candidateByText[row.expression]?.let { return it }
        candidateByText[row.reading]?.let { return it }

        val normalizedReading = toHiragana(row.reading)
        for ((text, candidate) in candidateByText) {
            if (toHiragana(text) == normalizedReading) {
                return candidate
            }
        }
        return null
    }
}
