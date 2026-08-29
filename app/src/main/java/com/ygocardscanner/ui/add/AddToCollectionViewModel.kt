package com.ygocardscanner.ui.add

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ygocardscanner.data.repository.CardArtworkRepository
import com.ygocardscanner.data.repository.CatalogRepository
import com.ygocardscanner.data.repository.CatalogUpdateStatus
import com.ygocardscanner.data.repository.InventoryRepository
import com.ygocardscanner.data.settings.AppLanguageSettings
import com.ygocardscanner.data.work.CardArtworkUpdateScheduler
import com.ygocardscanner.data.work.CatalogUpdateScheduler
import com.ygocardscanner.data.work.FullArtworkDownloadScheduler
import com.ygocardscanner.model.ArtworkPackStatus
import com.ygocardscanner.model.CardArtworkDetail
import com.ygocardscanner.model.CardLanguage
import com.ygocardscanner.model.CatalogPrintingSummary
import com.ygocardscanner.model.KnownPrintingDraft
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AddToCollectionUiState(
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val isRequestingCatalogUpdate: Boolean = false,
    val isRequestingArtworkPack: Boolean = false,
    val query: String = "",
    val displayLanguage: CardLanguage = CardLanguage.ENGLISH,
    val printings: List<CatalogPrintingSummary> = emptyList(),
    val catalogUpdateStatus: CatalogUpdateStatus? = null,
    val artworkPackStatus: ArtworkPackStatus? = null,
    val selectedArtwork: CardArtworkDetail? = null,
    val errorMessage: String? = null,
)

sealed interface AddToCollectionEvent { data object EntryAdded : AddToCollectionEvent }

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class AddToCollectionViewModel(
    private val catalogRepository: CatalogRepository,
    private val inventoryRepository: InventoryRepository,
    private val catalogUpdateScheduler: CatalogUpdateScheduler,
    private val artworkRepository: CardArtworkRepository,
    private val artworkUpdateScheduler: CardArtworkUpdateScheduler,
    private val artworkPackScheduler: FullArtworkDownloadScheduler,
    private val languageSettings: AppLanguageSettings,
) : ViewModel() {
    private val query = MutableStateFlow("")
    private val displayLanguage = languageSettings.language
    private val _uiState = MutableStateFlow(AddToCollectionUiState())
    val uiState = _uiState
    private val _events = MutableSharedFlow<AddToCollectionEvent>()
    val events = _events
    private var catalogJob: Job? = null
    private var catalogStatusJob: Job? = null
    private var artworkPackStatusJob: Job? = null

    init { load() }

    fun updateQuery(value: String) {
        query.value = value
        _uiState.update { it.copy(query = value, isLoading = value.isNotBlank(), printings = if (value.isBlank()) emptyList() else it.printings, errorMessage = null) }
    }

    fun updateDisplayLanguage(value: CardLanguage) {
        languageSettings.setLanguage(value)
        _uiState.update { it.copy(displayLanguage = value, isLoading = it.query.isNotBlank(), printings = if (it.query.isBlank()) emptyList() else it.printings, errorMessage = null) }
    }

    fun selectPrinting(printing: CatalogPrintingSummary) {
        viewModelScope.launch {
            try {
                artworkUpdateScheduler.enqueue(printing.cardId)
                _uiState.update { it.copy(selectedArtwork = artworkRepository.getArtwork(printing.cardId)) }
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                _uiState.update { it.copy(errorMessage = "The local image preview could not be prepared.") }
            }
        }
    }

    fun clearSelectedArtwork() { _uiState.update { it.copy(selectedArtwork = null) } }
    fun refreshSelectedArtwork(cardId: String) {
        viewModelScope.launch { _uiState.update { it.copy(selectedArtwork = artworkRepository.getArtwork(cardId)) } }
    }

    fun retry() = load()

    fun requestCatalogUpdate() {
        val state = _uiState.value
        if (state.isRequestingCatalogUpdate || state.catalogUpdateStatus?.phase?.isInProgress == true) return
        viewModelScope.launch {
            _uiState.update { it.copy(isRequestingCatalogUpdate = true, errorMessage = null) }
            try { catalogUpdateScheduler.enqueue(force = true) }
            catch (error: Throwable) {
                if (error is CancellationException) throw error
                _uiState.update { it.copy(errorMessage = error.message ?: "The catalog update could not be scheduled.") }
            } finally { _uiState.update { it.copy(isRequestingCatalogUpdate = false) } }
        }
    }

    fun requestArtworkPack() {
        val state = _uiState.value
        if (state.isRequestingArtworkPack) return
        viewModelScope.launch {
            _uiState.update { it.copy(isRequestingArtworkPack = true, errorMessage = null) }
            try { artworkPackScheduler.enqueue() }
            catch (error: Throwable) {
                if (error is CancellationException) throw error
                _uiState.update { it.copy(errorMessage = "The offline card-image download could not be scheduled.") }
            } finally { _uiState.update { it.copy(isRequestingArtworkPack = false) } }
        }
    }

    fun addKnownPrinting(draft: KnownPrintingDraft) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, errorMessage = null) }
            try {
                inventoryRepository.addKnownPrinting(draft)
                _uiState.update { it.copy(isSaving = false) }
                _events.emit(AddToCollectionEvent.EntryAdded)
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                _uiState.update { it.copy(isSaving = false, errorMessage = error.message ?: "The card could not be added to your collection.") }
            }
        }
    }

    private fun load() {
        catalogJob?.cancel(); catalogStatusJob?.cancel(); artworkPackStatusJob?.cancel()
        _uiState.update { it.copy(isLoading = it.query.isNotBlank(), errorMessage = null) }
        catalogJob = viewModelScope.launch {
            try {
                combine(query, displayLanguage) { currentQuery, language -> currentQuery.trim() to language }
                    .flatMapLatest { (currentQuery, language) -> if (currentQuery.isBlank()) flowOf(emptyList()) else catalogRepository.observePrintings(currentQuery, language) }
                    .collect { printings -> _uiState.update { it.copy(isLoading = false, displayLanguage = displayLanguage.value, printings = printings, errorMessage = null) } }
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                _uiState.update { it.copy(isLoading = false, errorMessage = error.message ?: "The local card catalog could not be loaded.") }
            }
        }
        catalogStatusJob = viewModelScope.launch {
            try { catalogRepository.observeCatalogUpdateStatus().collect { status -> _uiState.update { it.copy(catalogUpdateStatus = status) } } }
            catch (error: Throwable) { if (error is CancellationException) throw error }
        }
        artworkPackStatusJob = viewModelScope.launch {
            try { artworkRepository.observePackStatus().collect { status -> _uiState.update { it.copy(artworkPackStatus = status) } } }
            catch (error: Throwable) { if (error is CancellationException) throw error }
        }
    }
}