package com.ygocardscanner.ui.navigation

import android.net.Uri

object Destinations {
    const val COLLECTION = "collection"
    const val CATALOG = "catalog"
    const val ADD = "add"
    const val MANUAL = "manual"
    const val SCANNER = "scanner"
    const val SETTINGS = "settings"
    const val DECK_IMPORT = "deck-import"
    const val OFFICIAL_DECKS = "official-decks"
    const val OFFICIAL_DECK_IMPORT_PATTERN = "deck-import/official/{variantId}"
    const val DECK_AVAILABILITY = "deck-availability"
    const val DETAIL_PATTERN = "detail/{entryId}"

    fun detail(entryId: String): String = "detail/${Uri.encode(entryId)}"
    fun officialDeckImport(variantId: String): String = "deck-import/official/${Uri.encode(variantId)}"
}

