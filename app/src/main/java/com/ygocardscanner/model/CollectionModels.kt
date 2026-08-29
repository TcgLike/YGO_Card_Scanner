package com.ygocardscanner.model

enum class CardLanguage(val code: String, val label: String) {
    ENGLISH("en", "English"),
    GERMAN("de", "Deutsch"),
    ;

    companion object {
        fun fromCode(code: String): CardLanguage = entries.firstOrNull { it.code == code.lowercase() }
            ?: ENGLISH
    }
}

enum class CardCondition(val code: String, val label: String) {
    MINT("mint", "Mint"),
    NEAR_MINT("near_mint", "Near mint"),
    LIGHTLY_PLAYED("lightly_played", "Lightly played"),
    MODERATELY_PLAYED("moderately_played", "Moderately played"),
    HEAVILY_PLAYED("heavily_played", "Heavily played"),
    DAMAGED("damaged", "Damaged"),
    ;

    companion object {
        fun fromCode(code: String): CardCondition = entries.firstOrNull { it.code == code } ?: NEAR_MINT
    }
}

enum class CardEdition(val code: String, val label: String) {
    FIRST_EDITION("first_edition", "1st Edition"),
    UNLIMITED("unlimited", "Unlimited"),
    LIMITED("limited", "Limited"),
    UNKNOWN("unknown", "Unknown"),
    ;

    companion object {
        fun fromCode(code: String?): CardEdition = entries.firstOrNull { it.code == code } ?: UNKNOWN
    }
}

enum class PrintingKind(val code: String) {
    KNOWN("known"),
    UNKNOWN("unknown"),
    ;

    companion object {
        fun fromCode(code: String): PrintingKind = entries.firstOrNull { it.code == code } ?: UNKNOWN
    }
}

data class CatalogPrintingSummary(
    val printingId: String,
    val cardId: String,
    val displayName: String,
    val setCode: String,
    val setName: String?,
    val language: CardLanguage,
    val rarity: String?,
    val edition: CardEdition,
)

data class CollectionEntrySummary(
    val entryId: String,
    val cardName: String,
    val setCode: String?,
    val language: CardLanguage,
    val rarity: String?,
    val edition: CardEdition,
    val condition: CardCondition,
    val quantity: Int,
    val isUnknownPrinting: Boolean,
    /** Local-only English artwork cache state; remote URLs never reach the collection UI. */
    val artwork: CardArtworkDetail?,
)

data class InventoryEntryDetail(
    val entryId: String,
    val cardId: String,
    val cardName: String,
    val canonicalName: String,
    val passcode: String?,
    val setCode: String?,
    val setName: String?,
    val language: CardLanguage,
    val rarity: String?,
    val edition: CardEdition,
    val condition: CardCondition,
    val quantity: Int,
    val notes: String,
    val printingKind: PrintingKind,
    val artwork: CardArtworkDetail?,
)

data class KnownPrintingDraft(
    val printingId: String,
    val language: CardLanguage,
    val rarity: String?,
    val edition: CardEdition,
    val condition: CardCondition,
    val quantity: Int,
    val notes: String,
)

data class UnknownPrintingDraft(
    val cardName: String,
    val setCode: String?,
    val language: CardLanguage,
    val rarity: String?,
    val edition: CardEdition,
    val condition: CardCondition,
    val quantity: Int,
    val notes: String,
)
