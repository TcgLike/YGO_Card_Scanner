package com.ygocardscanner.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.ygocardscanner.data.local.entity.CatalogUpdateState
import kotlinx.coroutines.flow.Flow

@Dao
interface CatalogUpdateStateDao {
    @Upsert
    suspend fun upsert(state: CatalogUpdateState)

    @Query("SELECT * FROM catalog_update_state WHERE source_id = :sourceId LIMIT 1")
    suspend fun get(sourceId: String): CatalogUpdateState?

    @Query("SELECT * FROM catalog_update_state WHERE source_id = :sourceId LIMIT 1")
    fun observe(sourceId: String): Flow<CatalogUpdateState?>
}

