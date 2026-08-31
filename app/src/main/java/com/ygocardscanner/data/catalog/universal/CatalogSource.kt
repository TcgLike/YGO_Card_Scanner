package com.ygocardscanner.data.catalog.universal
import com.ygocardscanner.data.catalog.universal.CatalogPayload

/**
 * Boundary for catalog input. Implementations may read bundled content now and download public
 * catalog data in a later milestone; neither implementation is allowed to expose Room entities.
 */
interface CatalogSource {
    val sourceId: String

    suspend fun loadCatalog(): CatalogPayload
}

