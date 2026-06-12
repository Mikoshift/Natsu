package io.mikoshift.natsu.ui.library

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import android.content.Context
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.mikoshift.natsu.R
import io.mikoshift.natsu.data.book.import.BookImportException
import io.mikoshift.natsu.data.book.import.CannotOpenFileException
import io.mikoshift.natsu.data.book.import.EmptyFileException
import io.mikoshift.natsu.data.book.import.UnsupportedFormatException
import io.mikoshift.natsu.data.book.import.UnsupportedTextEncodingException
import io.mikoshift.natsu.domain.model.Document
import io.mikoshift.natsu.domain.model.hasReadingProgress
import io.mikoshift.natsu.domain.model.readingProgressPercent
import io.mikoshift.natsu.ui.shell.LocalDrawerOpen
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel,
    onDocumentClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val documents by viewModel.documents.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val openDrawer = LocalDrawerOpen.current
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshDocuments()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    var documentToRename by remember { mutableStateOf<Document?>(null) }
    var documentToDelete by remember { mutableStateOf<Document?>(null) }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        uri?.let { viewModel.importBook(it, null) }
    }

    LaunchedEffect(uiState.error) {
        uiState.error?.let { error ->
            snackbarHostState.showSnackbar(resolveLibraryErrorMessage(context, error))
            viewModel.clearError()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.library_title)) },
                navigationIcon = {
                    IconButton(onClick = { openDrawer?.invoke() }) {
                        Icon(
                            Icons.Default.Menu,
                            contentDescription = stringResource(R.string.menu_open),
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { importLauncher.launch(arrayOf("text/plain", "text/*", "*/*")) },
            ) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.import_text))
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            if (documents.isEmpty() && !uiState.isImporting) {
                EmptyLibraryState(
                    modifier = Modifier.align(Alignment.Center),
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(documents, key = { it.id }) { document ->
                        DocumentListItem(
                            document = document,
                            onClick = { onDocumentClick(document.id) },
                            onRename = { documentToRename = document },
                            onDelete = { documentToDelete = document },
                        )
                    }
                }
            }

            if (uiState.isImporting) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
        }
    }

    documentToRename?.let { document ->
        RenameDocumentDialog(
            initialTitle = document.title,
            onDismiss = { documentToRename = null },
            onConfirm = { title ->
                viewModel.renameDocument(document.id, title)
                documentToRename = null
            },
        )
    }

    documentToDelete?.let { document ->
        AlertDialog(
            onDismissRequest = { documentToDelete = null },
            title = { Text(stringResource(R.string.document_delete_title)) },
            text = { Text(stringResource(R.string.document_delete_message, document.title)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteDocument(document.id)
                        documentToDelete = null
                    },
                ) {
                    Text(stringResource(R.string.document_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { documentToDelete = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun DocumentListItem(
    document: Document,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val progressPercent = document.readingProgressPercent()
    val hasProgress = document.hasReadingProgress()

    Column(modifier = Modifier.fillMaxWidth()) {
        ListItem(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick),
            headlineContent = {
                Text(
                    text = document.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            supportingContent = {
                Text(
                    text = documentSubtitle(document, progressPercent, hasProgress),
                    style = MaterialTheme.typography.bodySmall,
                )
            },
            trailingContent = {
                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = stringResource(R.string.document_actions),
                        )
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.document_rename)) },
                            onClick = {
                                menuExpanded = false
                                onRename()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.document_delete)) },
                            onClick = {
                                menuExpanded = false
                                onDelete()
                            },
                        )
                    }
                }
            },
        )
        if (progressPercent != null && progressPercent > 0) {
            LinearProgressIndicator(
                progress = { progressPercent / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            )
        }
    }
}

@Composable
private fun documentSubtitle(
    document: Document,
    progressPercent: Int?,
    hasProgress: Boolean,
): String {
    return when {
        hasProgress && progressPercent != null ->
            stringResource(
                R.string.document_continue_reading_percent,
                progressPercent,
                document.lastReadCharOffset,
            )
        hasProgress ->
            stringResource(R.string.document_continue_reading)
        else ->
            DateFormat.getDateTimeInstance().format(Date(document.importedAt))
    }
}

@Composable
private fun RenameDocumentDialog(
    initialTitle: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var title by remember(initialTitle) { mutableStateOf(initialTitle) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.document_rename)) },
        text = {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(title) },
                enabled = title.isNotBlank(),
            ) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

private fun resolveLibraryErrorMessage(context: Context, error: Throwable): String {
    val importError = error as? BookImportException ?: error.cause as? BookImportException
    return when (importError) {
        is CannotOpenFileException -> context.getString(R.string.import_error_cannot_open)
        is EmptyFileException -> context.getString(R.string.import_error_empty)
        is UnsupportedTextEncodingException -> context.getString(R.string.import_error_unsupported_encoding)
        is UnsupportedFormatException -> context.getString(R.string.import_error_unsupported_format)
        else -> context.getString(R.string.import_error_generic)
    }
}

@Composable
private fun EmptyLibraryState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.library_empty_title),
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = stringResource(R.string.library_empty_message),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
