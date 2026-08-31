package com.ygocardscanner.data.scanner

import com.ygocardscanner.model.CatalogPrintingSummary

/** Ephemeral OCR output. Neither frame data nor this text is stored on device. */
data class ScanTextObservation(
    val rawText: String,
    val setCodeCandidates: List<String>,
    val passcodeCandidates: List<String>,
    val nameCandidates: List<String>,
)

/** Transient OCR block in camera-image coordinates, used only to form a bulk-photo queue. */
data class OcrTextBlock(
    val text: String,
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
)

data class ScanTextRegion(
    val blocks: List<OcrTextBlock>,
) {
    val rawText: String = blocks.joinToString("\n") { it.text }
}

enum class ScanMatchKind {
    EXACT_SET_CODE,
    EXACT_PASSCODE,
    FUZZY_LOCALIZED_NAME,
}

data class ScanCandidate(
    val printing: CatalogPrintingSummary,
    val kind: ScanMatchKind,
    val score: Int,
)

sealed interface ScanMatchResult {
    data object NoMatch : ScanMatchResult
    data class Candidates(
        val kind: ScanMatchKind,
        val candidates: List<ScanCandidate>,
        val observedFingerprint: String,
        /** Normalized set codes recognized for this result. */
        val observedSetCodes: List<String> = emptyList(),
    ) : ScanMatchResult {
        val isAmbiguous: Boolean get() = candidates.size != 1
    }
}

enum class ScannerMode { LIVE, BULK_PHOTO }

data class BulkPhotoQueueItem(
    val region: ScanTextRegion,
    val match: ScanMatchResult.Candidates,
)