package com.ygocardscanner.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.ygocardscanner.data.local.entity.Card
import com.ygocardscanner.data.local.entity.CardText
import com.ygocardscanner.data.local.entity.CatalogMetadata
import com.ygocardscanner.data.local.entity.Printing
import com.ygocardscanner.data.local.query.CatalogPrintingRow
import com.ygocardscanner.data.local.query.CardPasscodeRow
import kotlinx.coroutines.flow.Flow

@Dao
interface CatalogDao {
    @Upsert
    suspend fun upsertCards(cards: List<Card>)

    @Upsert
    suspend fun upsertCardTexts(cardTexts: List<CardText>)

    @Upsert
    suspend fun upsertPrintings(printings: List<Printing>)

    @Upsert
    suspend fun upsertMetadata(metadata: CatalogMetadata)

    @Query("UPDATE cards SET is_active = 0 WHERE source_id = :sourceId")
    suspend fun deactivateCards(sourceId: String)

    @Query(
        """
        UPDATE card_texts SET is_active = 0
        WHERE card_id IN (SELECT card_id FROM cards WHERE source_id = :sourceId)
        """,
    )
    suspend fun deactivateCardTexts(sourceId: String)

    @Query("UPDATE printings SET is_active = 0 WHERE source_id = :sourceId")
    suspend fun deactivatePrintings(sourceId: String)

    @Query("UPDATE printings SET is_active = 1 WHERE source_id = :sourceId")
    suspend fun activatePrintings(sourceId: String)

    @Query(
        """
        SELECT card_id, passcode FROM cards
        WHERE source_id = :sourceId AND is_active = 1 AND passcode IS NOT NULL
        """,
    )
    suspend fun getActiveCardPasscodes(sourceId: String): List<CardPasscodeRow>

    @Query(
        """
        SELECT c.card_id AS card_id,
            c.passcode AS passcode,
            COALESCE(preferred.name, english.name, c.canonical_name) AS display_name
        FROM cards AS c
        LEFT JOIN card_texts AS preferred
            ON preferred.card_id = c.card_id
            AND preferred.language_code = :languageCode
            AND preferred.is_active = 1
        LEFT JOIN card_texts AS english
            ON english.card_id = c.card_id
            AND english.language_code = 'en'
            AND english.is_active = 1
        WHERE c.is_active = 1 AND c.passcode = :passcode
        LIMIT 1
        """,
    )
    suspend fun getActiveCardForDeckImport(
        passcode: String,
        languageCode: String,
    ): com.ygocardscanner.data.local.query.DeckImportCardRow?

    @Query(
        """
        SELECT c.passcode AS passcode
        FROM cards AS c
        INNER JOIN printings AS p ON p.card_id = c.card_id
        WHERE c.is_active = 1
            AND p.is_active = 1
            AND c.passcode IS NOT NULL
            AND p.normalized_set_code = :normalizedSetCode
        GROUP BY c.passcode
        """,
    )
    suspend fun getActivePasscodesByNormalizedSetCode(
        normalizedSetCode: String,
    ): List<String?>
    @Query("SELECT * FROM catalog_metadata WHERE source_id = :sourceId LIMIT 1")
    suspend fun getMetadata(sourceId: String): CatalogMetadata?

