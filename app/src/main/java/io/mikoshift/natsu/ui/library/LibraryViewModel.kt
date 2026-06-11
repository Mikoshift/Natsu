package io.mikoshift.natsu.ui.library

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.mikoshift.natsu.domain.repository.DocumentRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class LibraryUiState(
    val isImporting: Boolean = false,
    val errorMessage: String? = null,
)

class LibraryViewModel(
    private val documentRepository: DocumentRepository,
) : ViewModel() {

    val documents = documentRepository.observeDocuments()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    fun importBook(uri: Uri, displayName: String?) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isImporting = true, errorMessage = null)
            documentRepository.importBook(uri, displayName)
                .onSuccess {
                    _uiState.value = _uiState.value.copy(isImporting = false)
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        isImporting = false,
                        errorMessage = error.message ?: "Import failed",
                    )
                }
        }
    }

    fun renameDocument(id: String, title: String) {
        viewModelScope.launch {
            documentRepository.renameDocument(id, title)
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        errorMessage = error.message ?: "Rename failed",
                    )
                }
        }
    }

    fun deleteDocument(id: String) {
        viewModelScope.launch {
            documentRepository.deleteDocument(id)
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        errorMessage = error.message ?: "Delete failed",
                    )
                }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    fun refreshDocuments() {
        documentRepository.notifyDocumentsChanged()
    }
}
