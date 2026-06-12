package io.mikoshift.natsu.data.dictionary

fun ruleTagsMatch(termRuleTags: String, candidateConditions: Set<String>): Boolean {
    if (termRuleTags.isBlank() || candidateConditions.isEmpty()) return true

    val termTags = termRuleTags.split(' ')
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .toSet()

    return candidateConditions.any { condition ->
        condition in termTags ||
            termTags.any { tag -> tag == condition || tag.startsWith(condition) }
    }
}
