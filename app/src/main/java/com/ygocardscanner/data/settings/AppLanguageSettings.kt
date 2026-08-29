package com.ygocardscanner.data.settings

import android.content.Context
import com.ygocardscanner.model.CardLanguage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** App-private display preference. Catalog and collection data remain in Room. */
class AppLanguageSettings(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val _language = MutableStateFlow(CardLanguage.fromCode(preferences.getString(KEY_LANGUAGE, CardLanguage.ENGLISH.code).orEmpty()))
    val language = _language.asStateFlow()
    private val _germanPrintingSourceEnabled = MutableStateFlow(
        preferences.getBoolean(KEY_GERMAN_PRINTING_SOURCE_ENABLED, false),
    )
    val germanPrintingSourceEnabled = _germanPrintingSourceEnabled.asStateFlow()

    fun setLanguage(value: CardLanguage) {
        preferences.edit().putString(KEY_LANGUAGE, value.code).apply()
        _language.value = value
    }


    fun setGermanPrintingSourceEnabled(value: Boolean) {
        preferences.edit().putBoolean(KEY_GERMAN_PRINTING_SOURCE_ENABLED, value).apply()
        _germanPrintingSourceEnabled.value = value
    }
    private companion object {
        const val PREFERENCES_NAME = "display_settings"
        const val KEY_LANGUAGE = "app_language"
        const val KEY_GERMAN_PRINTING_SOURCE_ENABLED = "ygojson_german_printings_enabled"
    }
}        const val KEY_GERMAN_PRINTING_SOURCE_ENABLED = "ygojson_german_printings_enabled"
