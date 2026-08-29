package com.ygocardscanner.data.work

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import com.ygocardscanner.data.repository.CardArtworkRepository
import com.ygocardscanner.data.repository.CatalogRepository

/** Manual WorkerFactory keeps all public catalog work inside the explicit dependency graph. */
class AppWorkerFactory(
    private val catalogRepository: CatalogRepository,
    private val artworkRepository: CardArtworkRepository,
) : WorkerFactory() {
    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters,
    ): ListenableWorker? = when (workerClassName) {
        CatalogUpdateWorker::class.java.name ->
            CatalogUpdateWorker(appContext, workerParameters, catalogRepository)

        CardArtworkUpdateWorker::class.java.name ->
            CardArtworkUpdateWorker(appContext, workerParameters, artworkRepository)

        else -> null
    }
}