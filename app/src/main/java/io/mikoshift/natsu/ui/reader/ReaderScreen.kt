package io.mikoshift.natsu.ui.reader

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.mikoshift.natsu.R
import io.mikoshift.natsu.ui.theme.ReaderThemeWrapper
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    viewModel: ReaderViewModel,
    documentId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val readerSettings by viewModel.readerSettings.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val contentReady = !uiState.isLoading &&
        uiState.errorMessage == null &&
        uiState.paragraphs.isNotEmpty()

    LaunchedEffect(documentId) {
        viewModel.loadDocument(documentId)
    }

    LaunchedEffect(contentReady, uiState.scrollToIndex) {
        if (contentReady && uiState.scrollToIndex > 0) {
            listState.scrollToItem(
                uiState.scrollToIndex.coerceAtMost(uiState.paragraphs.lastIndex),
            )
        }
    }

    var highlightCenterY by remember { mutableStateOf<Float?>(null) }

    LaunchedEffect(contentReady, uiState.searchHighlight?.scrollRequestId) {
        val highlight = uiState.searchHighlight ?: return@LaunchedEffect
        if (!contentReady) return@LaunchedEffect

        highlightCenterY = null
        listState.scrollToItem(
            highlight.paragraphIndex.coerceAtMost(uiState.paragraphs.lastIndex),
        )
        val centerY = snapshotFlow { highlightCenterY }
            .filterNotNull()
            .first()
        val viewportHeight = listState.layoutInfo.viewportSize.height
        val scrollOffset = (centerY - viewportHeight / 2f).toInt().coerceAtLeast(0)
        listState.animateScrollToItem(highlight.paragraphIndex, scrollOffset)
    }

    LaunchedEffect(contentReady, listState) {
        if (!contentReady) return@LaunchedEffect
        snapshotFlow { listState.firstVisibleItemIndex }
            .distinctUntilChanged()
            .collect { index -> viewModel.saveReadingPosition(index) }
    }

    DisposableEffect(contentReady) {
        onDispose {
            if (contentReady) {
                viewModel.saveReadingPosition(
                    paragraphIndex = listState.firstVisibleItemIndex,
                    immediate = true,
                )
            }
        }
    }

    ReaderThemeWrapper(settings = readerSettings) {
        Scaffold(
            modifier = modifier.fillMaxSize(),
            topBar = {
                TopAppBar(
                    title = {
                        if (uiState.searchActive) {
                            ReaderSearchBar(
                                query = uiState.searchQuery,
                                matchIndex = uiState.searchMatchIndex,
                                matchCount = uiState.searchMatchOffsets.size,
                                onQueryChange = viewModel::updateSearchQuery,
                                onPreviousMatch = viewModel::goToPreviousSearchMatch,
                                onNextMatch = viewModel::goToNextSearchMatch,
                                onClose = viewModel::closeSearch,
                            )
                        } else {
                            Text(
                                text = uiState.document?.title ?: stringResource(R.string.reader_title),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    },
                    navigationIcon = {
                        if (!uiState.searchActive) {
                            IconButton(onClick = {
                                viewModel.flushReadingPosition(listState.firstVisibleItemIndex)
                                onBack()
                            }) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = stringResource(R.string.back),
                                )
                            }
                        }
                    },
                    actions = {
                        if (!uiState.searchActive && contentReady) {
                            IconButton(onClick = viewModel::openSearch) {
                                Icon(
                                    Icons.Default.Search,
                                    contentDescription = stringResource(R.string.reader_search),
                                )
                            }
                        }
                    },
                )
            },
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                when {
                    uiState.isLoading -> {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    }
                    uiState.errorMessage != null -> {
                        Text(
                            text = uiState.errorMessage ?: "",
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(24.dp),
                        )
                    }
                    else -> {
                        LazyColumn(
                            state = listState,
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        ) {
                            itemsIndexed(
                                items = uiState.paragraphs,
                                key = { index, _ -> index },
                            ) { index, tokens ->
                                val highlight = uiState.searchHighlight
                                    ?.takeIf { it.paragraphIndex == index }
                                TokenizedParagraph(
                                    tokens = tokens,
                                    settings = readerSettings,
                                    onWordClick = viewModel::onWordClicked,
                                    highlightRange = highlight?.range,
                                    onHighlightPositioned = if (highlight != null) {
                                        { y -> highlightCenterY = y }
                                    } else {
                                        null
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }

        WordDefinitionSheet(
            wordLookup = uiState.wordLookup,
            onDismiss = viewModel::dismissWordLookup,
        )
    }
}
