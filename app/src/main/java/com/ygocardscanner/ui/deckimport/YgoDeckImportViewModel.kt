package com.ygocardscanner.ui.deckimport

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ygocardscanner.data.deckimport.yugioh.YgoDeckImportCard
import com.ygocardscanner.data.deckimport.yugioh.YgoDeckImportPreview
import com.ygocardscanner.data.deckimport.yugioh.YgoDeckImportRepository
import com.ygocardscanner.data.deckimport.yugioh.YgoDeckImportRequest
import com.ygocardscanner.data.deckimport.yugioh.YgoDeckImportSelection
import com.ygocardscanner.data.deckimport.yugioh.YgoDeckParsers
import com.ygocardscanner.data.settings.AppLanguageSettings
import com.ygocardscanner.model.CardCondition
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class YgoDeckImportUiState(
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val sourceLabel: String? = null,
    val baseCodeInput: String = "",
    val preview: YgoDeckImportPreview? = null,
    val selectedPrintingIds: Map<String, String?> = emptyMap(),
    val condition: CardCondition = CardCondition.NEAR_MINT,
    val notes: String = "",
    val errorMessage: String? = null,
) {
    val unresolvedCards: List<YgoDeckImportCard> get() = preview?.cards.orEmpty().filterNot(YgoDeckImportCard::isResolved)
    val cardsWithoutBaseCode: List<YgoDeckImportCard>
        get() = preview?.baseCodePrefix?.let { preview.cards.filter { card -> card.isResolved && !card.hasBaseCodeMatch } }.orEmpty()
}

sealed interface YgoDeckImportEvent { data object Imported : YgoDeckImportEvent }

class YgoDeckImportViewModel(
    private val repository: YgoDeckImportRepository,
    private val languageSettings: AppLanguageSettings,
) : ViewModel() {
    private val _uiState = MutableStateFlow(YgoDeckImportUiState())
    val uiState = _uiState
    private val _events = MutableSharedFlow<YgoDeckImportEvent>()
    val events = _events

    fun preview(sourceLabel: String, rawInput: String, baseCodeInput: String = "") {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    errorMessage = null,
                    preview = null,
                    sourceLabel = sourceLabel,
                    baseCodeInput = baseCodeInput,
                )
            }
            try {
                val document = YgoDeckParsers.parse(sourceLabel, rawInput)
                val result = repository.preview(document, languageSettings.language.value, baseCodeInput)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        preview = result,
                        // A base-code match is selected only when it is unique. Ambiguous candidates stay safe/unknown.
                        selectedPrintingIds = result.cards.associate { card ->
                            card.passcode to card.matchingBaseCodePrintingIds.singleOrNull()
                        },
                    )
                }
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                _uiState.update { it.copy(isLoading = false, errorMessage = error.message ?: "The deck could not be read.") }
            }
        }
    }

    fun clearPreview() {
        _uiState.update { it.copy(preview = null, selectedPrintingIds = emptyMap(), errorMessage = null) }
    }

    fun reportReadError(message: String) {
        _uiState.update { it.copy(isLoading = false, errorMessage = message) }
    }

    fun selectPrinting(passcode: String, printingId: String?) {
        _uiState.update { it.copy(selectedPrintingIds = it.selectedPrintingIds + (passcode to printingId)) }
    }

    fun setCondition(condition: CardCondition) = _uiState.update { it.copy(condition = condition) }

    fun setNotes(notes: String) = _uiState.update { it.copy(notes = notes) }

    fun importDeck() {
        val state = _uiState.value
        val preview = state.preview ?: return
        if (state.isSaving || state.unresolvedCards.isNotEmpty()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, errorMessage = null) }
            try {
                repository.importDeck(
                    YgoDeckImportRequest(
                        cards = preview.cards.map { card ->
                            YgoDeckImportSelection(
                                passcode = card.passcode,
                                cardId = requireNotNull(card.cardId),
                                printingId = state.selectedPrintingIds[card.passcode],
                                quantity = card.quantity,
                            )
                        },
                        language = languageSettings.language.value,
                        condition = state.condition,
                        notes = state.notes,
                    ),
                )
                _uiState.update { it.copy(isSaving = false) }
                _events.emit(YgoDeckImportEvent.Imported)
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                _uiState.update { it.copy(isSaving = false, errorMessage = error.message ?: "The deck could not be added to your collection.") }
            }
        }
    }
}
