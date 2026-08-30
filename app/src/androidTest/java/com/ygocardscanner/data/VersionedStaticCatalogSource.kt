package com.ygocardscanner.data

import com.ygocardscanner.data.catalog.universal.CatalogRevision
import com.ygocardscanner.data.catalog.universal.CatalogSource
import com.ygocardscanner.data.catalog.universal.VersionedCatalogSource
import com.ygocardscanner.data.catalog.universal.CatalogPayload

/** Test-only public-catalog stand-in with version and legacy-source transition behavior. */
internal class VersionedStaticCatalogSource(
    private val payload: CatalogPayload,
    override val supersededSourceIds: Set<String> = emptySet(),
) : CatalogSource, VersionedCatalogSource {
    override val sourceId: String = payload.sourceId
    var loadCalls: Int = 0
        private set

    override suspend fun fetchRevision(): CatalogRevision = CatalogRevision(
        sourceId = sourceId,
        revision = payload.catalogRevision,
        contentHash = payload.contentHash,
    )

    override suspend fun loadCatalog(): CatalogPayload = payload.also { loadCalls += 1 }
}
