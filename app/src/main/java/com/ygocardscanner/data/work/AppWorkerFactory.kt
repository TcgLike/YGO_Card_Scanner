package com.ygocardscanner.data.work

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import com.ygocardscanner.data.repository.CardArtworkRepository
import com.ygocardscanner.data.repository.CatalogRepository
import com.ygocardscanner.data.repository.GermanPrintingEnrichmentRepository

/** Manual worker graph for the single Yu-Gi-Oh! workspace. */
class AppWorkerFactory(
    private val catalogRepository: CatalogRepository,
    private val artworkRepository: CardArtworkRepository,
    private val germanPrintingRepository: GermanPrintingEnrichmentRepository? = null,
) : WorkerFactory() {
    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters,
    ): ListenableWorker? = when (workerClassName) {
        CatalogUpdateWorker::class.java.name -> CatalogUpdateWorker(appContext, workerParameters, catalogRepository)
        GermanPrintingUpdateWorker::class.java.name ->
            germanPrintingRepository?.let { GermanPrintingUpdateWorker(appContext, workerParameters, it) }
        CardArtworkUpdateWorker::class.java.name -> CardArtworkUpdateWorker(appContext, workerParameters, artworkRepository)
        FullArtworkDownloadWorker::class.java.name -> FullArtworkDownloadWorker(appContext, workerParameters, artworkRepository)
        else -> null
    }
}