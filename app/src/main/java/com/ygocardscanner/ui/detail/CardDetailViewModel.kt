package com.ygocardscanner.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ygocardscanner.data.repository.InventoryRepository
import com.ygocardscanner.data.work.CardArtworkUpdateScheduler
import com.ygocardscanner.model.CardArtworkDownloadState
import com.ygocardscanner.model.InventoryEntryDetail
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CardDetailUiState(
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val entry: InventoryEntryDetail? = null,
    val errorMessage: String? = null,
)

sealed interface CardDetailEvent {
    data object EntryDeleted : CardDetailEvent
}

class CardDetailViewModel(
    private val entryId: String,
    private val inventoryRepository: InventoryRepository,
    private val artworkUpdateScheduler: CardArtworkUpdateScheduler,
) : ViewModel() {
    private val _uiState = MutableStateFlow(CardDetailUiState())
    val uiState = _uiState

    private val _events = MutableSharedFlow<CardDetailEvent>()
    val events = _events
    private var automaticallyRequestedArtworkCardId: String? = null

    init {
        observeEntry()
    }

    fun retry() = observeEntry()

    fun requestArtwork() {
        val entry = _uiState.value.entry ?: return
        if (entry.artwork == null) return
        viewModelScope.launch {
            try {
                artworkUpdateScheduler.enqueue(entry.cardId)
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                _uiState.update {
                    it.copy(errorMessage = "The local card image could not be scheduled for download.")
                }
            }
        }
    }

    fun updateCondition(condition: com.ygocardscanner.model.CardCondition) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, errorMessage = null) }
            try { inventoryRepository.setCondition(entryId, condition); _uiState.update { it.copy(isSaving = false) } }
            catch (error: Throwable) {
                if (error is CancellationException) throw error
                _uiState.update { it.copy(isSaving = false, errorMessage = error.message ?: "The condition could not be updated.") }
            }
        }
    }
    fun updateQuantity(quantity: Int) {
        viewModelScope.launch {
            if (quantity < 0) {
                _uiState.update { it.copy(errorMessage = "Quantity cannot be negative.") }
                return@launch
            }
            _uiState.update { it.copy(isSaving = true, errorMessage = null) }
            try {
                inventoryRepository.setQuantity(entryId, quantity)
                _uiState.update { it.copy(isSaving = false) }
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        errorMessage = error.message ?: "The quantity could not be updated.",
                    )
                }
            }
        }
    }

    fun deleteEntry() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, errorMessage = null) }
            try {
                inventoryRepository.deleteEntry(entryId)
                _uiState.update { it.copy(isSaving = false) }
                _events.emit(CardDetailEvent.EntryDeleted)
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        errorMessage = error.message ?: "The entry could not be removed.",
                    )
                }
            }
        }
    }

    private fun observeEntry() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                inventoryRepository.observeEntry(entryId).collect { entry ->
                    _uiState.update { it.copy(isLoading = false, entry = entry) }
                    val artwork = entry?.artwork
                    if (artwork?.downloadState == CardArtworkDownloadState.NOT_DOWNLOADED &&
                        automaticallyRequestedArtworkCardId != entry.cardId
                    ) {
                        automaticallyRequestedArtworkCardId = entry.cardId
                        requestArtwork()
                    }
                }
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = error.message ?: "The card detail could not be loaded.",
                    )
                }
            }
        }
    }
}