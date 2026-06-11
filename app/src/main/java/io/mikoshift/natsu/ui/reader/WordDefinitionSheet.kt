package io.mikoshift.natsu.ui.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.mikoshift.natsu.R
import io.mikoshift.natsu.domain.model.DictionaryEntry
import io.mikoshift.natsu.domain.model.DictionarySense
import io.mikoshift.natsu.domain.model.SenseBlock
import io.mikoshift.natsu.domain.model.TextToken

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WordDefinitionSheet(
    wordLookup: WordLookupState,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (wordLookup == WordLookupState.Hidden) return

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier,
    ) {
        when (wordLookup) {
            WordLookupState.Hidden -> Unit
            WordLookupState.Loading -> {
                CircularProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                )
            }
            is WordLookupState.Found -> {
                DictionaryEntryContent(entry = wordLookup.entry)
            }
            is WordLookupState.NotFound -> {
                NotFoundContent(
                    token = wordLookup.token,
                    suggestInstallDictionary = wordLookup.suggestInstallDictionary,
                )
            }
        }
    }
}

@Composable
private fun DictionaryEntryContent(
    entry: DictionaryEntry,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                text = entry.querySurface.ifBlank { entry.queryLemma },
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            if (entry.queryReading.isNotBlank()) {
                Text(
                    text = entry.queryReading,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        itemsIndexed(entry.senses) { index, sense ->
            SenseItem(index = index + 1, sense = sense)
        }
    }
}

@Composable
private fun SenseItem(
    index: Int,
    sense: DictionarySense,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (index > 1) {
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
        }
        Text(
            text = stringResource(R.string.dictionary_source_label, sense.dictionaryTitle),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (sense.partsOfSpeech.isNotEmpty()) {
            Text(
                text = sense.partsOfSpeech.joinToString(" · "),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        sense.senseBlocks.forEachIndexed { blockIndex, block ->
            SenseBlockItem(
                number = if (sense.senseBlocks.size > 1) blockIndex + 1 else null,
                block = block,
            )
        }
    }
}

@Composable
private fun SenseBlockItem(
    number: Int?,
    block: SenseBlock,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        block.definitions.forEach { definition ->
            Text(
                text = buildString {
                    if (number != null) append("$number. ")
                    append(definition)
                },
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        block.exampleJapanese?.let { example ->
            Text(
                text = example,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        block.exampleEnglish?.let { translation ->
            Text(
                text = translation,
                style = MaterialTheme.typography.bodyMedium,
                fontStyle = FontStyle.Italic,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun NotFoundContent(
    token: TextToken,
    suggestInstallDictionary: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = token.surface,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        if (token.reading.isNotBlank()) {
            Text(
                text = token.reading,
                style = MaterialTheme.typography.titleMedium,
            )
        }
        Text(
            text = stringResource(
                if (suggestInstallDictionary) {
                    R.string.dictionary_not_installed_hint
                } else {
                    R.string.dictionary_not_found
                },
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
