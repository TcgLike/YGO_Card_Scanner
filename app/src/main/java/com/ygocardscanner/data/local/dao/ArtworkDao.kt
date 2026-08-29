package com.ygocardscanner.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.ygocardscanner.data.local.entity.CardArtwork
import com.ygocardscanner.data.local.entity.CardArtworkCache

@Dao
interface ArtworkDao {
    @Upsert
    suspend fun upsertArtworks(artworks: List<CardArtwork>)

    @Query("UPDATE card_artworks SET is_active = 0 WHERE source_id = :sourceId")
    suspend fun deactivateArtworks(sourceId: String)

    @Query(
        """
        SELECT a.*
        FROM card_artworks AS a
        INNER JOIN cards AS c ON c.card_id = a.card_id
        WHERE a.card_id = :cardId AND a.is_active = 1 AND c.is_active = 1
        LIMIT 1
        """,
    )
    suspend fun getActiveArtwork(cardId: String): CardArtwork?

    @Query("SELECT * FROM card_artwork_cache WHERE card_id = :cardId LIMIT 1")
    suspend fun getCache(cardId: String): CardArtworkCache?

    @Upsert
    suspend fun upsertCache(cache: CardArtworkCache)
}
