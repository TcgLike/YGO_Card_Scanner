package com.ygocardscanner.data.catalog

import android.content.Context
import com.ygocardscanner.data.catalog.network.CatalogPayload
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The only catalog source used by the Inventory MVP. It never accesses the network. */
class DevelopmentCatalogSource(
    private val context: Context,
) : CatalogSource {
    override val sourceId: String = SOURCE_ID

    override suspend fun loadCatalog(): CatalogPayload = withContext(Dispatchers.IO) {
        val rawJson = context.assets.open(ASSET_NAME).bufferedReader().use { it.readText() }
        DevelopmentCatalogParser.parse(rawJson).also { payload ->
            require(payload.sourceId == sourceId) {
                "Bundled catalog source '${payload.sourceId}' does not match '$sourceId'."
            }
        }
    }

    companion object {
        const val SOURCE_ID = "development-seed"
        const val ASSET_NAME = "development_catalog.json"
    }
}
