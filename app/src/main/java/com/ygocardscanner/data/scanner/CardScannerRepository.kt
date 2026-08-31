package com.ygocardscanner.data.scanner

import com.ygocardscanner.model.CardLanguage

/** Local-only catalog matcher used by the camera scanner. */
interface CardScannerRepository {
    suspend fun match(
        observation: ScanTextObservation,
        displayLanguage: CardLanguage,
    ): ScanMatchResult
}