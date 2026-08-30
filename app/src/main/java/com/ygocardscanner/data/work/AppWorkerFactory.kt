package com.ygocardscanner.data.work

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import com.ygocardscanner.data.repository.CardArtworkRepository
import com.ygocardscanner.data.repository.CatalogRepository
import com.ygocardscanner.data.repository.GermanPrintingEnrichmentRepository
import com.ygocardscanner.model.CardGame

/** Manual worker graph. Pokémon dependencies are selected only by Pokémon-tagged work requests. */
class AppWorkerFactory(
    private val catalogRepository: CatalogRepository,
    private val artworkRepository: CardArtworkRepository,
    private val germanPrintingRepository: GermanPrintingEnrichmentRepository? = null,
    private val pokemonCatalogRepository: CatalogRepository? = null,
    private val pokemonArtworkRepository: CardArtworkRepository? = null,
) : WorkerFactory() {
    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters,
    ): ListenableWorker? {
        val game = CardGame.fromCode(workerParameters.inputData.getString(CatalogUpdateWorker.KEY_GAME))
        val selectedCatalog = if (game == CardGame.POKEMON) pokemonCatalogRepository else catalogRepository
        val selectedArtwork = if (game == CardGame.POKEMON) pokemonArtworkRepository else artworkRepository
        return when (workerClassName) {
            CatalogUpdateWorker::class.java.name ->
                selectedCatalog?.let { CatalogUpdateWorker(appContext, workerParameters, it) }
            GermanPrintingUpdateWorker::class.java.name ->
                germanPrintingRepository?.let { GermanPrintingUpdateWorker(appContext, workerParameters, it) }
            CardArtworkUpdateWorker::class.java.name ->
                selectedArtwork?.let { CardArtworkUpdateWorker(appContext, workerParameters, it) }
            FullArtworkDownloadWorker::class.java.name ->
                selectedArtwork?.let { FullArtworkDownloadWorker(appContext, workerParameters, it) }
            else -> null
        }
    }
}