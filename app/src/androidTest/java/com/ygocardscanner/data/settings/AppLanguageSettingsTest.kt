package com.ygocardscanner.data.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppLanguageSettingsTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun scanSuccessAnimationPreferencePersistsAcrossSettingsRecreation() = runBlocking {
        val preferences = context.getSharedPreferences("display_settings", Context.MODE_PRIVATE)
        val hadValue = preferences.contains("scan_success_animation_enabled")
        val previousValue = preferences.getBoolean("scan_success_animation_enabled", true)
        try {
            AppLanguageSettings(context).setScanSuccessAnimationEnabled(false)
            assertFalse(AppLanguageSettings(context).scanSuccessAnimationEnabled.value)

            AppLanguageSettings(context).setScanSuccessAnimationEnabled(true)
            assertTrue(AppLanguageSettings(context).scanSuccessAnimationEnabled.value)
        } finally {
            if (hadValue) {
                preferences.edit().putBoolean("scan_success_animation_enabled", previousValue).commit()
            } else {
                preferences.edit().remove("scan_success_animation_enabled").commit()
            }
        }
    }
}
