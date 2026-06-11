package io.mikoshift.natsu.ui.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.mikoshift.natsu.domain.model.DictionaryEntry
import io.mikoshift.natsu.domain.model.Document
import io.mikoshift.natsu.domain.model.ReaderSettings
import io.mikoshift.natsu.domain.model.TextToken
import io.mikoshift.natsu.domain.repository.DictionaryRepository
import io.mikoshift.natsu.domain.repository.DocumentRepository
import io.mikoshift.natsu.domain.repository.ReadingContentRepository
import io.mikoshift.natsu.domain.repository.TextTokenizer
import io.mikoshift.natsu.data.reader.findMatchOffsets
import io.mikoshift.natsu.data.reader.localHighlightRange
import io.mikoshift.natsu.data.reader.paragraphIndexForCharOffset
import io.mikoshift.natsu.data.settings.ReaderSettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface WordLookupState {
    data object Hidden : WordLookupState
    data object Loading : WordLookupState
    data class Found(val entry: DictionaryEntry) : WordLookupState
    data class NotFound(
        val token: TextToken,
        val suggestInstallDictionary: Boolean = false,
    ) : WordLookupState
}

data class SearchHighlight(
    val paragraphIndex: Int,
    val range: IntRange,
    val scrollRequestId: Long,
)

