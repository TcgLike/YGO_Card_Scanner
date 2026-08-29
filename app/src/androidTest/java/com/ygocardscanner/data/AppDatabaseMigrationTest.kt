package com.ygocardscanner.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ygocardscanner.data.local.AppDatabase
import com.ygocardscanner.data.local.AppDatabaseMigrations
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationTest {
    private val databaseName = "migration-${UUID.randomUUID()}.db"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    @Test
    fun migratesV1InventoryWithoutLosingQuantityOrNotes() {
        helper.createDatabase(databaseName, 1).apply {
            execSQL(
                """
                INSERT INTO cards(
                    card_id, source_id, provider_card_id, passcode, canonical_name,
                    is_active, catalog_revision, updated_at_epoch_millis
                ) VALUES ('test:card', 'test', 'card', '89631139', 'Blue-Eyes White Dragon', 1, '1', 1)
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO printings(
                    printing_id, card_id, source_id, provider_printing_id, set_code,
                    normalized_set_code, set_name, language_code, rarity_code, edition_code,
                    is_active, catalog_revision, updated_at_epoch_millis
                ) VALUES (
                    'test:printing', 'test:card', 'test', 'printing', 'LOB-001', 'LOB001',
                    'Test set', 'en', 'ultra_rare', 'first_edition', 1, '1', 1
                )
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO inventory_entries(
                    entry_id, card_id, printing_id, printing_kind, set_code_snapshot,
                    language_code, rarity_code, edition_code, condition_code, quantity, notes,
                    created_at_epoch_millis, updated_at_epoch_millis
                ) VALUES (
                    'test:entry', 'test:card', 'test:printing', 'known', 'LOB-001',
                    'en', 'ultra_rare', 'first_edition', 'near_mint', 3, 'pre-migration note', 1, 1
                )
                """.trimIndent(),
            )
            close()
        }

        helper.runMigrationsAndValidate(
            databaseName,
            2,
            true,
            AppDatabaseMigrations.MIGRATION_1_2,
        ).use { database ->
            database.query(
                """
                SELECT quantity, notes, normalized_set_code_snapshot
                FROM inventory_entries
                WHERE entry_id = 'test:entry'
                """.trimIndent(),
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(3, cursor.getInt(0))
                assertEquals("pre-migration note", cursor.getString(1))
                assertNull(cursor.getString(2))
            }
        }
    }

    @Test
    fun migratesV2InventoryWithoutLosingDataAndValidatesCatalogUpdateStateSchema() {
        helper.createDatabase(databaseName, 2).apply {
            execSQL(
                """
                INSERT INTO cards(
                    card_id, source_id, provider_card_id, passcode, canonical_name,
                    is_active, catalog_revision, updated_at_epoch_millis
                ) VALUES ('v2:card', 'test', 'card', '89631139', 'Blue-Eyes White Dragon', 1, '2', 2)
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO printings(
                    printing_id, card_id, source_id, provider_printing_id, set_code,
                    normalized_set_code, set_name, language_code, rarity_code, edition_code,
                    is_active, catalog_revision, updated_at_epoch_millis
                ) VALUES (
                    'v2:printing', 'v2:card', 'test', 'printing', 'LOB-001', 'LOB001',
                    'Test set', 'en', 'ultra_rare', 'first_edition', 1, '2', 2
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
                    'v2:entry', 'v2:card', 'v2:printing', 'known', 'LOB-001', 'LOB001',
                    'en', 'ultra_rare', 'first_edition', 'near_mint', 5, 'preserve v2 inventory', 2, 2
                )
                """.trimIndent(),
            )
            close()
        }

        helper.runMigrationsAndValidate(
            databaseName,
            3,
            true,
            AppDatabaseMigrations.MIGRATION_2_3,
        ).use { database ->
            database.query(
                """
                SELECT quantity, notes, normalized_set_code_snapshot
                FROM inventory_entries
                WHERE entry_id = 'v2:entry'
                """.trimIndent(),
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(5, cursor.getInt(0))
                assertEquals("preserve v2 inventory", cursor.getString(1))
                assertEquals("LOB001", cursor.getString(2))
            }
        }
    }

    @Test
    fun migratesV5ToPriceSnapshotsSchemaWithoutDestructiveFallback() {
        helper.createDatabase(databaseName, 5).close()

        helper.runMigrationsAndValidate(
            databaseName,
            6,
            true,
            AppDatabaseMigrations.MIGRATION_5_6,
        ).use { database ->
            database.query(
                "SELECT name FROM sqlite_master WHERE type = 'table' AND name = 'price_snapshots'",
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("price_snapshots", cursor.getString(0))
            }
        }
    }}
