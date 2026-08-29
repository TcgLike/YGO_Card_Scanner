package com.ygocardscanner.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ygocardscanner.data.repository.CardArtworkRepository
import com.ygocardscanner.data.repository.CatalogRepository
import com.ygocardscanner.data.repository.CatalogUpdateStatus
import com.ygocardscanner.data.settings.AppLanguageSettings
import com.ygocardscanner.data.work.CatalogUpdateScheduler
import com.ygocardscanner.data.work.FullArtworkDownloadScheduler
import com.ygocardscanner.model.ArtworkPackStatus
import com.ygocardscanner.model.CardLanguage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val language: CardLanguage = CardLanguage.ENGLISH,
    val catalogStatus: CatalogUpdateStatus? = null,
    val artworkStatus: ArtworkPackStatus? = null,
    val isSchedulingCatalog: Boolean = false,
    val isSchedulingArtwork: Boolean = false,
    val errorMessage: String? = null,
)

class SettingsViewModel(
    private val languageSettings: AppLanguageSettings,
    private val catalogRepository: CatalogRepository,
    private val artworkRepository: CardArtworkRepository,
    private val catalogScheduler: CatalogUpdateScheduler,
    private val artworkScheduler: FullArtworkDownloadScheduler,
) : ViewModel() {
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState = _uiState

    init {
        viewModelScope.launch {
            combine(
                languageSettings.language,
                catalogRepository.observeCatalogUpdateStatus(),
                artworkRepository.observePackStatus(),
            ) { language, catalog, artwork -> Triple(language, catalog, artwork) }
                .collect { (language, catalog, artwork) ->
                    _uiState.update { it.copy(language = language, catalogStatus = catalog, artworkStatus = artwork) }
                }
        }
    }

    fun setLanguage(language: CardLanguage) = languageSettings.setLanguage(language)

    /** A forced refresh repairs older installs that were downloaded before German text support. */
    fun refreshBilingualCatalog() {
        if (_uiState.value.isSchedulingCatalog) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSchedulingCatalog = true, errorMessage = null) }
            try {
                catalogScheduler.enqueue(force = true)
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                _uiState.update { it.copy(errorMessage = error.message ?: "The catalog refresh could not be scheduled.") }
            } finally {
                _uiState.update { it.copy(isSchedulingCatalog = false) }
            }
        }
    }

    fun resumeArtworkDownload() {
        if (_uiState.value.isSchedulingArtwork) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSchedulingArtwork = true, errorMessage = null) }
            try {
                artworkScheduler.enqueue()
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                _uiState.update { it.copy(errorMessage = error.message ?: "The image download could not be scheduled.") }
            } finally {
                _uiState.update { it.copy(isSchedulingArtwork = false) }
            }
        }
    }
}
