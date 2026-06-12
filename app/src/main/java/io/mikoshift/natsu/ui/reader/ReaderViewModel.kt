package io.mikoshift.natsu.ui.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.mikoshift.natsu.data.book.html.HtmlChapterResolver
import io.mikoshift.natsu.data.reader.findMatches
import io.mikoshift.natsu.data.reader.globalCharOffsetForLocator
import io.mikoshift.natsu.data.reader.layoutParagraphIndexForGlobalOffset
import io.mikoshift.natsu.data.reader.layoutParagraphIndexForLocator
import io.mikoshift.natsu.data.reader.sectionIdForGlobalCharOffset
import io.mikoshift.natsu.domain.model.DictionaryEntry
import io.mikoshift.natsu.domain.model.Document
import io.mikoshift.natsu.domain.model.FuriganaMode
import io.mikoshift.natsu.domain.model.ReaderSettings
import io.mikoshift.natsu.domain.model.TextToken
import io.mikoshift.natsu.domain.model.reading.ReadingBlock
import io.mikoshift.natsu.domain.model.reading.ReadingBookOutline
import io.mikoshift.natsu.domain.model.reading.ReadingLocator
import io.mikoshift.natsu.domain.model.reading.SearchIndex
import io.mikoshift.natsu.domain.model.reading.SearchMatch
import io.mikoshift.natsu.domain.model.reading.SectionReadingContent
import io.mikoshift.natsu.domain.repository.DictionaryRepository
import io.mikoshift.natsu.domain.repository.DocumentRepository
import io.mikoshift.natsu.domain.repository.ReadingContentRepository
import io.mikoshift.natsu.domain.repository.TextTokenizer
import io.mikoshift.natsu.data.settings.ReaderSettingsStore
import io.mikoshift.natsu.ui.reader.web.ReaderSectionTokenCache
import io.mikoshift.natsu.ui.reader.web.ReaderWebUrls
import io.mikoshift.natsu.ui.reader.web.ReaderWordTap
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
import java.io.File

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
    val bookDocumentId: String? = null,
    val bookStoragePath: String? = null,
    val chapterUrl: String? = null,
    val currentSectionId: String? = null,
    val sectionNavItems: List<ReaderSectionNav> = emptyList(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val scrollToCharOffset: Int? = null,
    val scrollRequestId: Long = 0L,
    val searchHighlightRanges: List<IntRange> = emptyList(),
    val furiganaTokens: List<FuriganaInjectToken> = emptyList(),
    val wordLookup: WordLookupState = WordLookupState.Hidden,
    val searchActive: Boolean = false,
    val searchQuery: String = "",
    val searchMatches: List<SearchMatch> = emptyList(),
    val searchMatchIndex: Int = 0,
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
    private var outline: ReadingBookOutline? = null
    private val loadedSections = linkedMapOf<String, SectionReadingContent>()
    private val sectionTokenCache = ReaderSectionTokenCache()

    fun loadDocument(documentId: String) {
        loadJob?.cancel()
        pendingLoadDocumentId = documentId
        loadedSections.clear()
        sectionTokenCache.clear()
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
                    documentRepository.ensureCharCount(
                        requestedDocumentId,
                        bookOutline.searchIndex.totalCharCount,
                    )
                    val initialSectionId = resolveInitialSectionId(document, bookOutline.searchIndex)
                    if (!openSection(
                            documentId = requestedDocumentId,
                            document = document,
                            outline = bookOutline,
                            sectionId = initialSectionId,
                            scrollToSavedPosition = true,
                        )
                    ) {
                        _uiState.update {
                            it.copy(isLoading = false, errorMessage = LOAD_ERROR_MESSAGE)
                        }
                        return@onSuccess
                    }
                    lastSavedGlobalCharOffset = document.lastReadCharOffset
                    lastSavedParagraphIndex = document.lastReadParagraphIndex
                    lastSavedLocator = document.lastReadLocator
                    _uiState.update {
                        it.copy(isLoading = false)
                    }
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

    fun navigateToSection(section: ReaderSectionNav) {
        val documentId = _uiState.value.document?.id ?: return
        val document = _uiState.value.document ?: return
        val bookOutline = outline ?: return
        viewModelScope.launch {
            openSection(
                documentId = documentId,
                document = document,
                outline = bookOutline,
                sectionId = section.id,
                scrollToCharOffset = 0,
                bumpScroll = true,
            )
        }
    }

    fun onChapterLink(relativePath: String) {
        val bookOutline = outline ?: return
        val bookDir = File(_uiState.value.bookStoragePath ?: return)
        val section = bookOutline.manifest.sections.firstOrNull { manifestSection ->
            manifestSection.path == relativePath ||
                HtmlChapterResolver.resolveRelativePath(bookDir, manifestSection) == relativePath
        } ?: return
        navigateToSection(
            ReaderSectionNav(
                id = section.id,
                title = section.title ?: section.id,
                startLayoutParagraphIndex = 0,
            ),
        )
    }

    fun onWebWordTap(paragraphText: String, charOffset: Int) {
        if (paragraphText.isBlank()) return
        val sectionId = _uiState.value.currentSectionId ?: return
        val tokens = sectionTokenCache.tokens(sectionId, paragraphText, textTokenizer::tokenize)
        val token = ReaderWordTap.resolveTapToken(tokens, charOffset) ?: return
        onWordClicked(token)
    }

    fun onWebScrollProgress(ratio: Float) {
        val state = _uiState.value
        val searchIndex = outline?.searchIndex ?: return
        val sectionId = state.currentSectionId ?: return
        val sectionOffset = searchIndex.sectionOffsets.firstOrNull { it.sectionId == sectionId } ?: return
        val charOffsetInSection = (ratio * sectionOffset.charCount).toInt()
            .coerceIn(0, sectionOffset.charCount)
        val globalOffset = sectionOffset.globalCharOffset + charOffsetInSection
        val paragraphIndex = searchIndex.layoutParagraphIndexForGlobalOffset(globalOffset)
        val locator = searchIndex.paragraphs.getOrNull(paragraphIndex)?.let { paragraph ->
            ReadingLocator(
                sectionId = paragraph.sectionId,
                blockIndex = paragraph.blockIndex,
                charOffset = (globalOffset - paragraph.globalCharOffset).coerceAtLeast(0),
            )
        }
        saveReadingPosition(
            globalCharOffset = globalOffset,
            paragraphIndex = paragraphIndex,
            locator = locator,
        )
    }

    fun onWebChapterReady() {
        val state = _uiState.value
        val searchIndex = outline?.searchIndex ?: return
        val sectionId = state.currentSectionId ?: return
        viewModelScope.launch {
            val furiganaTokens = withContext(Dispatchers.Default) {
                buildFuriganaTokens(sectionId)
            }
            val activeHighlight = activeSearchHighlightForSection(sectionId)
            _uiState.update {
                it.copy(
                    furiganaTokens = furiganaTokens,
                    searchHighlightRanges = activeHighlight,
                )
            }
            if (state.scrollToCharOffset != null) {
                _uiState.update { it.copy(scrollRequestId = it.scrollRequestId + 1) }
            }
        }
    }

    fun flushReadingPosition() {
        saveJob?.cancel()
        val documentId = _uiState.value.document?.id ?: return
        val position = currentReadingPosition() ?: return
        viewModelScope.launch {
            persistReadingPosition(
                documentId = documentId,
                position = position,
                notifyLibrary = true,
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
                searchHighlightRanges = emptyList(),
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
                searchHighlightRanges = emptyList(),
            )
        }
    }

    fun updateSearchQuery(query: String) {
        _uiState.update {
            it.copy(
                searchQuery = query,
                searchMatchIndex = 0,
                searchHighlightRanges = emptyList(),
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
                )
            }
            if (matches.isNotEmpty()) {
                navigateSearchMatch(delta = 0, matches = matches, matchIndex = 0)
            }
        }
    }

    fun goToNextSearchMatch() {
        navigateSearchMatch(delta = 1)
    }

    fun goToPreviousSearchMatch() {
        navigateSearchMatch(delta = -1)
    }

    private fun navigateSearchMatch(
        delta: Int,
        matches: List<SearchMatch>? = null,
        matchIndex: Int? = null,
    ) {
        val state = _uiState.value
        val resolvedMatches = matches ?: state.searchMatches
        if (resolvedMatches.isEmpty()) return

        val nextIndex = matchIndex ?: (state.searchMatchIndex + delta).mod(resolvedMatches.size)
        val match = resolvedMatches[nextIndex]
        val documentId = state.document?.id ?: return
        val document = state.document ?: return
        val bookOutline = outline ?: return

        viewModelScope.launch {
            openSection(
                documentId = documentId,
                document = document,
                outline = bookOutline,
                sectionId = match.locator.sectionId,
                scrollToCharOffset = sectionLocalOffsetForMatch(match),
                bumpScroll = true,
                searchHighlightRanges = listOf(sectionLocalRangeForMatch(match)),
                searchMatchIndex = nextIndex,
            )
        }
    }

    private suspend fun openSection(
        documentId: String,
        document: Document,
        outline: ReadingBookOutline,
        sectionId: String,
        scrollToSavedPosition: Boolean = false,
        scrollToCharOffset: Int? = null,
        bumpScroll: Boolean = false,
        searchHighlightRanges: List<IntRange> = emptyList(),
        searchMatchIndex: Int = _uiState.value.searchMatchIndex,
    ): Boolean {
        if (!ensureSectionLoaded(documentId, sectionId)) return false
        val manifestSection = outline.manifest.sections.firstOrNull { it.id == sectionId }
            ?: return false
        val bookDir = File(document.storagePath)
        val chapterPath = HtmlChapterResolver.resolveRelativePath(bookDir, manifestSection)
        val chapterUrl = ReaderWebUrls.chapterUrl(documentId, chapterPath)
        val resolvedScrollOffset = when {
            scrollToCharOffset != null -> scrollToCharOffset
            scrollToSavedPosition -> resolveSectionLocalScrollOffset(document, outline.searchIndex, sectionId)
            else -> 0
        }
        _uiState.update {
            it.copy(
                document = document,
                bookDocumentId = documentId,
                bookStoragePath = document.storagePath,
                chapterUrl = chapterUrl,
                currentSectionId = sectionId,
                sectionNavItems = ReaderDisplayBuilder.buildSectionNav(outline),
                scrollToCharOffset = resolvedScrollOffset,
                scrollRequestId = if (bumpScroll || scrollToSavedPosition) {
                    it.scrollRequestId + 1
                } else {
                    it.scrollRequestId
                },
                searchHighlightRanges = searchHighlightRanges,
                searchMatchIndex = searchMatchIndex,
                furiganaTokens = emptyList(),
            )
        }
        return true
    }

    private suspend fun ensureSectionLoaded(documentId: String, sectionId: String): Boolean {
        if (loadedSections.containsKey(sectionId)) return true
        val sectionContent = readingContentRepository.loadSection(documentId, sectionId).getOrNull()
            ?: return false
        loadedSections[sectionId] = sectionContent
        sectionTokenCache.warm(
            sectionId = sectionId,
            paragraphs = sectionContent.layout.paragraphs,
            tokenize = textTokenizer::tokenize,
        )
        return true
    }

    private fun buildFuriganaTokens(sectionId: String): List<FuriganaInjectToken> {
        if (readerSettings.value.furiganaMode == FuriganaMode.OFF) return emptyList()
        val sectionContent = loadedSections[sectionId] ?: return emptyList()
        val layout = sectionContent.layout
        val section = sectionContent.section
        val tokens = mutableListOf<FuriganaInjectToken>()

        layout.paragraphs.forEachIndexed { paragraphIndex, _ ->
            val paragraphStart = layout.paragraphStartOffsets[paragraphIndex]
            val blockIndex = layout.blockIndexByParagraph[paragraphIndex]
            val block = section.blocks[blockIndex]
            var cursor = 0
            val morphemes = when (block) {
                is ReadingBlock.Paragraph -> textTokenizer.tokenizeParagraph(block.spans)
                is ReadingBlock.Heading -> textTokenizer.tokenize(block.text)
                is ReadingBlock.Image -> emptyList()
            }
            morphemes.forEach { token ->
                if (shouldShowFurigana(token)) {
                    tokens += FuriganaInjectToken(
                        surface = token.surface,
                        reading = furiganaReading(token),
                        start = paragraphStart + cursor,
                        end = paragraphStart + cursor + token.surface.length,
                    )
                }
                cursor += token.surface.length
            }
        }
        return tokens
    }

    private fun activeSearchHighlightForSection(sectionId: String): List<IntRange> {
        val state = _uiState.value
        val match = state.searchMatches.getOrNull(state.searchMatchIndex) ?: return emptyList()
        if (match.locator.sectionId != sectionId) return emptyList()
        return listOf(sectionLocalRangeForMatch(match))
    }

    private fun sectionLocalRangeForMatch(match: SearchMatch): IntRange {
        val searchIndex = outline?.searchIndex ?: return match.localRange
        val sectionOffset = searchIndex.sectionOffsets
            .firstOrNull { it.sectionId == match.locator.sectionId }
            ?.globalCharOffset
            ?: return match.localRange
        val start = match.globalCharOffset - sectionOffset
        val matchLength = match.localRange.last - match.localRange.first + 1
        return start until (start + matchLength)
    }

    private fun sectionLocalOffsetForMatch(match: SearchMatch): Int {
        return sectionLocalRangeForMatch(match).first.coerceAtLeast(0)
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

    private fun resolveSectionLocalScrollOffset(
        document: Document,
        searchIndex: SearchIndex,
        sectionId: String,
    ): Int {
        val sectionOffset = searchIndex.sectionOffsets.firstOrNull { it.sectionId == sectionId }
            ?: return 0
        document.lastReadLocator?.let { locator ->
            if (locator.sectionId == sectionId) {
                return searchIndex.globalCharOffsetForLocator(locator)
                    ?.minus(sectionOffset.globalCharOffset)
                    ?.coerceAtLeast(0)
                    ?: 0
            }
        }
        if (document.lastReadCharOffset > 0) {
            val sectionStart = sectionOffset.globalCharOffset
            val sectionEnd = sectionStart + sectionOffset.charCount
            if (document.lastReadCharOffset in sectionStart..sectionEnd) {
                return document.lastReadCharOffset - sectionStart
            }
        }
        return 0
    }

    private fun currentReadingPosition(): SavedReadingPosition? {
        val searchIndex = outline?.searchIndex ?: return null
        val state = _uiState.value
        val sectionId = state.currentSectionId ?: return null
        val sectionOffset = searchIndex.sectionOffsets.firstOrNull { it.sectionId == sectionId } ?: return null
        val scrollOffset = state.scrollToCharOffset ?: 0
        val globalOffset = sectionOffset.globalCharOffset + scrollOffset
        val paragraphIndex = searchIndex.layoutParagraphIndexForGlobalOffset(globalOffset)
        val paragraph = searchIndex.paragraphs.getOrNull(paragraphIndex)
        return SavedReadingPosition(
            globalCharOffset = globalOffset,
            paragraphIndex = paragraphIndex,
            locator = paragraph?.let {
                ReadingLocator(
                    sectionId = it.sectionId,
                    blockIndex = it.blockIndex,
                    charOffset = (globalOffset - it.globalCharOffset).coerceAtLeast(0),
                )
            },
        )
    }

    private fun saveReadingPosition(
        globalCharOffset: Int,
        paragraphIndex: Int,
        locator: ReadingLocator?,
        immediate: Boolean = false,
    ) {
        val documentId = _uiState.value.document?.id ?: return
        if (!immediate &&
            globalCharOffset == lastSavedGlobalCharOffset &&
            paragraphIndex == lastSavedParagraphIndex &&
            locator == lastSavedLocator
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
                position = SavedReadingPosition(
                    globalCharOffset = globalCharOffset,
                    paragraphIndex = paragraphIndex,
                    locator = locator,
                ),
                notifyLibrary = immediate,
            )
        }
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
        const val LOAD_ERROR_MESSAGE = "Could not load this book"
    }
}
