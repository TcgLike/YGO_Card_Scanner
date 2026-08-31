package com.ygocardscanner.ui.localization

import com.ygocardscanner.model.CardLanguage
import org.junit.Assert.assertEquals
import org.junit.Test

class UiTextTokenTest {
    @Test
    fun `token resolves both supported application languages`() {
        val token = UiTextToken(english = "Collection", german = "Sammlung")

        assertEquals("Collection", token.resolve(CardLanguage.ENGLISH))
        assertEquals("Sammlung", token.resolve(CardLanguage.GERMAN))
    }
}