data class ReaderUiState(
    val document: Document? = null,
    val paragraphs: List<List<TextToken>> = emptyList(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val scrollToIndex: Int = 0,
    val paragraphStartOffsets: List<Int> = emptyList(),
    val wordLookup: WordLookupState = WordLookupState.Hidden,
    val searchActive: Boolean = false,
    val searchQuery: String = "",
    val searchMatchOffsets: List<Int> = emptyList(),
    val searchMatchIndex: Int = 0,
    val searchHighlight: SearchHighlight? = null,
)

class ReaderViewModel(
    private val documentRepository: DocumentRepository,
    private val readingContentRepository: ReadingContentRepository,
    private val dictionaryRepository: DictionaryRepository,
    private val textTokenizer: TextTokenizer,
    readerSettingsStore: ReaderSettingsStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReaderUiState())
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()

    val readerSettings: StateFlow<ReaderSettings> = readerSettingsStore.settings
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ReaderSettings(),
        )

    private var lastSavedCharOffset: Int = -1
    private var lastSavedParagraphIndex: Int = -1
    private var saveJob: Job? = null
    private var rawText: String = ""
    private var searchJob: Job? = null
    private var searchScrollRequestId: Long = 0L

    fun loadDocument(documentId: String) {
        viewModelScope.launch {
            saveJob?.cancel()
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val document = documentRepository.getDocument(documentId)
            if (document == null) {
                _uiState.update {
                    it.copy(isLoading = false, errorMessage = "Document not found")
                }
                return@launch
            }

            readingContentRepository.loadLayout(documentId)
                .onSuccess { layout ->
                    rawText = layout.canonicalText
                    documentRepository.ensureCharCount(documentId, layout.canonicalText.length)
                    val tokenized = withContext(Dispatchers.Default) {
                        textTokenizer.tokenizeParagraphs(layout.paragraphs)
                    }
                    val scrollIndex = when {
                        document.lastReadCharOffset > 0 ->
                            layout.paragraphIndexForCharOffset(document.lastReadCharOffset)
                        document.lastReadParagraphIndex > 0 ->
                            document.lastReadParagraphIndex.coerceAtMost(
                                layout.paragraphs.lastIndex.coerceAtLeast(0),
                            )
                        else -> 0
                    }
                    lastSavedCharOffset = document.lastReadCharOffset
                    lastSavedParagraphIndex = document.lastReadParagraphIndex
                    _uiState.update {
                        it.copy(
                            document = document,
                            paragraphs = tokenized,
                            isLoading = false,
                            scrollToIndex = scrollIndex,
                            paragraphStartOffsets = layout.paragraphStartOffsets,
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = error.message ?: "Failed to load text",
                        )
                    }
                }
        }
    }

    fun onWordClicked(token: TextToken) {
        if (!token.isClickable) return
        viewModelScope.launch {
            _uiState.update { it.copy(wordLookup = WordLookupState.Loading) }
            val entry = withContext(Dispatchers.IO) {
                dictionaryRepository.lookup(
                    surface = token.surface,
                    lemma = token.lemma,
                    reading = token.reading,
                )
            }
            val suggestInstall = entry == null &&
                !dictionaryRepository.hasEnabledDictionaries()
            _uiState.update { state ->
                state.copy(
                    wordLookup = if (entry != null) {
                        WordLookupState.Found(entry)
                    } else {
                        WordLookupState.NotFound(
                            token = token,
                            suggestInstallDictionary = suggestInstall,
                        )
                    },
                )
            }
        }
    }

    fun dismissWordLookup() {
        _uiState.update { it.copy(wordLookup = WordLookupState.Hidden) }
    }

    fun openSearch() {
        _uiState.update {
            it.copy(
                searchActive = true,
                searchQuery = "",
                searchMatchOffsets = emptyList(),
                searchMatchIndex = 0,
                searchHighlight = null,
            )
        }
    }

    fun closeSearch() {
        searchJob?.cancel()
        _uiState.update {
            it.copy(
                searchActive = false,
                searchQuery = "",
                searchMatchOffsets = emptyList(),
                searchMatchIndex = 0,
                searchHighlight = null,
            )
        }
    }

    fun updateSearchQuery(query: String) {
        _uiState.update {
            it.copy(
                searchQuery = query,
                searchMatchIndex = 0,
                searchHighlight = null,
                searchMatchOffsets = if (query.isEmpty()) emptyList() else it.searchMatchOffsets,
            )
        }
        searchJob?.cancel()
        if (query.isEmpty()) return
        searchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            val matches = withContext(Dispatchers.Default) {
                findMatchOffsets(rawText, query)
            }
            _uiState.update { state ->
                if (state.searchQuery != query) return@update state
                state.copy(
                    searchMatchOffsets = matches,
                    searchMatchIndex = 0,
                    searchHighlight = highlightForMatch(matches, matchIndex = 0, query),
                )
            }
        }
    }

    fun goToNextSearchMatch() {
        navigateSearchMatch(delta = 1)
    }

    fun goToPreviousSearchMatch() {
        navigateSearchMatch(delta = -1)
    }

    private fun navigateSearchMatch(delta: Int) {
        val state = _uiState.value
        val matches = state.searchMatchOffsets
        if (matches.isEmpty()) return

        val nextIndex = (state.searchMatchIndex + delta).mod(matches.size)
        _uiState.update {
            it.copy(
                searchMatchIndex = nextIndex,
                searchHighlight = highlightForMatch(
                    matches = matches,
                    matchIndex = nextIndex,
                    query = state.searchQuery,
                ),
            )
        }
    }

    private fun highlightForMatch(
        matches: List<Int>,
        matchIndex: Int,
        query: String,
    ): SearchHighlight? {
        val matchOffset = matches.getOrNull(matchIndex) ?: return null
        val paragraphIndex = paragraphIndexForMatch(matchOffset)
        val paragraphStart = _uiState.value.paragraphStartOffsets.getOrElse(paragraphIndex) { 0 }
        return SearchHighlight(
            paragraphIndex = paragraphIndex,
            range = localHighlightRange(matchOffset, query.length, paragraphStart),
            scrollRequestId = ++searchScrollRequestId,
        )
    }

    private fun paragraphIndexForMatch(charOffset: Int): Int {
        val offsets = _uiState.value.paragraphStartOffsets
        if (offsets.isEmpty()) return 0
        if (charOffset <= 0) return 0
        val index = offsets.indexOfLast { it <= charOffset }
        return if (index >= 0) index else 0
    }

    fun saveReadingPosition(paragraphIndex: Int, immediate: Boolean = false) {
        val state = _uiState.value
        val documentId = state.document?.id ?: return
        if (state.paragraphs.isEmpty()) return

        val safeIndex = paragraphIndex.coerceIn(0, state.paragraphs.lastIndex)
        val charOffset = state.paragraphStartOffsets.getOrElse(safeIndex) { 0 }
        if (!immediate &&
            charOffset == lastSavedCharOffset &&
            safeIndex == lastSavedParagraphIndex
        ) {
            return
        }

        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            if (!immediate) {
                delay(READING_POSITION_SAVE_DELAY_MS)
            }
            persistReadingPosition(
                documentId = documentId,
                charOffset = charOffset,
                paragraphIndex = safeIndex,
                notifyLibrary = immediate,
            )
        }
    }

    fun flushReadingPosition(paragraphIndex: Int) {
        saveJob?.cancel()
        val state = _uiState.value
        val documentId = state.document?.id ?: return
        if (state.paragraphs.isEmpty()) return

        val safeIndex = paragraphIndex.coerceIn(0, state.paragraphs.lastIndex)
        val charOffset = state.paragraphStartOffsets.getOrElse(safeIndex) { 0 }

        viewModelScope.launch {
            persistReadingPosition(
                documentId = documentId,
                charOffset = charOffset,
                paragraphIndex = safeIndex,
                notifyLibrary = true,
            )
        }
    }

    private suspend fun persistReadingPosition(
        documentId: String,
        charOffset: Int,
        paragraphIndex: Int,
        notifyLibrary: Boolean,
    ) {
        if (charOffset == lastSavedCharOffset &&
            paragraphIndex == lastSavedParagraphIndex &&
            !notifyLibrary
        ) {
            return
        }
        lastSavedCharOffset = charOffset
        lastSavedParagraphIndex = paragraphIndex
        runCatching {
            documentRepository.updateReadingPosition(
                documentId = documentId,
                charOffset = charOffset,
                paragraphIndex = paragraphIndex,
            )
            if (notifyLibrary) {
                documentRepository.notifyDocumentsChanged()
            }
        }
    }

    companion object {
        private const val READING_POSITION_SAVE_DELAY_MS = 400L
        private const val SEARCH_DEBOUNCE_MS = 250L
    }
}