    @Query(
        """
        SELECT p.printing_id,
            p.card_id,
            p.source_id,
            p.provider_printing_id,
            p.set_code,
            p.normalized_set_code,
            p.set_name,
            p.language_code,
            p.rarity_code,
            p.edition_code,
            p.is_active,
            p.catalog_revision,
            p.updated_at_epoch_millis, COALESCE(requested.name, english.name, c.canonical_name) AS display_name,
            set_price.amount_minor AS printing_price_amount_minor,
            set_price.currency_code AS printing_price_currency_code,
            set_price.observed_at_epoch_millis AS printing_price_observed_at_epoch_millis,
            cardmarket_price.amount_minor AS fallback_price_amount_minor,
            cardmarket_price.currency_code AS fallback_price_currency_code,
            cardmarket_price.observed_at_epoch_millis AS fallback_price_observed_at_epoch_millis
        FROM printings AS p
        INNER JOIN cards AS c ON c.card_id = p.card_id
        LEFT JOIN card_texts AS requested
            ON requested.card_id = c.card_id
            AND requested.language_code = :languageCode
            AND requested.is_active = 1
        LEFT JOIN card_texts AS english
            ON english.card_id = c.card_id
            AND english.language_code = 'en'
            AND english.is_active = 1
        LEFT JOIN price_snapshots AS set_price
            ON set_price.printing_id = p.printing_id
            AND set_price.provider_id = 'set_price'
        LEFT JOIN price_snapshots AS cardmarket_price
            ON cardmarket_price.card_id = c.card_id
            AND cardmarket_price.printing_id IS NULL
            AND cardmarket_price.provider_id = 'cardmarket'
        WHERE p.is_active = 1
            AND c.is_active = 1
            AND :hasQuery = 1
            AND (
                (
                    :compactQuery <> ''
                    AND (
                        p.normalized_set_code LIKE '%' || :compactQuery || '%'
                        OR c.passcode LIKE '%' || :compactQuery || '%'
                    )
                )
                OR EXISTS (
                    SELECT 1 FROM card_texts AS searchable
                    WHERE searchable.card_id = c.card_id
                        AND searchable.is_active = 1
                        AND searchable.normalized_name LIKE '%' || :nameQuery || '%'
                )
            )
        ORDER BY display_name COLLATE NOCASE, p.set_code COLLATE NOCASE
        LIMIT :resultLimit
        """,
    )
    fun observeActivePrintings(
        nameQuery: String,
        compactQuery: String,
        languageCode: String,
        hasQuery: Boolean,
        resultLimit: Int,
    ): Flow<List<CatalogPrintingRow>>

    @Query(
        """
        SELECT p.printing_id,
            p.card_id,
            p.source_id,
            p.provider_printing_id,
            p.set_code,
            p.normalized_set_code,
            p.set_name,
            p.language_code,
            p.rarity_code,
            p.edition_code,
            p.is_active,
            p.catalog_revision,
            p.updated_at_epoch_millis, COALESCE(requested.name, english.name, c.canonical_name) AS display_name,
            set_price.amount_minor AS printing_price_amount_minor,
            set_price.currency_code AS printing_price_currency_code,
            set_price.observed_at_epoch_millis AS printing_price_observed_at_epoch_millis,
            cardmarket_price.amount_minor AS fallback_price_amount_minor,
            cardmarket_price.currency_code AS fallback_price_currency_code,
            cardmarket_price.observed_at_epoch_millis AS fallback_price_observed_at_epoch_millis
        FROM printings AS p
        INNER JOIN cards AS c ON c.card_id = p.card_id
        LEFT JOIN card_texts AS requested
            ON requested.card_id = c.card_id
            AND requested.language_code = :languageCode
        LEFT JOIN card_texts AS english
            ON english.card_id = c.card_id
            AND english.language_code = 'en'
        LEFT JOIN price_snapshots AS set_price
            ON set_price.printing_id = p.printing_id
            AND set_price.provider_id = 'set_price'
        LEFT JOIN price_snapshots AS cardmarket_price
            ON cardmarket_price.card_id = c.card_id
            AND cardmarket_price.printing_id IS NULL
            AND cardmarket_price.provider_id = 'cardmarket'
        WHERE p.printing_id = :printingId
        LIMIT 1
        """,
    )
    suspend fun getPrintingRow(printingId: String, languageCode: String): CatalogPrintingRow?
    @Query(
        """
        SELECT p.printing_id,
            p.card_id,
            p.source_id,
            p.provider_printing_id,
            p.set_code,
            p.normalized_set_code,
            p.set_name,
            p.language_code,
            p.rarity_code,
            p.edition_code,
            p.is_active,
            p.catalog_revision,
            p.updated_at_epoch_millis, COALESCE(requested.name, english.name, c.canonical_name) AS display_name,
            set_price.amount_minor AS printing_price_amount_minor,
            set_price.currency_code AS printing_price_currency_code,
            set_price.observed_at_epoch_millis AS printing_price_observed_at_epoch_millis,
            cardmarket_price.amount_minor AS fallback_price_amount_minor,
            cardmarket_price.currency_code AS fallback_price_currency_code,
            cardmarket_price.observed_at_epoch_millis AS fallback_price_observed_at_epoch_millis
        FROM printings AS p
        INNER JOIN cards AS c ON c.card_id = p.card_id
        LEFT JOIN card_texts AS requested ON requested.card_id = c.card_id
            AND requested.language_code = :languageCode AND requested.is_active = 1
        LEFT JOIN card_texts AS english ON english.card_id = c.card_id
            AND english.language_code = 'en' AND english.is_active = 1
        LEFT JOIN price_snapshots AS set_price
            ON set_price.printing_id = p.printing_id
            AND set_price.provider_id = 'set_price'
        LEFT JOIN price_snapshots AS cardmarket_price
            ON cardmarket_price.card_id = c.card_id
            AND cardmarket_price.printing_id IS NULL
            AND cardmarket_price.provider_id = 'cardmarket'
        WHERE p.is_active = 1 AND c.is_active = 1 AND p.normalized_set_code = :normalizedSetCode
        ORDER BY p.set_code COLLATE NOCASE
        """,
    )
    suspend fun getActivePrintingsByNormalizedSetCode(
        normalizedSetCode: String,
        languageCode: String,
    ): List<CatalogPrintingRow>

