package com.ygocardscanner.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.ygocardscanner.data.local.entity.PriceSnapshot
import kotlinx.coroutines.flow.Flow

@Dao
interface PriceDao {
    @Upsert
    suspend fun upsertSnapshots(snapshots: List<PriceSnapshot>)

    /** Returns local observations; presentation ordering is handled by the repository. */
    @Query(
        """
        SELECT * FROM price_snapshots
        WHERE card_id = :cardId
            AND (printing_id IS NULL OR printing_id = :printingId)
        """,
    )
    fun observeForCardAndPrinting(cardId: String, printingId: String?): Flow<List<PriceSnapshot>>
}