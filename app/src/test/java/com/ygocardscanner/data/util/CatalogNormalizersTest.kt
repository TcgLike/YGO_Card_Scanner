package com.ygocardscanner.data.util

import org.junit.Assert.assertEquals
import org.junit.Test

class CatalogNormalizersTest {
    @Test
    fun normalizesSetCodesWithoutPunctuation() {
        assertEquals("LOBDE001", CatalogNormalizers.setCode(" lob-de 001 "))
        assertEquals("MACREN036", CatalogNormalizers.setCode("MACR-EN036"))
    }

    @Test
    fun normalizesGermanNamesForLocalSearch() {
        assertEquals(
            "blauaugiger w drache",
            CatalogNormalizers.name("Blauäugiger w. Drache"),
        )
        assertEquals("weisser drache", CatalogNormalizers.name("Weißer Drache"))
    }
}
