package io.mikoshift.natsu.ui.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.mikoshift.natsu.domain.model.DictionaryEntry
import io.mikoshift.natsu.domain.model.Document
import io.mikoshift.natsu.domain.model.ReaderSettings
import io.mikoshift.natsu.domain.model.TextToken
import io.mikoshift.natsu.domain.repository.DictionaryRepository
import io.mikoshift.natsu.domain.repository.DocumentRepository
import io.mikoshift.natsu.domain.repository.TextTokenizer
import io.mikoshift.natsu.data.reader.buildParagraphLayout
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

data class ReaderUiState(
    val document: Document? = null,
    val paragraphs: List<List<TextToken>> = emptyList(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val scrollToIndex: Int = 0,
    val paragraphStartOffsets: List<Int> = emptyList(),
    val wordLookup: WordLookupState = WordLookupState.Hidden,
)

class ReaderViewModel(
    private val documentRepository: DocumentRepository,
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

            documentRepository.readDocumentText(document)
                .onSuccess { text ->
                    documentRepository.ensureCharCount(documentId, text.length)
                    val layout = buildParagraphLayout(text)
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
                            paragraphStartOffsets = layout.startOffsets,
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
    }
}
