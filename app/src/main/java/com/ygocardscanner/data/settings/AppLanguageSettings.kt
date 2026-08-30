package com.ygocardscanner.data.settings

import android.content.Context
import com.ygocardscanner.model.CardLanguage
import com.ygocardscanner.model.CardGame
import com.ygocardscanner.model.CollectionLayout
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** App-private preferences. Catalog, artwork, and collection data remain in Room. */
class AppLanguageSettings(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val _language = MutableStateFlow(
        CardLanguage.fromCode(preferences.getString(KEY_LANGUAGE, CardLanguage.ENGLISH.code).orEmpty()),
    )
    val language = _language.asStateFlow()

    private val _germanPrintingSourceEnabled = MutableStateFlow(
        preferences.getBoolean(KEY_GERMAN_PRINTING_SOURCE_ENABLED, false),
    )
    val germanPrintingSourceEnabled = _germanPrintingSourceEnabled.asStateFlow()

    private val _scanSuccessAnimationEnabled = MutableStateFlow(
        preferences.getBoolean(KEY_SCAN_SUCCESS_ANIMATION_ENABLED, true),
    )
    val scanSuccessAnimationEnabled = _scanSuccessAnimationEnabled.asStateFlow()

    private val _collectionLayout = MutableStateFlow(
        CollectionLayout.fromCode(preferences.getString(KEY_COLLECTION_LAYOUT, CollectionLayout.DETAILED.code)),
    )
    val collectionLayout = _collectionLayout.asStateFlow()

    private val _selectedGame = MutableStateFlow(
        CardGame.fromCode(preferences.getString(KEY_SELECTED_GAME, CardGame.YUGIOH.code)),
    )
    val selectedGame = _selectedGame.asStateFlow()

    fun setLanguage(value: CardLanguage) {
        preferences.edit().putString(KEY_LANGUAGE, value.code).apply()
        _language.value = value
    }

    fun setGermanPrintingSourceEnabled(value: Boolean) {
        preferences.edit().putBoolean(KEY_GERMAN_PRINTING_SOURCE_ENABLED, value).apply()
        _germanPrintingSourceEnabled.value = value
    }

    fun setScanSuccessAnimationEnabled(value: Boolean) {
        preferences.edit().putBoolean(KEY_SCAN_SUCCESS_ANIMATION_ENABLED, value).apply()
        _scanSuccessAnimationEnabled.value = value
    }

    fun setSelectedGame(value: CardGame) {
        preferences.edit().putString(KEY_SELECTED_GAME, value.code).apply()
        _selectedGame.value = value
    }
    fun setCollectionLayout(value: CollectionLayout) {
        preferences.edit().putString(KEY_COLLECTION_LAYOUT, value.code).apply()
        _collectionLayout.value = value
    }

    private companion object {
        const val PREFERENCES_NAME = "display_settings"
        const val KEY_LANGUAGE = "app_language"
        const val KEY_GERMAN_PRINTING_SOURCE_ENABLED = "ygojson_german_printings_enabled"
        const val KEY_SCAN_SUCCESS_ANIMATION_ENABLED = "scan_success_animation_enabled"
        const val KEY_COLLECTION_LAYOUT = "collection_layout"
        const val KEY_SELECTED_GAME = "selected_game"
    }
}