package com.ygocardscanner.ui.collection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ygocardscanner.data.repository.InventoryRepository
import com.ygocardscanner.data.settings.AppLanguageSettings
import com.ygocardscanner.model.CollectionEntrySummary
import com.ygocardscanner.model.CollectionLayout
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CollectionUiState(
    val isLoading: Boolean = true,
    val query: String = "",
    val entries: List<CollectionEntrySummary> = emptyList(),
    val layout: CollectionLayout = CollectionLayout.DETAILED,
    val errorMessage: String? = null,
)

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class CollectionListViewModel(
    private val inventoryRepository: InventoryRepository,
    private val languageSettings: AppLanguageSettings,
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

    fun setLayout(layout: CollectionLayout) = languageSettings.setCollectionLayout(layout)

    fun retry() = load()

    private fun load() {
        collectionJob?.cancel()
        collectionJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                combine(query, languageSettings.language) { currentQuery, language -> currentQuery to language }
                    .flatMapLatest { (currentQuery, language) -> inventoryRepository.observeCollection(currentQuery, language) }
                    .combine(languageSettings.collectionLayout) { entries, layout -> entries to layout }
                    .collect { (entries, layout) ->
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                entries = entries,
                                layout = layout,
                                errorMessage = null,
                            )
                        }
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