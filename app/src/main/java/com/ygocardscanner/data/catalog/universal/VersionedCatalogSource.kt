package com.ygocardscanner.data.catalog.universal
/**
 * Optional capability for sources that can cheaply report whether their upstream catalog changed.
 *
 * This intentionally extends no Room or repository type: callers can feature-detect it with an
 * `is VersionedCatalogSource` check while existing bundled sources remain unchanged.
 */
interface VersionedCatalogSource {
    /** Sources replaced by this source after a successful complete import. */
    val supersededSourceIds: Set<String> get() = emptySet()

    suspend fun fetchRevision(): CatalogRevision
}

data class CatalogRevision(
    val sourceId: String,
    val revision: String,
    val contentHash: String? = null,
)

