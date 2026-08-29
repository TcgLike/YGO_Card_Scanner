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

    @Query("SELECT * FROM catalog_metadata WHERE source_id = :sourceId LIMIT 1")
    suspend fun getMetadata(sourceId: String): CatalogMetadata?

    @Query(
        """
        SELECT p.*, COALESCE(requested.name, english.name, c.canonical_name) AS display_name
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
        SELECT p.*, COALESCE(requested.name, english.name, c.canonical_name) AS display_name
        FROM printings AS p
        INNER JOIN cards AS c ON c.card_id = p.card_id
        LEFT JOIN card_texts AS requested
            ON requested.card_id = c.card_id
            AND requested.language_code = :languageCode
        LEFT JOIN card_texts AS english
            ON english.card_id = c.card_id
            AND english.language_code = 'en'
        WHERE p.printing_id = :printingId
        LIMIT 1
        """,
    )
    suspend fun getPrintingRow(printingId: String, languageCode: String): CatalogPrintingRow?
    @Query(
        """
        SELECT p.*, COALESCE(requested.name, english.name, c.canonical_name) AS display_name
        FROM printings AS p
        INNER JOIN cards AS c ON c.card_id = p.card_id
        LEFT JOIN card_texts AS requested ON requested.card_id = c.card_id
            AND requested.language_code = :languageCode AND requested.is_active = 1
        LEFT JOIN card_texts AS english ON english.card_id = c.card_id
            AND english.language_code = 'en' AND english.is_active = 1
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
        SELECT p.*, COALESCE(requested.name, english.name, c.canonical_name) AS display_name
        FROM printings AS p
        INNER JOIN cards AS c ON c.card_id = p.card_id
        LEFT JOIN card_texts AS requested ON requested.card_id = c.card_id
            AND requested.language_code = :languageCode AND requested.is_active = 1
        LEFT JOIN card_texts AS english ON english.card_id = c.card_id
            AND english.language_code = 'en' AND english.is_active = 1
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
        SELECT p.*, COALESCE(requested.name, english.name, c.canonical_name) AS display_name,
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
}
