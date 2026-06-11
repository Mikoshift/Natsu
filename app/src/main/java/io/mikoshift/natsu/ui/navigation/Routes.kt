package io.mikoshift.natsu.ui.navigation

object Routes {
    const val LIBRARY = "library"
    const val DICTIONARIES = "dictionaries"
    const val SETTINGS = "settings"
    const val READER = "reader/{documentId}"

    fun reader(documentId: String): String = "reader/$documentId"
}
