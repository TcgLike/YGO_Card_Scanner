package com.ygocardscanner.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.ygocardscanner.data.local.entity.OfficialDeckCard
import com.ygocardscanner.data.local.entity.OfficialDeckCatalogState
import com.ygocardscanner.data.local.entity.OfficialDeckProduct
import com.ygocardscanner.data.local.entity.OfficialDeckVariant
import kotlinx.coroutines.flow.Flow

@Dao
interface OfficialDeckDao {
    @Query("SELECT * FROM official_deck_catalog_state WHERE source_id = :sourceId LIMIT 1")
    suspend fun state(sourceId: String): OfficialDeckCatalogState?

    @Upsert suspend fun upsertState(state: OfficialDeckCatalogState)
    @Upsert suspend fun upsertProducts(products: List<OfficialDeckProduct>)
    @Upsert suspend fun upsertVariants(variants: List<OfficialDeckVariant>)
    @Upsert suspend fun upsertCards(cards: List<OfficialDeckCard>)

    @Query("DELETE FROM official_deck_cards")
    suspend fun deleteCards()

    @Query("DELETE FROM official_deck_variants")
    suspend fun deleteVariants()

    @Query("DELETE FROM official_deck_products")
    suspend fun deleteProducts()

    @Query("SELECT * FROM official_deck_products ORDER BY product_type, release_date DESC, title COLLATE NOCASE")
    fun observeProducts(): Flow<List<OfficialDeckProduct>>

    @Query("SELECT * FROM official_deck_products ORDER BY product_type, release_date DESC, title COLLATE NOCASE")
    suspend fun products(): List<OfficialDeckProduct>

    @Query("SELECT * FROM official_deck_variants WHERE product_id = :productId ORDER BY title COLLATE NOCASE")
    suspend fun variants(productId: String): List<OfficialDeckVariant>

    @Query("SELECT * FROM official_deck_variants WHERE variant_id = :variantId LIMIT 1")
    suspend fun variant(variantId: String): OfficialDeckVariant?

    @Query("SELECT * FROM official_deck_cards WHERE variant_id = :variantId AND option_group_id = '' ORDER BY section_code, passcode")
    suspend fun cards(variantId: String): List<OfficialDeckCard>
}