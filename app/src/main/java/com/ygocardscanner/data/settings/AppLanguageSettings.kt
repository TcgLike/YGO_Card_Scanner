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

    fun setLanguage(value: CardLanguage) {
        preferences.edit().putString(KEY_LANGUAGE, value.code).apply()
        _language.value = value
    }

    private companion object {
        const val PREFERENCES_NAME = "display_settings"
        const val KEY_LANGUAGE = "app_language"
    }
}