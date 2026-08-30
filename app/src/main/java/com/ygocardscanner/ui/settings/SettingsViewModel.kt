package com.ygocardscanner.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ygocardscanner.data.repository.CardArtworkRepository
import com.ygocardscanner.data.repository.CatalogRepository
import com.ygocardscanner.data.repository.CatalogUpdateStatus
import com.ygocardscanner.data.repository.GermanPrintingEnrichmentRepository
import com.ygocardscanner.data.settings.AppLanguageSettings
import com.ygocardscanner.data.work.CatalogUpdateScheduler
import com.ygocardscanner.data.work.FullArtworkDownloadScheduler
import com.ygocardscanner.data.work.GermanPrintingUpdateScheduler
import com.ygocardscanner.model.ArtworkPackStatus
import com.ygocardscanner.model.CardGame
import com.ygocardscanner.model.CardLanguage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val game: CardGame = CardGame.YUGIOH,
    val language: CardLanguage = CardLanguage.ENGLISH,
    val catalogStatus: CatalogUpdateStatus? = null,
    val supportsGermanPrintingBackup: Boolean = false,
    val germanPrintingSourceEnabled: Boolean = false,
    val germanPrintingStatus: CatalogUpdateStatus? = null,
    val artworkStatus: ArtworkPackStatus? = null,
    val scanSuccessAnimationEnabled: Boolean = true,
    val isSchedulingCatalog: Boolean = false,
    val isSchedulingGermanPrintings: Boolean = false,
    val isSchedulingArtwork: Boolean = false,
    val errorMessage: String? = null,
)

private data class PrimarySettings(
    val language: CardLanguage,
    val catalog: CatalogUpdateStatus?,
    val artwork: ArtworkPackStatus?,
    val scanSuccessAnimationEnabled: Boolean,
)

class SettingsViewModel(
    private val game: CardGame,
    private val languageSettings: AppLanguageSettings,
    private val catalogRepository: CatalogRepository,
    private val artworkRepository: CardArtworkRepository,
    private val catalogScheduler: CatalogUpdateScheduler,
    private val germanPrintingRepository: GermanPrintingEnrichmentRepository?,
    private val germanPrintingScheduler: GermanPrintingUpdateScheduler?,
    private val artworkScheduler: FullArtworkDownloadScheduler,
) : ViewModel() {
    private val _uiState = MutableStateFlow(SettingsUiState(game = game))
    val uiState = _uiState

    init {
        val germanEnabled = germanPrintingRepository?.let { languageSettings.germanPrintingSourceEnabled } ?: flowOf(false)
        val germanStatus = germanPrintingRepository?.observeUpdateStatus() ?: flowOf(null)
        viewModelScope.launch {
            val primary = combine(
                languageSettings.language,
                catalogRepository.observeCatalogUpdateStatus(),
                artworkRepository.observePackStatus(),
                languageSettings.scanSuccessAnimationEnabled,
            ) { language, catalog, artwork, animationEnabled ->
                PrimarySettings(language, catalog, artwork, animationEnabled)
            }
            combine(primary, germanEnabled, germanStatus) { settings, enabled, status ->
                Triple(settings, enabled, status)
            }.collect { (settings, enabled, status) ->
                _uiState.update {
                    it.copy(
                        language = settings.language,
                        catalogStatus = settings.catalog,
                        artworkStatus = settings.artwork,
                        scanSuccessAnimationEnabled = settings.scanSuccessAnimationEnabled,
                        supportsGermanPrintingBackup = germanPrintingRepository != null,
                        germanPrintingSourceEnabled = enabled,
                        germanPrintingStatus = status,
                    )
                }
            }
        }
    }

    fun setLanguage(language: CardLanguage) = languageSettings.setLanguage(language)

    fun setScanSuccessAnimationEnabled(enabled: Boolean) = languageSettings.setScanSuccessAnimationEnabled(enabled)

    fun refreshCatalog() = scheduleCatalogRefresh()

    private fun scheduleCatalogRefresh() {
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

    fun setGermanPrintingSourceEnabled(enabled: Boolean) {
        val repository = germanPrintingRepository ?: return
        languageSettings.setGermanPrintingSourceEnabled(enabled)
        viewModelScope.launch {
            try {
                repository.setEnabled(enabled)
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                _uiState.update { it.copy(errorMessage = error.message ?: "The German printing source could not be updated.") }
            }
        }
    }

    fun refreshGermanPrintings() {
        val scheduler = germanPrintingScheduler ?: return
        if (!_uiState.value.germanPrintingSourceEnabled || _uiState.value.isSchedulingGermanPrintings) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSchedulingGermanPrintings = true, errorMessage = null) }
            try {
                scheduler.enqueue(force = true)
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                _uiState.update { it.copy(errorMessage = error.message ?: "The German printing update could not be scheduled.") }
            } finally {
                _uiState.update { it.copy(isSchedulingGermanPrintings = false) }
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