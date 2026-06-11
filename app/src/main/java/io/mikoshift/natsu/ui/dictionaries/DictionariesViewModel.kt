package io.mikoshift.natsu.ui.dictionaries

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.mikoshift.natsu.domain.model.DictionaryInstallState
import io.mikoshift.natsu.domain.model.InstalledDictionary
import io.mikoshift.natsu.domain.repository.DictionaryManagerRepository
import io.mikoshift.natsu.domain.repository.PriorityDirection
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DictionariesUiState(
    val dictionaries: List<InstalledDictionary> = emptyList(),
    val errorMessage: String? = null,
    val actionInProgressId: String? = null,
)

class DictionariesViewModel(
    private val dictionaryManagerRepository: DictionaryManagerRepository,
) : ViewModel() {

    val dictionaries: StateFlow<List<InstalledDictionary>> =
        dictionaryManagerRepository.observeDictionaries()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyList(),
            )

    private val _uiState = MutableStateFlow(DictionariesUiState())
    val uiState: StateFlow<DictionariesUiState> = _uiState.asStateFlow()

    fun download(catalogId: String) {
        if (_uiState.value.actionInProgressId != null) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(actionInProgressId = catalogId, errorMessage = null)
            dictionaryManagerRepository.downloadDictionary(catalogId)
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        errorMessage = error.message ?: "Download failed",
                    )
                }
            _uiState.value = _uiState.value.copy(actionInProgressId = null)
        }
    }

    fun delete(catalogId: String) {
        viewModelScope.launch {
            dictionaryManagerRepository.deleteDictionary(catalogId)
        }
    }

    fun setEnabled(catalogId: String, enabled: Boolean) {
        viewModelScope.launch {
            dictionaryManagerRepository.setEnabled(catalogId, enabled)
        }
    }

    fun movePriority(catalogId: String, direction: PriorityDirection) {
        viewModelScope.launch {
            dictionaryManagerRepository.movePriority(catalogId, direction)
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }
}
