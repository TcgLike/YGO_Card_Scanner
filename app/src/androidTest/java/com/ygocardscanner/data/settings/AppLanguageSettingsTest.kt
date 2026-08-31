package com.ygocardscanner.data.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ygocardscanner.model.CollectionLayout
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
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
    @Test
    fun collectionLayoutPreferencePersistsAcrossSettingsRecreation() = runBlocking {
        val preferences = context.getSharedPreferences("display_settings", Context.MODE_PRIVATE)
        val hadValue = preferences.contains("collection_layout")
        val previousValue = preferences.getString("collection_layout", null)
        try {
            AppLanguageSettings(context).setCollectionLayout(CollectionLayout.ARTWORK_TILES)
            assertEquals(CollectionLayout.ARTWORK_TILES, AppLanguageSettings(context).collectionLayout.value)

            AppLanguageSettings(context).setCollectionLayout(CollectionLayout.COMPACT)
            assertEquals(CollectionLayout.COMPACT, AppLanguageSettings(context).collectionLayout.value)
        } finally {
            if (hadValue) {
                preferences.edit().putString("collection_layout", previousValue).commit()
            } else {
                preferences.edit().remove("collection_layout").commit()
            }
        }
    }
}

