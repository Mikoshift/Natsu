package io.mikoshift.natsu.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.mikoshift.natsu.R
import io.mikoshift.natsu.domain.model.ReaderSettings
import io.mikoshift.natsu.domain.model.ReaderTheme
import io.mikoshift.natsu.ui.shell.LocalDrawerOpen
import io.mikoshift.natsu.ui.theme.ReaderThemeWrapper
import kotlin.math.roundToInt

private const val MIN_FONT_SIZE_SP = 14f
private const val MAX_FONT_SIZE_SP = 28f
private const val MIN_LINE_SPACING = 1.2f
private const val MAX_LINE_SPACING = 2.5f

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    modifier: Modifier = Modifier,
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val openDrawer = LocalDrawerOpen.current

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
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
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            ReaderPreviewCard(settings = settings)

            Text(
                text = stringResource(R.string.settings_reader_section),
                style = MaterialTheme.typography.titleMedium,
            )

            ThemeSelector(
                selectedTheme = settings.theme,
                onThemeSelected = viewModel::setTheme,
            )

            FontSizeSlider(
                fontSizeSp = settings.fontSizeSp,
                onFontSizeChange = viewModel::setFontSize,
            )

            LineSpacingSlider(
                lineSpacingMultiplier = settings.lineSpacingMultiplier,
                onLineSpacingChange = viewModel::setLineSpacing,
            )
        }
    }
}

@Composable
private fun ReaderPreviewCard(
    settings: ReaderSettings,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.settings_preview_label),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ReaderThemeWrapper(settings = settings) {
                Text(
                    text = stringResource(R.string.settings_preview_sample),
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = settings.fontSizeSp.sp,
                        lineHeight = settings.fontSizeSp.sp * settings.lineSpacingMultiplier,
                    ),
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                        .background(
                            color = MaterialTheme.colorScheme.background,
                            shape = RoundedCornerShape(8.dp),
                        )
                        .padding(12.dp),
                )
            }
        }
    }
}

@Composable
private fun ThemeSelector(
    selectedTheme: ReaderTheme,
    onThemeSelected: (ReaderTheme) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.settings_theme),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ReaderTheme.entries.forEach { theme ->
                FilterChip(
                    selected = selectedTheme == theme,
                    onClick = { onThemeSelected(theme) },
                    label = {
                        Text(
                            text = when (theme) {
                                ReaderTheme.LIGHT -> stringResource(R.string.settings_theme_light)
                                ReaderTheme.DARK -> stringResource(R.string.settings_theme_dark)
                                ReaderTheme.SEPIA -> stringResource(R.string.settings_theme_sepia)
                            },
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun FontSizeSlider(
    fontSizeSp: Float,
    onFontSizeChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.settings_font_size),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = stringResource(R.string.settings_font_size_value, fontSizeSp.roundToInt()),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Slider(
            value = fontSizeSp,
            onValueChange = { onFontSizeChange(it.roundToInt().toFloat()) },
            valueRange = MIN_FONT_SIZE_SP..MAX_FONT_SIZE_SP,
            steps = (MAX_FONT_SIZE_SP - MIN_FONT_SIZE_SP).roundToInt() - 1,
        )
    }
}

@Composable
private fun LineSpacingSlider(
    lineSpacingMultiplier: Float,
    onLineSpacingChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val roundedSpacing = (lineSpacingMultiplier * 10).roundToInt() / 10f
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.settings_line_spacing),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = stringResource(R.string.settings_line_spacing_value, roundedSpacing),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Slider(
            value = roundedSpacing,
            onValueChange = { value ->
                onLineSpacingChange((value * 10).roundToInt() / 10f)
            },
            valueRange = MIN_LINE_SPACING..MAX_LINE_SPACING,
            steps = ((MAX_LINE_SPACING - MIN_LINE_SPACING) * 10).roundToInt() - 1,
        )
    }
}
