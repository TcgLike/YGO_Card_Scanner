package com.ygocardscanner.data.deckimport.yugioh

import com.ygocardscanner.model.CardCondition
import com.ygocardscanner.model.CardEdition
import com.ygocardscanner.model.CardLanguage

enum class YgoDeckSection {
    MAIN,
    EXTRA,
    SIDE,
}

data class YgoDeckDocument(
    val sourceLabel: String,
    val cardsBySection: Map<YgoDeckSection, List<String>>,
) {
    val totalCardCount: Int get() = cardsBySection.values.sumOf(List<String>::size)
}

data class YgoDeckImportPreview(
    val sourceLabel: String,
    val totalCardCount: Int,
    /** Normalized optional printing-prefix filter, for example CH02DE. */
    val baseCodePrefix: String?,
    val cards: List<YgoDeckImportCard>,
)

data class YgoDeckImportCard(
    val passcode: String,
    val cardId: String?,
    val displayName: String?,
    val mainQuantity: Int,
    val extraQuantity: Int,
    val sideQuantity: Int,
    val printingChoices: List<YgoDeckPrintingChoice>,
    /** Candidate printings whose normalized set code starts with the chosen base-code prefix. */
    val matchingBaseCodePrintingIds: List<String> = emptyList(),
    /** Numeric suffix of the first matching base-code printing, used for review ordering. */
    val baseCodeSuffix: Int? = null,
) {
    val quantity: Int get() = mainQuantity + extraQuantity + sideQuantity
    val isResolved: Boolean get() = cardId != null
    val hasBaseCodeMatch: Boolean get() = matchingBaseCodePrintingIds.isNotEmpty()
}

data class YgoDeckPrintingChoice(
    val printingId: String,
    val label: String,
    val normalizedSetCode: String,
)

data class YgoDeckImportRequest(
    val cards: List<YgoDeckImportSelection>,
    val language: CardLanguage,
    val condition: CardCondition,
    val notes: String,
)

data class YgoDeckImportSelection(
    val passcode: String,
    val cardId: String,
    val printingId: String?,
    val quantity: Int,
)

data class YgoDeckImportResult(
    val addedEntryCount: Int,
    val addedCardCount: Int,
)

internal data class ResolvedDeckImportAttributes(
    val cardId: String,
    val printingId: String?,
    val setCode: String?,
    val normalizedSetCode: String?,
    val rarity: String?,
    val edition: CardEdition,
)
