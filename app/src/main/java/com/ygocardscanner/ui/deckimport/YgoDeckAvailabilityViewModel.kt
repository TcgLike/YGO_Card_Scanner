package com.ygocardscanner.ui.deckimport

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ygocardscanner.data.deckimport.yugioh.YgoDeckAvailabilityPreview
import com.ygocardscanner.data.deckimport.yugioh.YgoDeckAvailabilityRepository
import com.ygocardscanner.data.deckimport.yugioh.YgoDeckParsers
import com.ygocardscanner.data.settings.AppLanguageSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class YgoDeckAvailabilityUiState(
    val isLoading: Boolean = false,
    val preview: YgoDeckAvailabilityPreview? = null,
    val errorMessage: String? = null,
)

class YgoDeckAvailabilityViewModel(
    private val repository: YgoDeckAvailabilityRepository,
    private val languageSettings: AppLanguageSettings,
) : ViewModel() {
    private val _uiState = MutableStateFlow(YgoDeckAvailabilityUiState())
    val uiState = _uiState

    fun check(sourceLabel: String, rawInput: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, preview = null, errorMessage = null) }
            try {
                val document = YgoDeckParsers.parse(sourceLabel, rawInput)
                val preview = repository.check(document, languageSettings.language.value)
                _uiState.update { it.copy(isLoading = false, preview = preview) }
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                _uiState.update { it.copy(isLoading = false, errorMessage = error.message ?: "The deck could not be checked.") }
            }
        }
    }

    fun clear() {
        _uiState.update { it.copy(preview = null, errorMessage = null) }
    }

    fun reportReadError(message: String) {
        _uiState.update { it.copy(isLoading = false, errorMessage = message) }
    }
}