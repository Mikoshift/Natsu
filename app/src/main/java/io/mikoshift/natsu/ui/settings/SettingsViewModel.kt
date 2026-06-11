package io.mikoshift.natsu.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.mikoshift.natsu.data.settings.ReaderSettingsStore
import io.mikoshift.natsu.domain.model.FuriganaMode
import io.mikoshift.natsu.domain.model.ReaderSettings
import io.mikoshift.natsu.domain.model.ReaderTheme
import io.mikoshift.natsu.domain.model.TextToken
import io.mikoshift.natsu.domain.repository.TextTokenizer
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val readerSettingsStore: ReaderSettingsStore,
    private val textTokenizer: TextTokenizer,
) : ViewModel() {

    val settings: StateFlow<ReaderSettings> = readerSettingsStore.settings
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ReaderSettings(),
        )

    fun tokenizeForPreview(text: String): List<TextToken> = textTokenizer.tokenize(text)

    fun setFontSize(fontSizeSp: Float) {
        viewModelScope.launch {
            readerSettingsStore.updateFontSize(fontSizeSp)
        }
    }

    fun setLineSpacing(lineSpacingMultiplier: Float) {
        viewModelScope.launch {
            readerSettingsStore.updateLineSpacing(lineSpacingMultiplier)
        }
    }

    fun setTheme(theme: ReaderTheme) {
        viewModelScope.launch {
            readerSettingsStore.updateTheme(theme)
        }
    }

    fun setFuriganaMode(furiganaMode: FuriganaMode) {
        viewModelScope.launch {
            readerSettingsStore.updateFuriganaMode(furiganaMode)
        }
    }
}
