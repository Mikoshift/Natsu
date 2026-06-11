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
import io.mikoshift.natsu.data.reader.splitIntoParagraphs
import io.mikoshift.natsu.data.settings.ReaderSettingsStore
import kotlinx.coroutines.Dispatchers
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

    private var lastSavedParagraphIndex: Int = -1

    fun loadDocument(documentId: String) {
        viewModelScope.launch {
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
                    val rawParagraphs = splitIntoParagraphs(text)
                    val tokenized = withContext(Dispatchers.Default) {
                        textTokenizer.tokenizeParagraphs(rawParagraphs)
                    }
                    lastSavedParagraphIndex = document.lastReadParagraphIndex
                    _uiState.update {
                        it.copy(
                            document = document,
                            paragraphs = tokenized,
                            isLoading = false,
                            scrollToIndex = document.lastReadParagraphIndex,
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

    fun saveReadingPosition(paragraphIndex: Int) {
        val documentId = _uiState.value.document?.id ?: return
        if (paragraphIndex == lastSavedParagraphIndex) return
        lastSavedParagraphIndex = paragraphIndex
        viewModelScope.launch {
            documentRepository.updateReadingPosition(documentId, paragraphIndex)
        }
    }
}