    @Query(
        """
        SELECT p.printing_id,
            p.card_id,
            p.source_id,
            p.provider_printing_id,
            p.set_code,
            p.normalized_set_code,
            p.set_name,
            p.language_code,
            p.rarity_code,
            p.edition_code,
            p.is_active,
            p.catalog_revision,
            p.updated_at_epoch_millis, COALESCE(requested.name, english.name, c.canonical_name) AS display_name,
            set_price.amount_minor AS printing_price_amount_minor,
            set_price.currency_code AS printing_price_currency_code,
            set_price.observed_at_epoch_millis AS printing_price_observed_at_epoch_millis,
            cardmarket_price.amount_minor AS fallback_price_amount_minor,
            cardmarket_price.currency_code AS fallback_price_currency_code,
            cardmarket_price.observed_at_epoch_millis AS fallback_price_observed_at_epoch_millis
        FROM printings AS p
        INNER JOIN cards AS c ON c.card_id = p.card_id
        LEFT JOIN card_texts AS requested ON requested.card_id = c.card_id
            AND requested.language_code = :languageCode AND requested.is_active = 1
        LEFT JOIN card_texts AS english ON english.card_id = c.card_id
            AND english.language_code = 'en' AND english.is_active = 1
        LEFT JOIN price_snapshots AS set_price
            ON set_price.printing_id = p.printing_id
            AND set_price.provider_id = 'set_price'
        LEFT JOIN price_snapshots AS cardmarket_price
            ON cardmarket_price.card_id = c.card_id
            AND cardmarket_price.printing_id IS NULL
            AND cardmarket_price.provider_id = 'cardmarket'
        WHERE p.is_active = 1 AND c.is_active = 1 AND c.passcode = :passcode
        ORDER BY p.set_code COLLATE NOCASE
        LIMIT :resultLimit
        """,
    )
    suspend fun getActivePrintingsByPasscode(
        passcode: String,
        languageCode: String,
        resultLimit: Int = 100,
    ): List<CatalogPrintingRow>

