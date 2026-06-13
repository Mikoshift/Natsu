package io.mikoshift.natsu.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.mikoshift.natsu.data.remote.ApiException
import io.mikoshift.natsu.data.settings.ReaderSettingsStore
import io.mikoshift.natsu.domain.model.AuthState
import io.mikoshift.natsu.domain.model.FuriganaMode
import io.mikoshift.natsu.domain.model.ReaderSettings
import io.mikoshift.natsu.domain.model.ReaderTheme
import io.mikoshift.natsu.domain.model.TextToken
import io.mikoshift.natsu.domain.repository.AuthRepository
import io.mikoshift.natsu.domain.repository.SyncRepository
import io.mikoshift.natsu.domain.repository.TextTokenizer
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val readerSettingsStore: ReaderSettingsStore,
    private val textTokenizer: TextTokenizer,
    private val authRepository: AuthRepository,
    private val syncRepository: SyncRepository,
) : ViewModel() {

    val settings: StateFlow<ReaderSettings> = readerSettingsStore.settings
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ReaderSettings(),
        )

    val authState: StateFlow<AuthState> = authRepository.authState
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = AuthState.Guest,
        )

    val isSyncing: StateFlow<Boolean> = syncRepository.isSyncing
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = false,
        )

    fun tokenizeForPreview(text: String): List<TextToken> = textTokenizer.tokenize(text)

    fun setFontSize(fontSizeSp: Float) {
        viewModelScope.launch {
            readerSettingsStore.updateFontSize(fontSizeSp)
            syncRepository.scheduleSync(delaySeconds = 5)
        }
    }

    fun setLineSpacing(lineSpacingMultiplier: Float) {
        viewModelScope.launch {
            readerSettingsStore.updateLineSpacing(lineSpacingMultiplier)
            syncRepository.scheduleSync(delaySeconds = 5)
        }
    }

    fun setTheme(theme: ReaderTheme) {
        viewModelScope.launch {
            readerSettingsStore.updateTheme(theme)
            syncRepository.scheduleSync(delaySeconds = 5)
        }
    }

    fun setFuriganaMode(furiganaMode: FuriganaMode) {
        viewModelScope.launch {
            readerSettingsStore.updateFuriganaMode(furiganaMode)
            syncRepository.scheduleSync(delaySeconds = 5)
        }
    }

    fun syncNow() {
        viewModelScope.launch {
            syncRepository.syncAll()
        }
    }

    fun logout(onResult: (Result<Unit>) -> Unit = {}) {
        viewModelScope.launch {
            val result = authRepository.logout()
            onResult(result)
        }
    }

    fun deleteAccount(password: String, onResult: (Result<Unit>) -> Unit) {
        viewModelScope.launch {
            val result = authRepository.deleteAccount(password)
            onResult(result)
        }
    }

    fun authErrorMessage(error: Throwable): String = when (error) {
        is ApiException -> error.message ?: "Request failed"
        else -> error.message ?: "Something went wrong"
    }
}
