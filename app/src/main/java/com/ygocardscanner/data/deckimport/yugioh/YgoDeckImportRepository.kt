package com.ygocardscanner.data.deckimport.yugioh

import com.ygocardscanner.model.CardLanguage

interface YgoDeckImportRepository {
    suspend fun preview(document: YgoDeckDocument, language: CardLanguage): YgoDeckImportPreview

    /** Validates every selection and writes the complete deck in a single transaction. */
    suspend fun importDeck(request: YgoDeckImportRequest): YgoDeckImportResult
}
