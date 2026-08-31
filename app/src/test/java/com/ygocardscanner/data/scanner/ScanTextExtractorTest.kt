package com.ygocardscanner.data.scanner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanTextExtractorTest {
    @Test
    fun extractsAndNormalizesSetCodeBeforeOtherText() {
        val observation = ScanTextExtractor.extract("Dunkler Magier\nLOB-001\n89631139")

        assertEquals(listOf("LOB001"), observation.setCodeCandidates)
        assertEquals(listOf("89631139"), observation.passcodeCandidates)
    }

    @Test
    fun extractsModernLocalizedSetCode() {
        val observation = ScanTextExtractor.extract("Geschwindigkeitsroid Taketomborg HSRD-EN006")

        assertEquals(listOf("HSRDEN006"), observation.setCodeCandidates)
    }

    @Test
    fun retainsGermanAndEnglishNameLinesWithoutPersistingRawFrameData() {
        val observation = ScanTextExtractor.extract("Blue-Eyes White Dragon\nBlauäugiger w. Drache\nSDK-001")

        assertTrue(observation.nameCandidates.contains("Blue-Eyes White Dragon"))
        assertTrue(observation.nameCandidates.contains("Blauäugiger w. Drache"))
    }
}

