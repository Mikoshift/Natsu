package io.mikoshift.natsu.ui.dictionaries

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.mikoshift.natsu.R
import io.mikoshift.natsu.domain.model.DictionaryInstallState
import io.mikoshift.natsu.domain.model.InstalledDictionary
import io.mikoshift.natsu.domain.repository.PriorityDirection
import io.mikoshift.natsu.ui.shell.LocalDrawerOpen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DictionariesScreen(
    viewModel: DictionariesViewModel,
    modifier: Modifier = Modifier,
) {
    val dictionaries by viewModel.dictionaries.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val openDrawer = LocalDrawerOpen.current

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearError()
        }
    }

    val installedItems = dictionaries.filter { it.installState == DictionaryInstallState.Installed }
    val hasInstalledDictionary = installedItems.isNotEmpty()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.dictionaries_title)) },
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
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        if (dictionaries.isEmpty()) {
            Text(
                text = stringResource(R.string.dictionary_empty_hint),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(24.dp),
                style = MaterialTheme.typography.bodyLarge,
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (!hasInstalledDictionary) {
                    item {
                        Text(
                            text = stringResource(R.string.dictionary_empty_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                items(dictionaries, key = { it.catalogId }) { dictionary ->
                    DictionaryCard(
                        dictionary = dictionary,
                        canMoveUp = dictionary.installState == DictionaryInstallState.Installed &&
                            installedItems.indexOfFirst { it.catalogId == dictionary.catalogId } > 0,
                        canMoveDown = dictionary.installState == DictionaryInstallState.Installed &&
                            installedItems.indexOfFirst { it.catalogId == dictionary.catalogId } <
                            installedItems.lastIndex,
                        onDownload = { viewModel.download(dictionary.catalogId) },
                        onDelete = { viewModel.delete(dictionary.catalogId) },
                        onEnabledChange = { enabled ->
                            viewModel.setEnabled(dictionary.catalogId, enabled)
                        },
                        onMoveUp = { viewModel.movePriority(dictionary.catalogId, PriorityDirection.Up) },
                        onMoveDown = { viewModel.movePriority(dictionary.catalogId, PriorityDirection.Down) },
                    )
                }
            }
        }
    }
}

@Composable
private fun DictionaryCard(
    dictionary: InstalledDictionary,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onDownload: () -> Unit,
    onDelete: () -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(text = dictionary.name, style = MaterialTheme.typography.titleMedium)
            Text(
                text = dictionary.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (dictionary.sizeHintMb > 0) {
                Text(
                    text = "~${dictionary.sizeHintMb} MB",
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            when (dictionary.installState) {
                DictionaryInstallState.NotInstalled -> {
                    Button(onClick = onDownload, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.dictionary_download))
                    }
                }
                DictionaryInstallState.Downloading -> {
                    val isInstalling = dictionary.downloadProgress >= 0.99f
                    Text(
                        stringResource(
                            if (isInstalling) R.string.dictionary_installing
                            else R.string.dictionary_downloading,
                        ),
                    )
                    LinearProgressIndicator(
                        progress = { dictionary.downloadProgress.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (!isInstalling) {
                        Text(
                            text = "${(dictionary.downloadProgress * 100).toInt()}%",
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
                DictionaryInstallState.Installed -> {
                    dictionary.title?.let { title ->
                        Text(
                            text = title,
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(stringResource(R.string.dictionary_enabled))
                        Switch(
                            checked = dictionary.enabled,
                            onCheckedChange = onEnabledChange,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        IconButton(onClick = onMoveUp, enabled = canMoveUp) {
                            Icon(Icons.Default.ArrowUpward, contentDescription = null)
                        }
                        IconButton(onClick = onMoveDown, enabled = canMoveDown) {
                            Icon(Icons.Default.ArrowDownward, contentDescription = null)
                        }
                        OutlinedButton(onClick = onDelete) {
                            Text(stringResource(R.string.dictionary_delete))
                        }
                    }
                }
            }
        }
    }
}
