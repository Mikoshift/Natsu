package io.mikoshift.natsu.data.dictionary

import io.mikoshift.natsu.domain.model.LookupMatchKind

data class LookupCandidate(
    val text: String,
    val kind: LookupMatchKind,
    val ruleName: String? = null,
    val conditionsOut: Set<String> = emptySet(),
)
