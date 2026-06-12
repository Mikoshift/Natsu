package io.mikoshift.natsu.ui.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.mikoshift.natsu.data.reader.findMatches
import io.mikoshift.natsu.data.reader.layoutParagraphIndexForGlobalOffset
import io.mikoshift.natsu.data.reader.layoutParagraphIndexForLocator
import io.mikoshift.natsu.data.reader.layoutParagraphIndexForMatch
import io.mikoshift.natsu.data.reader.layoutParagraphStart
import io.mikoshift.natsu.data.reader.sectionIdForGlobalCharOffset
import io.mikoshift.natsu.domain.model.DictionaryEntry
import io.mikoshift.natsu.domain.model.Document
import io.mikoshift.natsu.domain.model.ReaderSettings
import io.mikoshift.natsu.domain.model.TextToken
import io.mikoshift.natsu.domain.model.reading.ReadingBlock
import io.mikoshift.natsu.domain.model.reading.ReadingBookOutline
import io.mikoshift.natsu.domain.model.reading.ReadingSection
import io.mikoshift.natsu.domain.model.reading.ReadingLocator
import io.mikoshift.natsu.domain.model.reading.SearchIndex
import io.mikoshift.natsu.domain.model.reading.contributesLayoutParagraph
import io.mikoshift.natsu.domain.model.reading.SearchMatch
import io.mikoshift.natsu.domain.model.reading.SectionReadingContent
import io.mikoshift.natsu.domain.repository.DictionaryRepository
import io.mikoshift.natsu.domain.repository.DocumentRepository
import io.mikoshift.natsu.domain.repository.ReadingContentRepository
import io.mikoshift.natsu.domain.repository.TextTokenizer
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
    val displayItems: List<ReaderDisplayItem> = emptyList(),
    val sectionNavItems: List<ReaderSectionNav> = emptyList(),
    val isLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val errorMessage: String? = null,
    val scrollToDisplayIndex: Int = 0,
    val scrollRequestId: Long = 0L,
    val paragraphStartOffsets: List<Int> = emptyList(),
    val wordLookup: WordLookupState = WordLookupState.Hidden,
    val searchActive: Boolean = false,
    val searchQuery: String = "",
    val searchMatches: List<SearchMatch> = emptyList(),
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

    private var lastSavedGlobalCharOffset: Int = -1
    private var lastSavedParagraphIndex: Int = -1
    private var lastSavedLocator: ReadingLocator? = null
    private var saveJob: Job? = null
    private var loadJob: Job? = null
    private var pendingLoadDocumentId: String? = null
    private var searchJob: Job? = null
    private var searchScrollRequestId: Long = 0L
    private var outline: ReadingBookOutline? = null
    private var bookStoragePath: String = ""
    private val loadedSections = linkedMapOf<String, SectionReadingContent>()
    private val loadedSectionItems = linkedMapOf<String, List<ReaderDisplayItem>>()

    fun loadDocument(documentId: String) {
        loadJob?.cancel()
        pendingLoadDocumentId = documentId
        loadedSections.clear()
        loadedSectionItems.clear()
        outline = null
        loadJob = viewModelScope.launch {
            saveJob?.cancel()
            val requestedDocumentId = documentId
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val document = documentRepository.getDocument(requestedDocumentId)
            if (document == null) {
                if (pendingLoadDocumentId != requestedDocumentId) return@launch
                _uiState.update {
                    it.copy(isLoading = false, errorMessage = "Document not found")
                }
                return@launch
            }

            readingContentRepository.loadOutline(requestedDocumentId)
                .onSuccess { bookOutline ->
                    if (pendingLoadDocumentId != requestedDocumentId) return@onSuccess
                    outline = bookOutline
                    bookStoragePath = document.storagePath
                    documentRepository.ensureCharCount(
                        requestedDocumentId,
                        bookOutline.searchIndex.totalCharCount,
                    )
                    val initialSectionId = resolveInitialSectionId(document, bookOutline.searchIndex)
                    if (!ensureSectionLoaded(requestedDocumentId, initialSectionId)) {
                        _uiState.update {
                            it.copy(isLoading = false, errorMessage = LOAD_ERROR_MESSAGE)
                        }
                        return@onSuccess
                    }
                    val displayItems = mergedDisplayItems()
                    val scrollLayoutIndex = resolveScrollLayoutIndex(document, bookOutline.searchIndex)
                    val scrollDisplayIndex = ReaderDisplayBuilder.displayIndexForLayoutParagraph(
                        items = displayItems,
                        layoutParagraphIndex = scrollLayoutIndex,
                    )
                    lastSavedGlobalCharOffset = document.lastReadCharOffset
                    lastSavedParagraphIndex = document.lastReadParagraphIndex
                    lastSavedLocator = document.lastReadLocator
                    publishReaderState(
                        document = document,
                        outline = bookOutline,
                        displayItems = displayItems,
                        scrollDisplayIndex = scrollDisplayIndex,
                        isLoading = false,
                    )
                }
                .onFailure {
                    if (pendingLoadDocumentId != requestedDocumentId) return@onFailure
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = LOAD_ERROR_MESSAGE,
                        )
                    }
                }
        }
    }

    fun onNearEnd(displayIndex: Int) {
        val bookOutline = outline ?: return
        val documentId = _uiState.value.document?.id ?: return
        if (_uiState.value.isLoadingMore) return

        val items = _uiState.value.displayItems
        if (items.isEmpty()) return
        if (displayIndex < items.lastIndex - NEAR_END_THRESHOLD) return

        val nextSectionId = bookOutline.manifest.sections
            .map { it.id }
            .dropWhile { it != loadedSections.keys.lastOrNull() }
            .drop(1)
            .firstOrNull()
            ?: return
        if (loadedSections.containsKey(nextSectionId)) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMore = true) }
            if (ensureSectionLoaded(documentId, nextSectionId)) {
                publishReaderState(
                    document = _uiState.value.document ?: return@launch,
                    outline = bookOutline,
                    displayItems = mergedDisplayItems(),
                    scrollDisplayIndex = displayIndex,
                    isLoading = false,
                )
            }
            _uiState.update { it.copy(isLoadingMore = false) }
        }
    }

    fun navigateToSection(section: ReaderSectionNav) {
        val documentId = _uiState.value.document?.id ?: return
        viewModelScope.launch {
            if (!ensureSectionLoaded(documentId, section.id)) return@launch
            val displayItems = mergedDisplayItems()
            val displayIndex = ReaderDisplayBuilder.displayIndexForLayoutParagraph(
                items = displayItems,
                layoutParagraphIndex = section.startLayoutParagraphIndex,
            )
            publishReaderState(
                document = _uiState.value.document ?: return@launch,
                outline = outline ?: return@launch,
                displayItems = displayItems,
                scrollDisplayIndex = displayIndex,
                isLoading = false,
                bumpScroll = true,
            )
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
                searchMatches = emptyList(),
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
                searchMatches = emptyList(),
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
                searchMatches = if (query.isEmpty()) emptyList() else it.searchMatches,
            )
        }
        searchJob?.cancel()
        if (query.isEmpty()) return
        val searchIndex = outline?.searchIndex ?: return
        searchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            val matches = withContext(Dispatchers.Default) {
                searchIndex.findMatches(query)
            }
            _uiState.update { state ->
                if (state.searchQuery != query) return@update state
                state.copy(
                    searchMatches = matches,
                    searchMatchIndex = 0,
                    searchHighlight = highlightForMatch(matches, matchIndex = 0),
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
        val matches = state.searchMatches
        if (matches.isEmpty()) return

        val nextIndex = (state.searchMatchIndex + delta).mod(matches.size)
        val match = matches[nextIndex]
        viewModelScope.launch {
            ensureSectionLoaded(state.document?.id ?: return@launch, match.locator.sectionId)
            val displayItems = mergedDisplayItems()
            val paragraphIndex = outline?.searchIndex?.layoutParagraphIndexForMatch(match) ?: 0
            val displayIndex = ReaderDisplayBuilder.displayIndexForLayoutParagraph(
                items = displayItems,
                layoutParagraphIndex = paragraphIndex,
            )
            publishReaderState(
                document = state.document ?: return@launch,
                outline = outline ?: return@launch,
                displayItems = displayItems,
                scrollDisplayIndex = displayIndex,
                isLoading = false,
                bumpScroll = true,
                searchHighlight = highlightForMatch(matches, nextIndex),
                searchMatchIndex = nextIndex,
            )
        }
    }

    private fun highlightForMatch(
        matches: List<SearchMatch>,
        matchIndex: Int,
    ): SearchHighlight? {
        val match = matches.getOrNull(matchIndex) ?: return null
        val searchIndex = outline?.searchIndex ?: return null
        val paragraphIndex = searchIndex.layoutParagraphIndexForMatch(match)
        return SearchHighlight(
            paragraphIndex = paragraphIndex,
            range = match.localRange,
            scrollRequestId = ++searchScrollRequestId,
        )
    }

    fun saveReadingPosition(displayIndex: Int, immediate: Boolean = false) {
        val state = _uiState.value
        val documentId = state.document?.id ?: return
        if (state.displayItems.isEmpty()) return

        val position = readingPositionForDisplayIndex(state, displayIndex) ?: return
        if (!immediate &&
            position.globalCharOffset == lastSavedGlobalCharOffset &&
            position.paragraphIndex == lastSavedParagraphIndex &&
            position.locator == lastSavedLocator
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
                position = position,
                notifyLibrary = immediate,
            )
        }
    }

    fun flushReadingPosition(displayIndex: Int) {
        saveJob?.cancel()
        val state = _uiState.value
        val documentId = state.document?.id ?: return
        if (state.displayItems.isEmpty()) return
        val position = readingPositionForDisplayIndex(state, displayIndex) ?: return

        viewModelScope.launch {
            persistReadingPosition(
                documentId = documentId,
                position = position,
                notifyLibrary = true,
            )
        }
    }

    private fun tokenizeSectionBlocks(section: ReadingSection): List<List<TextToken>> {
        return section.blocks
            .filter { it.contributesLayoutParagraph() }
            .map { block ->
                when (block) {
                    is ReadingBlock.Paragraph -> textTokenizer.tokenizeParagraph(block.spans)
                    is ReadingBlock.Heading -> textTokenizer.tokenize(block.text)
                    is ReadingBlock.Image -> emptyList()
                }
            }
    }

    private suspend fun ensureSectionLoaded(documentId: String, sectionId: String): Boolean {
        if (loadedSections.containsKey(sectionId)) return true
        val sectionContent = readingContentRepository.loadSection(documentId, sectionId).getOrNull()
            ?: return false
        val tokenized = withContext(Dispatchers.Default) {
            tokenizeSectionBlocks(sectionContent.section)
        }
        val globalOffset = outline?.searchIndex?.layoutParagraphStart(sectionId) ?: 0
        val items = ReaderDisplayBuilder.buildSectionItems(
            section = sectionContent.section,
            bookStoragePath = bookStoragePath,
            tokenizedParagraphs = tokenized,
            globalLayoutParagraphOffset = globalOffset,
        )
        loadedSections[sectionId] = sectionContent
        loadedSectionItems[sectionId] = items
        return true
    }

    private fun mergedDisplayItems(): List<ReaderDisplayItem> {
        val outline = outline ?: return emptyList()
        return outline.manifest.sections.mapNotNull { section ->
            loadedSectionItems[section.id]
        }.flatten()
    }

    private fun publishReaderState(
        document: Document,
        outline: ReadingBookOutline,
        displayItems: List<ReaderDisplayItem>,
        scrollDisplayIndex: Int,
        isLoading: Boolean,
        bumpScroll: Boolean = false,
        searchHighlight: SearchHighlight? = _uiState.value.searchHighlight,
        searchMatchIndex: Int = _uiState.value.searchMatchIndex,
    ) {
        _uiState.update {
            it.copy(
                document = document,
                displayItems = displayItems,
                sectionNavItems = ReaderDisplayBuilder.buildSectionNav(outline),
                isLoading = isLoading,
                scrollToDisplayIndex = scrollDisplayIndex,
                scrollRequestId = if (bumpScroll) it.scrollRequestId + 1 else it.scrollRequestId,
                paragraphStartOffsets = outline.searchIndex.paragraphs.map { paragraph ->
                    paragraph.globalCharOffset
                },
                searchHighlight = searchHighlight,
                searchMatchIndex = searchMatchIndex,
            )
        }
    }

    private fun resolveInitialSectionId(document: Document, searchIndex: SearchIndex): String {
        document.lastReadLocator?.sectionId?.let { return it }
        if (document.lastReadCharOffset > 0) {
            searchIndex.sectionIdForGlobalCharOffset(document.lastReadCharOffset)?.let { return it }
        }
        return searchIndex.sectionOffsets.firstOrNull()?.sectionId
            ?: searchIndex.paragraphs.firstOrNull()?.sectionId
            ?: "main"
    }

    private fun resolveScrollLayoutIndex(document: Document, searchIndex: SearchIndex): Int {
        document.lastReadLocator?.let { locator ->
            return searchIndex.layoutParagraphIndexForLocator(locator)
        }
        return when {
            document.lastReadCharOffset > 0 ->
                searchIndex.layoutParagraphIndexForGlobalOffset(document.lastReadCharOffset)
            document.lastReadParagraphIndex > 0 ->
                document.lastReadParagraphIndex.coerceAtMost(
                    searchIndex.paragraphs.lastIndex.coerceAtLeast(0),
                )
            else -> 0
        }
    }

    private fun readingPositionForDisplayIndex(
        state: ReaderUiState,
        displayIndex: Int,
    ): SavedReadingPosition? {
        val searchIndex = outline?.searchIndex ?: return null
        val safeDisplayIndex = displayIndex.coerceIn(0, state.displayItems.lastIndex)
        val layoutParagraphIndex = ReaderDisplayBuilder.layoutParagraphForDisplayIndex(
            items = state.displayItems,
            displayIndex = safeDisplayIndex,
        )
        val paragraph = searchIndex.paragraphs.getOrNull(layoutParagraphIndex) ?: return null
        val item = state.displayItems.getOrNull(safeDisplayIndex)
        return SavedReadingPosition(
            globalCharOffset = paragraph.globalCharOffset,
            paragraphIndex = layoutParagraphIndex,
            locator = ReadingLocator(
                sectionId = paragraph.sectionId,
                blockIndex = paragraph.blockIndex,
                charOffset = 0,
            ).takeIf { item != null },
        )
    }

    private suspend fun persistReadingPosition(
        documentId: String,
        position: SavedReadingPosition,
        notifyLibrary: Boolean,
    ) {
        if (position.globalCharOffset == lastSavedGlobalCharOffset &&
            position.paragraphIndex == lastSavedParagraphIndex &&
            position.locator == lastSavedLocator &&
            !notifyLibrary
        ) {
            return
        }
        lastSavedGlobalCharOffset = position.globalCharOffset
        lastSavedParagraphIndex = position.paragraphIndex
        lastSavedLocator = position.locator
        runCatching {
            documentRepository.updateReadingPosition(
                documentId = documentId,
                globalCharOffset = position.globalCharOffset,
                paragraphIndex = position.paragraphIndex,
                locator = position.locator,
            )
            if (notifyLibrary) {
                documentRepository.notifyDocumentsChanged()
            }
        }
    }

    private data class SavedReadingPosition(
        val globalCharOffset: Int,
        val paragraphIndex: Int,
        val locator: ReadingLocator?,
    )

    companion object {
        private const val READING_POSITION_SAVE_DELAY_MS = 400L
        private const val SEARCH_DEBOUNCE_MS = 250L
        private const val NEAR_END_THRESHOLD = 3
        const val LOAD_ERROR_MESSAGE = "Could not load this book"
    }
}
