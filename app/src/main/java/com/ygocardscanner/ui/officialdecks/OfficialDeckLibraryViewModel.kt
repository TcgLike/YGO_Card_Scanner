package com.ygocardscanner.ui.officialdecks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ygocardscanner.data.officialdecks.yugioh.OfficialDeckProductSummary
import com.ygocardscanner.data.officialdecks.yugioh.OfficialDeckRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class OfficialDeckLibraryUiState(val isLoading: Boolean = true, val products: List<OfficialDeckProductSummary> = emptyList(), val errorMessage: String? = null)

class OfficialDeckLibraryViewModel(private val repository: OfficialDeckRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(OfficialDeckLibraryUiState())
    val uiState = _uiState.asStateFlow()
    init { load() }
    fun retry() = load()
    private fun load() = viewModelScope.launch {
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        try { _uiState.update { it.copy(isLoading = false, products = repository.loadLibrary()) } }
        catch (error: Throwable) { if (error is CancellationException) throw error; _uiState.update { it.copy(isLoading = false, errorMessage = error.message ?: "Official decks could not be loaded.") } }
    }
}