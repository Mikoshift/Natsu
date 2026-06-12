package io.mikoshift.natsu.data.dictionary.deinflect

data class DeinflectionCandidate(
    val text: String,
    val ruleName: String,
    val conditionsOut: Set<String>,
)
