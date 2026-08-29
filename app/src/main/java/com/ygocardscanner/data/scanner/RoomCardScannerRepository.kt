package com.ygocardscanner.data.scanner

import com.ygocardscanner.data.local.AppDatabase
import com.ygocardscanner.data.local.query.CatalogPrintingRow
import com.ygocardscanner.data.local.query.ScannerPrintingRow
import com.ygocardscanner.data.util.CatalogNormalizers
import com.ygocardscanner.model.CardEdition
import com.ygocardscanner.model.CardLanguage
import com.ygocardscanner.model.CatalogPrintingSummary
import kotlin.math.max

class RoomCardScannerRepository(database: AppDatabase) : CardScannerRepository {
    private val catalogDao = database.catalogDao()

    override suspend fun match(observation: ScanTextObservation): ScanMatchResult {
        observation.setCodeCandidates.forEach { code ->
            val matches = catalogDao.getActivePrintingsByNormalizedSetCode(code, DISPLAY_LANGUAGE)
            if (matches.isNotEmpty()) return candidates(ScanMatchKind.EXACT_SET_CODE, matches, code, EXACT_SCORE)
        }
        observation.passcodeCandidates.forEach { passcode ->
            val matches = catalogDao.getActivePrintingsByPasscode(passcode, DISPLAY_LANGUAGE)
            if (matches.isNotEmpty()) return candidates(ScanMatchKind.EXACT_PASSCODE, matches, passcode, EXACT_SCORE)
        }

        val ranked = observation.nameCandidates.flatMap { observedName ->
            val normalized = CatalogNormalizers.name(observedName)
            if (normalized.length < MIN_NAME_LENGTH) emptyList() else {
                catalogDao.getActivePrintingsByNameFragment(normalized, DISPLAY_LANGUAGE, NAME_SEARCH_LIMIT)
                    .map { row -> ScanCandidate(row.toSummary(), ScanMatchKind.FUZZY_LOCALIZED_NAME, similarity(normalized, CatalogNormalizers.name(row.matchedName ?: row.displayName))) }
            }
        }.distinctBy { it.printing.printingId }.sortedByDescending(ScanCandidate::score)

        val qualified = ranked.filter { it.score >= FUZZY_SCORE_THRESHOLD }.take(MAX_CANDIDATES)
        return if (qualified.isEmpty()) ScanMatchResult.NoMatch
        else ScanMatchResult.Candidates(ScanMatchKind.FUZZY_LOCALIZED_NAME, qualified, observation.nameCandidates.first())
    }

    private fun candidates(kind: ScanMatchKind, rows: List<CatalogPrintingRow>, fingerprint: String, score: Int) =
        ScanMatchResult.Candidates(kind, rows.map { ScanCandidate(it.toSummary(), kind, score) }, fingerprint)

    private fun CatalogPrintingRow.toSummary() = CatalogPrintingSummary(
        printingId = printing.printingId,
        cardId = printing.cardId,
        displayName = displayName,
        setCode = printing.setCode,
        setName = printing.setName,
        language = CardLanguage.fromCode(printing.languageCode),
        rarity = printing.rarityCode,
        edition = CardEdition.fromCode(printing.editionCode),
    )

    private fun ScannerPrintingRow.toSummary() = CatalogPrintingSummary(
        printingId = printing.printingId, cardId = printing.cardId, displayName = displayName,
        setCode = printing.setCode, setName = printing.setName,
        language = CardLanguage.fromCode(printing.languageCode), rarity = printing.rarityCode,
        edition = CardEdition.fromCode(printing.editionCode),
    )

    private fun similarity(left: String, right: String): Int {
        if (left == right) return EXACT_SCORE
        val longer = max(left.length, right.length)
        if (longer == 0) return 0
        val previous = IntArray(right.length + 1) { it }
        val current = IntArray(right.length + 1)
        left.forEachIndexed { i, leftChar ->
            current[0] = i + 1
            right.forEachIndexed { j, rightChar ->
                current[j + 1] = minOf(current[j] + 1, previous[j + 1] + 1, previous[j] + if (leftChar == rightChar) 0 else 1)
            }
            previous.indices.forEach { index -> previous[index] = current[index] }
        }
        return ((longer - previous[right.length]) * 100) / longer
    }

    private companion object {
        const val DISPLAY_LANGUAGE = "en"
        const val EXACT_SCORE = 100
        const val FUZZY_SCORE_THRESHOLD = 76
        const val MIN_NAME_LENGTH = 3
        const val NAME_SEARCH_LIMIT = 80
        const val MAX_CANDIDATES = 5
    }
}
