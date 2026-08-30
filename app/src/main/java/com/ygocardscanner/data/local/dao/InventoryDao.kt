package com.ygocardscanner.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ygocardscanner.data.local.entity.InventoryEntry
import com.ygocardscanner.data.local.query.CollectionEntryRow
import com.ygocardscanner.data.local.query.InventoryEntryDetailRow
import kotlinx.coroutines.flow.Flow

@Dao
interface InventoryDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entry: InventoryEntry)

    @Query(
        """
        UPDATE inventory_entries
        SET quantity = :quantity, updated_at_epoch_millis = :updatedAtEpochMillis
        WHERE entry_id = :entryId
        """,
    )
    suspend fun updateQuantity(entryId: String, quantity: Int, updatedAtEpochMillis: Long): Int

    @Query(
        """
        SELECT * FROM inventory_entries
        WHERE card_id = :cardId
            AND (printing_id = :printingId OR (printing_id IS NULL AND :printingId IS NULL))
            AND language_code = :languageCode
            AND condition_code = :conditionCode
            AND edition_code = :editionCode
            AND COALESCE(rarity_code, '') = COALESCE(:rarityCode, '')
            AND notes = :notes
        ORDER BY created_at_epoch_millis
        LIMIT 1
        """,
    )
    suspend fun findMatchingDeckImportEntry(
        cardId: String,
        printingId: String?,
        languageCode: String,
        rarityCode: String?,
        editionCode: String,
        conditionCode: String,
        notes: String,
    ): InventoryEntry?

    @Query(
        """
        UPDATE inventory_entries
        SET quantity = quantity + :quantityDelta, updated_at_epoch_millis = :updatedAtEpochMillis
        WHERE entry_id = :entryId
        """,
    )
    suspend fun incrementQuantity(entryId: String, quantityDelta: Int, updatedAtEpochMillis: Long): Int
    @Query("UPDATE inventory_entries SET condition_code = :conditionCode, updated_at_epoch_millis = :updatedAtEpochMillis WHERE entry_id = :entryId")
    suspend fun updateCondition(entryId: String, conditionCode: String, updatedAtEpochMillis: Long): Int
    @Query("DELETE FROM inventory_entries WHERE entry_id = :entryId")
    suspend fun deleteById(entryId: String): Int

    @Query(
        """
        SELECT
            e.*,
            COALESCE(preferred.name, english.name, c.canonical_name) AS display_name,
            c.canonical_name AS canonical_name,
            c.passcode AS passcode,
            p.set_name AS catalog_set_name,
            a.remote_url AS artwork_remote_url,
            cache.local_file_name AS artwork_local_file_name,
            cache.download_state AS artwork_download_state,
            cache.safe_error_text AS artwork_message
        FROM inventory_entries AS e
        INNER JOIN cards AS c ON c.card_id = e.card_id
        LEFT JOIN printings AS p ON p.printing_id = e.printing_id
        LEFT JOIN card_artworks AS a
            ON a.card_id = c.card_id
            AND a.is_active = 1
        LEFT JOIN card_artwork_cache AS cache
            ON cache.card_id = a.card_id
            AND cache.remote_url_snapshot = a.remote_url
        
        LEFT JOIN card_texts AS preferred
            ON preferred.card_id = c.card_id
            AND preferred.language_code = :displayLanguageCode
        LEFT JOIN card_texts AS english
            ON english.card_id = c.card_id
            AND english.language_code = 'en'
        WHERE (
            :nameQuery = ''
            OR COALESCE(e.normalized_set_code_snapshot, '') LIKE '%' || :compactQuery || '%'
            OR COALESCE(c.passcode, '') LIKE '%' || :compactQuery || '%'
            OR EXISTS (
                SELECT 1 FROM card_texts AS searchable
                WHERE searchable.card_id = e.card_id
                    AND searchable.normalized_name LIKE '%' || :nameQuery || '%'
            )
            OR LOWER(e.notes) LIKE '%' || LOWER(:rawQuery) || '%'
        )
        ORDER BY display_name COLLATE NOCASE, e.created_at_epoch_millis DESC
        """,
    )
    fun observeCollection(
        rawQuery: String,
        nameQuery: String,
        compactQuery: String,
        displayLanguageCode: String,
    ): Flow<List<CollectionEntryRow>>

    @Query(
        """
        SELECT
            e.*,
            COALESCE(preferred.name, english.name, c.canonical_name) AS display_name,
            c.canonical_name AS canonical_name,
            c.passcode AS passcode,
            p.set_name AS catalog_set_name,
            a.remote_url AS artwork_remote_url,
            cache.local_file_name AS artwork_local_file_name,
            cache.download_state AS artwork_download_state,
            cache.safe_error_text AS artwork_message
        FROM inventory_entries AS e
        INNER JOIN cards AS c ON c.card_id = e.card_id
        LEFT JOIN printings AS p ON p.printing_id = e.printing_id
        LEFT JOIN card_artworks AS a
            ON a.card_id = c.card_id
            AND a.is_active = 1
        LEFT JOIN card_artwork_cache AS cache
            ON cache.card_id = a.card_id
            AND cache.remote_url_snapshot = a.remote_url
        LEFT JOIN card_texts AS preferred
            ON preferred.card_id = c.card_id
            AND preferred.language_code = e.language_code
        LEFT JOIN card_texts AS english
            ON english.card_id = c.card_id
            AND english.language_code = 'en'
        WHERE e.entry_id = :entryId
        LIMIT 1
        """,
    )
    fun observeEntry(entryId: String): Flow<InventoryEntryDetailRow?>
}