package io.mikoshift.natsu.ui.reader

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.mikoshift.natsu.R
import io.mikoshift.natsu.ui.reader.web.ReaderWebView
import io.mikoshift.natsu.ui.reader.web.ReaderWebViewController
import io.mikoshift.natsu.ui.theme.ReaderThemeWrapper
import java.io.File

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
    val webViewController = remember { ReaderWebViewController() }
    val contentReady = !uiState.isLoading &&
        uiState.errorMessage == null &&
        uiState.chapterUrl != null &&
        uiState.bookStoragePath != null &&
        uiState.bookDocumentId != null

    LaunchedEffect(documentId) {
        viewModel.loadDocument(documentId)
    }

    DisposableEffect(contentReady) {
        onDispose {
            if (contentReady) {
                viewModel.flushReadingPosition()
            }
        }
    }

    var sectionMenuExpanded by remember { mutableStateOf(false) }

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
                                matchCount = uiState.searchMatches.size,
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
                                viewModel.flushReadingPosition()
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
                            if (uiState.sectionNavItems.isNotEmpty()) {
                                Box {
                                    IconButton(onClick = { sectionMenuExpanded = true }) {
                                        Icon(
                                            Icons.Default.List,
                                            contentDescription = stringResource(R.string.reader_sections),
                                        )
                                    }
                                    DropdownMenu(
                                        expanded = sectionMenuExpanded,
                                        onDismissRequest = { sectionMenuExpanded = false },
                                    ) {
                                        uiState.sectionNavItems.forEach { section ->
                                            DropdownMenuItem(
                                                text = { Text(section.title) },
                                                onClick = {
                                                    sectionMenuExpanded = false
                                                    viewModel.navigateToSection(section)
                                                },
                                            )
                                        }
                                    }
                                }
                            }
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
                    contentReady -> {
                        ReaderWebView(
                            bookDir = File(uiState.bookStoragePath!!),
                            documentId = uiState.bookDocumentId!!,
                            chapterUrl = uiState.chapterUrl,
                            readerSettings = readerSettings,
                            scrollToCharOffset = uiState.scrollToCharOffset,
                            scrollRequestId = uiState.scrollRequestId,
                            searchHighlightRanges = uiState.searchHighlightRanges,
                            furiganaTokens = uiState.furiganaTokens,
                            controller = webViewController,
                            onWordTap = { text, _, _ -> viewModel.onWebWordTap(text) },
                            onScrollProgress = viewModel::onWebScrollProgress,
                            onChapterReady = viewModel::onWebChapterReady,
                            onChapterLink = viewModel::onChapterLink,
                            modifier = Modifier.fillMaxSize(),
                        )
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
