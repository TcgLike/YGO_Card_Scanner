package com.ygocardscanner.ui.scanner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ygocardscanner.data.repository.InventoryRepository
import com.ygocardscanner.data.settings.AppLanguageSettings
import com.ygocardscanner.data.scanner.BulkPhotoQueueItem
import com.ygocardscanner.data.scanner.CardScannerRepository
import com.ygocardscanner.data.scanner.OcrTextBlock
import com.ygocardscanner.data.scanner.ScanCandidate
import com.ygocardscanner.data.scanner.ScanMatchResult
import com.ygocardscanner.data.scanner.ScanTextExtractor
import com.ygocardscanner.data.scanner.ScanTextRegionGrouper
import com.ygocardscanner.data.scanner.ScannerMode
import com.ygocardscanner.model.CardCondition
import com.ygocardscanner.model.KnownPrintingDraft
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ScannerUiState(
    val mode: ScannerMode = ScannerMode.LIVE,
    val isMatching: Boolean = false,
    val isProcessingPhoto: Boolean = false,
    val isSaving: Boolean = false,
    val match: ScanMatchResult.Candidates? = null,
    val selectedCandidate: ScanCandidate? = null,
    val photoQueue: List<BulkPhotoQueueItem> = emptyList(),
    val queueIndex: Int = 0,
    val acceptedCount: Int = 0,
    val lastAcceptedEntryId: String? = null,
    val unmatchedRegionCount: Int = 0,
    val message: String = "Point the camera at one card's title and lower printed identifiers.",
    val errorMessage: String? = null,
) {
    val isBulkPhotoMode: Boolean get() = mode == ScannerMode.BULK_PHOTO
    val queueRemaining: Int get() = (photoQueue.size - queueIndex).coerceAtLeast(0)
}

sealed interface ScannerEvent { data object AddedSingleCard : ScannerEvent }

class ScannerViewModel(
    private val scannerRepository: CardScannerRepository,
    private val inventoryRepository: InventoryRepository,
    private val languageSettings: AppLanguageSettings,
) : ViewModel() {
    private val isMatching = AtomicBoolean(false)
    private val _uiState = MutableStateFlow(ScannerUiState())
    val uiState = _uiState
    private val _events = MutableSharedFlow<ScannerEvent>()
    val events = _events

    fun setMode(mode: ScannerMode) {
        _uiState.update {
            it.copy(
                mode = mode,
                match = null,
                selectedCandidate = null,
                photoQueue = emptyList(),
                queueIndex = 0,
                unmatchedRegionCount = 0,
                errorMessage = null,
                message = if (mode == ScannerMode.LIVE) {
                    "Point the camera at one card's title and lower printed identifiers."
                } else {
                    "Arrange multiple cards clearly, then take a photo. Matches will be reviewed in a queue."
                },
            )
        }
    }

    fun onRecognizedText(rawText: String) {
        if (_uiState.value.isBulkPhotoMode) return
        val observation = ScanTextExtractor.extract(rawText)
        if (observation.rawText.isBlank() || _uiState.value.match != null || _uiState.value.isSaving || !isMatching.compareAndSet(false, true)) return
        _uiState.update { it.copy(isMatching = true, errorMessage = null) }
        viewModelScope.launch {
            try {
                updateLiveMatch(scannerRepository.match(observation, languageSettings.language.value))
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                _uiState.update { it.copy(errorMessage = "The local card catalog could not be searched.") }
            } finally {
                isMatching.set(false)
                _uiState.update { it.copy(isMatching = false) }
            }
        }
    }

    fun onBulkPhotoRecognized(blocks: List<OcrTextBlock>) {
        if (!_uiState.value.isBulkPhotoMode || _uiState.value.isProcessingPhoto) return
        _uiState.update { it.copy(isProcessingPhoto = true, errorMessage = null, match = null, selectedCandidate = null) }
        viewModelScope.launch {
            try {
                val matches = ScanTextRegionGrouper.group(blocks).mapNotNull { region ->
                    (scannerRepository.match(ScanTextExtractor.extract(region.rawText), languageSettings.language.value) as? ScanMatchResult.Candidates)
                        ?.let { BulkPhotoQueueItem(region, it) }
                }
                val regionCount = ScanTextRegionGrouper.group(blocks).size
                val first = matches.firstOrNull()
                _uiState.update {
                    it.copy(
                        isProcessingPhoto = false,
                        photoQueue = matches,
                        queueIndex = 0,
                        match = first?.match,
                        selectedCandidate = first?.match?.candidates?.singleOrNull(),
                        unmatchedRegionCount = regionCount - matches.size,
                        message = if (first == null) "No local matches found in this photo. Try a clearer photo or add manually." else "Review photo match 1 of ${matches.size}.",
                    )
                }
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                _uiState.update { it.copy(isProcessingPhoto = false, errorMessage = "The captured photo could not be matched locally.") }
            }
        }
    }

    fun selectCandidate(candidate: ScanCandidate) { _uiState.update { it.copy(selectedCandidate = candidate, errorMessage = null) } }

    fun dismissMatch() {
        if (_uiState.value.isBulkPhotoMode) advancePhotoQueue("Skipped. Review the next photo match.")
        else _uiState.update { it.copy(match = null, selectedCandidate = null, message = "Keep one card in the guide.") }
    }

    fun confirmSelected() {
        val state = _uiState.value
        val candidate = state.selectedCandidate ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, errorMessage = null) }
            try {
                val entryId = inventoryRepository.addKnownPrinting(
                    KnownPrintingDraft(candidate.printing.printingId, candidate.printing.language, candidate.printing.rarity, candidate.printing.edition, CardCondition.NEAR_MINT, 1, ""),
                )
                if (state.isBulkPhotoMode) {
                    _uiState.update { it.copy(isSaving = false, acceptedCount = it.acceptedCount + 1, lastAcceptedEntryId = entryId) }
                    advancePhotoQueue("Added. Review the next photo match.")
                } else {
                    _uiState.update { it.copy(isSaving = false) }
                    _events.emit(ScannerEvent.AddedSingleCard)
                }
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                _uiState.update { it.copy(isSaving = false, errorMessage = "The matched card could not be added.") }
            }
        }
    }

    fun undoLastBulkAdd() {
        val entryId = _uiState.value.lastAcceptedEntryId ?: return
        viewModelScope.launch {
            try {
                inventoryRepository.deleteEntry(entryId)
                _uiState.update { it.copy(acceptedCount = (it.acceptedCount - 1).coerceAtLeast(0), lastAcceptedEntryId = null, message = "Last photo-queue addition removed.") }
            } catch (_: Throwable) { _uiState.update { it.copy(errorMessage = "The last addition could not be undone.") } }
        }
    }

    private fun updateLiveMatch(result: ScanMatchResult) = _uiState.update { state ->
        when (result) {
            ScanMatchResult.NoMatch -> state.copy(message = "No local catalog match yet. Keep the title and lower code in view.")
            is ScanMatchResult.Candidates -> state.copy(match = result, selectedCandidate = result.candidates.singleOrNull(), message = if (result.isAmbiguous) "Choose the correct local match." else "Review this local match before adding.")
        }
    }

    private fun advancePhotoQueue(message: String) = _uiState.update { state ->
        val nextIndex = state.queueIndex + 1
        val next = state.photoQueue.getOrNull(nextIndex)
        state.copy(
            queueIndex = nextIndex,
            match = next?.match,
            selectedCandidate = next?.match?.candidates?.singleOrNull(),
            message = if (next == null) "Bulk photo queue complete. ${state.acceptedCount} added." else message,
        )
    }
}