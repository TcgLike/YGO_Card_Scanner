package com.ygocardscanner.data.scanner

/** Local-only catalog matcher used by the camera scanner. */
interface CardScannerRepository {
    suspend fun match(observation: ScanTextObservation): ScanMatchResult
}
