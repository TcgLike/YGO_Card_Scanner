package com.ygocardscanner.ui.catalog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ygocardscanner.data.repository.CatalogViewerRepository
import com.ygocardscanner.data.settings.AppLanguageSettings
import com.ygocardscanner.model.CatalogCardSummary
import com.ygocardscanner.model.CollectionLayout
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CatalogViewerUiState(
    val isLoading: Boolean = true,
    val query: String = "",
    val cards: List<CatalogCardSummary> = emptyList(),
    val layout: CollectionLayout = CollectionLayout.DETAILED,
    val errorMessage: String? = null,
)

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class CatalogViewerViewModel(
    private val catalogViewerRepository: CatalogViewerRepository,
    private val languageSettings: AppLanguageSettings,
) : ViewModel() {
    private val query = MutableStateFlow("")
    private val _uiState = MutableStateFlow(CatalogViewerUiState())
    val uiState = _uiState
    private var catalogJob: Job? = null

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
        catalogJob?.cancel()
        catalogJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                combine(query, languageSettings.language) { currentQuery, language -> currentQuery to language }
                    .flatMapLatest { (currentQuery, language) ->
                        catalogViewerRepository.observeCards(currentQuery, language)
                    }
                    .combine(languageSettings.collectionLayout) { cards, layout -> cards to layout }
                    .collect { (cards, layout) ->
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                cards = cards,
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
                        errorMessage = error.message ?: "The local card catalog could not be loaded.",
                    )
                }
            }
        }
    }
}