    @Query(
        """
        SELECT p.printing_id,
            p.card_id,
            p.source_id,
            p.provider_printing_id,
            p.set_code,
            p.normalized_set_code,
            p.set_name,
            p.language_code,
            p.rarity_code,
            p.edition_code,
            p.is_active,
            p.catalog_revision,
            p.updated_at_epoch_millis, COALESCE(requested.name, english.name, c.canonical_name) AS display_name,
            (SELECT matching.name FROM card_texts AS matching
                WHERE matching.card_id = c.card_id AND matching.is_active = 1
                    AND (matching.normalized_name LIKE '%' || :normalizedName || '%'
                        OR :normalizedName LIKE '%' || matching.normalized_name || '%')
                LIMIT 1) AS matched_name
        FROM printings AS p
        INNER JOIN cards AS c ON c.card_id = p.card_id
        LEFT JOIN card_texts AS requested ON requested.card_id = c.card_id
            AND requested.language_code = :languageCode AND requested.is_active = 1
        LEFT JOIN card_texts AS english ON english.card_id = c.card_id
            AND english.language_code = 'en' AND english.is_active = 1
        LEFT JOIN price_snapshots AS set_price
            ON set_price.printing_id = p.printing_id
            AND set_price.provider_id = 'set_price'
        LEFT JOIN price_snapshots AS cardmarket_price
            ON cardmarket_price.card_id = c.card_id
            AND cardmarket_price.printing_id IS NULL
            AND cardmarket_price.provider_id = 'cardmarket'
        WHERE p.is_active = 1 AND c.is_active = 1
            AND EXISTS (
                SELECT 1 FROM card_texts AS searchable
                WHERE searchable.card_id = c.card_id AND searchable.is_active = 1
                    AND (searchable.normalized_name LIKE '%' || :normalizedName || '%'
                        OR :normalizedName LIKE '%' || searchable.normalized_name || '%')
            )
        ORDER BY display_name COLLATE NOCASE, p.set_code COLLATE NOCASE
        LIMIT :resultLimit
        """,
    )
    suspend fun getActivePrintingsByNameFragment(
        normalizedName: String,
        languageCode: String,
        resultLimit: Int,
    ): List<com.ygocardscanner.data.local.query.ScannerPrintingRow>
    @Query(
        """
        SELECT
            c.card_id AS card_id,
            COALESCE(preferred.name, english.name, c.canonical_name) AS display_name,
            c.passcode AS passcode,
            a.remote_url AS artwork_remote_url,
            cache.local_file_name AS artwork_local_file_name,
            cache.download_state AS artwork_download_state,
            cache.safe_error_text AS artwork_message,
            CASE WHEN (
                SELECT COUNT(*) FROM inventory_entries AS entry
                WHERE entry.card_id = c.card_id AND entry.quantity > 0
            ) > 0 THEN 1 ELSE 0 END AS is_owned
        FROM cards AS c
        LEFT JOIN card_texts AS preferred
            ON preferred.card_id = c.card_id
            AND preferred.language_code = :languageCode
            AND preferred.is_active = 1
        LEFT JOIN card_texts AS english
            ON english.card_id = c.card_id
            AND english.language_code = 'en'
            AND english.is_active = 1
        LEFT JOIN card_artworks AS a
            ON a.card_id = c.card_id
            AND a.is_active = 1
        LEFT JOIN card_artwork_cache AS cache
            ON cache.card_id = a.card_id
            AND cache.remote_url_snapshot = a.remote_url
        WHERE c.is_active = 1
            AND c.source_id <> 'local'
            AND (
                :hasQuery = 0
                OR c.passcode LIKE '%' || :compactQuery || '%'
                OR EXISTS (
                    SELECT 1 FROM printings AS printing
                    WHERE printing.card_id = c.card_id
                        AND printing.is_active = 1
                        AND :compactQuery <> ''
                        AND printing.normalized_set_code LIKE '%' || :compactQuery || '%'
                )
                OR EXISTS (
                    SELECT 1 FROM card_texts AS searchable
                    WHERE searchable.card_id = c.card_id
                        AND searchable.is_active = 1
                        AND searchable.normalized_name LIKE '%' || :nameQuery || '%'
                )
            )
        ORDER BY display_name COLLATE NOCASE
        """,
    )
    fun observeActiveCards(
        nameQuery: String,
        compactQuery: String,
        languageCode: String,
        hasQuery: Boolean,
    ): Flow<List<com.ygocardscanner.data.local.query.CatalogCardRow>>
}

