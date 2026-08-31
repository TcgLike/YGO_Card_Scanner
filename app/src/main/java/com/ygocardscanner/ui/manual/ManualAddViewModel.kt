package com.ygocardscanner.ui.manual

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ygocardscanner.data.repository.InventoryRepository
import com.ygocardscanner.model.UnknownPrintingDraft
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ManualAddUiState(
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
)

sealed interface ManualAddEvent {
    data object EntryAdded : ManualAddEvent
}

class ManualAddViewModel(
    private val inventoryRepository: InventoryRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ManualAddUiState())
    val uiState = _uiState

    private val _events = MutableSharedFlow<ManualAddEvent>()
    val events = _events

    fun addUnknownPrinting(draft: UnknownPrintingDraft) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, errorMessage = null) }
            try {
                inventoryRepository.addUnknownPrinting(draft)
                _uiState.update { it.copy(isSaving = false) }
                _events.emit(ManualAddEvent.EntryAdded)
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        errorMessage = error.message ?: "The unknown printing could not be saved.",
                    )
                }
            }
        }
    }
}

