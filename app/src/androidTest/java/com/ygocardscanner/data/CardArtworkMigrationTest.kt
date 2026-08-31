package com.ygocardscanner.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ygocardscanner.data.local.AppDatabase
import com.ygocardscanner.data.local.AppDatabaseMigrations
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CardArtworkMigrationTest {
    private val databaseName = "artwork-migration-${UUID.randomUUID()}.db"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    @Test
    fun migratesV3ToV4WithoutChangingInventoryAndAddsArtworkTables() {
        helper.createDatabase(databaseName, 3).apply {
            execSQL(
                """
                INSERT INTO cards(
                    card_id, source_id, provider_card_id, passcode, canonical_name,
                    is_active, catalog_revision, updated_at_epoch_millis
                ) VALUES ('v3:card', 'test', 'card', '89631139', 'Blue-Eyes White Dragon', 1, '3', 3)
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO printings(
                    printing_id, card_id, source_id, provider_printing_id, set_code,
                    normalized_set_code, set_name, language_code, rarity_code, edition_code,
                    is_active, catalog_revision, updated_at_epoch_millis
                ) VALUES (
                    'v3:printing', 'v3:card', 'test', 'printing', 'LOB-001', 'LOB001',
                    'Test set', 'en', 'ultra_rare', 'first_edition', 1, '3', 3
                )
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO inventory_entries(
                    entry_id, card_id, printing_id, printing_kind, set_code_snapshot,
                    normalized_set_code_snapshot, language_code, rarity_code, edition_code,
                    condition_code, quantity, notes, created_at_epoch_millis,
                    updated_at_epoch_millis
                ) VALUES (
                    'v3:entry', 'v3:card', 'v3:printing', 'known', 'LOB-001', 'LOB001',
                    'en', 'ultra_rare', 'first_edition', 'near_mint', 7, 'keep inventory', 3, 3
                )
                """.trimIndent(),
            )
            close()
        }

        helper.runMigrationsAndValidate(
            databaseName,
            4,
            true,
            AppDatabaseMigrations.MIGRATION_3_4,
        ).use { database ->
            database.query("SELECT quantity, notes FROM inventory_entries WHERE entry_id = 'v3:entry'")
                .use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    assertEquals(7, cursor.getInt(0))
                    assertEquals("keep inventory", cursor.getString(1))
                }

            database.execSQL(
                """
                INSERT INTO card_artworks(
                    artwork_id, card_id, source_id, provider_artwork_id, remote_url,
                    is_active, catalog_revision, updated_at_epoch_millis
                ) VALUES (
                    'v3:artwork', 'v3:card', 'test', '89631139',
                    'https://images.ygoprodeck.com/images/cards/89631139.jpg', 1, '4', 4
                )
                """.trimIndent(),
            )
            database.execSQL(
                """
                INSERT INTO card_artwork_cache(
                    card_id, remote_url_snapshot, local_file_name, download_state,
                    last_attempt_at_epoch_millis, last_success_at_epoch_millis, safe_error_text
                ) VALUES (
                    'v3:card', 'https://images.ygoprodeck.com/images/cards/89631139.jpg',
                    'cached.img', 'available', 4, 4, NULL
                )
                """.trimIndent(),
            )
            database.query("SELECT local_file_name, download_state FROM card_artwork_cache WHERE card_id = 'v3:card'")
                .use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    assertEquals("cached.img", cursor.getString(0))
                    assertEquals("available", cursor.getString(1))
                }
        }
    }
    @Test
    fun migratesV4ToV5WithoutChangingInventoryAndAddsPackState() {
        helper.createDatabase(databaseName, 4).apply {
            execSQL(""" INSERT INTO cards(card_id, source_id, provider_card_id, passcode, canonical_name, is_active, catalog_revision, updated_at_epoch_millis) VALUES ('v4:card', 'test', 'card', NULL, 'Dark Magician', 1, '4', 4) """.trimIndent())
            execSQL(""" INSERT INTO inventory_entries(entry_id, card_id, printing_id, printing_kind, set_code_snapshot, normalized_set_code_snapshot, language_code, rarity_code, edition_code, condition_code, quantity, notes, created_at_epoch_millis, updated_at_epoch_millis) VALUES ('v4:entry', 'v4:card', NULL, 'unknown', NULL, NULL, 'en', NULL, 'unknown', 'near_mint', 9, 'preserve v4 inventory', 4, 4) """.trimIndent())
            close()
        }
        helper.runMigrationsAndValidate(databaseName, 5, true, AppDatabaseMigrations.MIGRATION_4_5).use { database ->
            database.query("SELECT quantity, notes FROM inventory_entries WHERE entry_id = 'v4:entry'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(9, cursor.getInt(0))
                assertEquals("preserve v4 inventory", cursor.getString(1))
            }
            database.execSQL(""" INSERT INTO artwork_pack_state(source_id, phase, total_artwork_count, completed_artwork_count, failed_artwork_count, next_offset, cached_bytes, updated_at_epoch_millis, safe_error_text) VALUES ('ygoprodeck-v7', 'queued', 10, 2, 0, 2, 100, 5, NULL) """.trimIndent())
            database.query("SELECT completed_artwork_count FROM artwork_pack_state WHERE source_id = 'ygoprodeck-v7'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(2, cursor.getInt(0))
            }
        }
    }
}