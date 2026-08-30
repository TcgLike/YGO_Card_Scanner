package com.ygocardscanner.data.deckimport.yugioh

import com.ygocardscanner.model.CardLanguage

/** Read-only local collection check for a parsed Yu-Gi-Oh! deck. */
interface YgoDeckAvailabilityRepository {
    suspend fun check(
        document: YgoDeckDocument,
        language: CardLanguage,
    ): YgoDeckAvailabilityPreview
}