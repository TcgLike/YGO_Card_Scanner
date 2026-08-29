package com.ygocardscanner.ui.collection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ygocardscanner.data.repository.InventoryRepository
import com.ygocardscanner.model.CollectionEntrySummary
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CollectionUiState(
    val isLoading: Boolean = true,
    val query: String = "",
    val entries: List<CollectionEntrySummary> = emptyList(),
    val errorMessage: String? = null,
)

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class CollectionListViewModel(
    private val inventoryRepository: InventoryRepository,
) : ViewModel() {
    private val query = MutableStateFlow("")
    private val _uiState = MutableStateFlow(CollectionUiState())
    val uiState = _uiState

    private var collectionJob: Job? = null

    init {
        load()
    }

    fun updateQuery(value: String) {
        query.value = value
        _uiState.update { it.copy(query = value) }
    }

    fun retry() = load()

    private fun load() {
        collectionJob?.cancel()
        collectionJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                query
                    .flatMapLatest { inventoryRepository.observeCollection(it) }
                    .collect { entries ->
                        _uiState.update { it.copy(isLoading = false, entries = entries, errorMessage = null) }
                    }
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = error.message ?: "Your local collection could not be loaded.",
                    )
                }
            }
        }
    }
}
