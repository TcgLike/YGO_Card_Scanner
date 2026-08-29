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
class CatalogUpdateStateMigrationTest {
    private val databaseName = "catalog-update-state-migration-${UUID.randomUUID()}.db"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    @Test
    fun migratesV2ToV3WithoutLosingInventoryAndAddsStateTable() {
        helper.createDatabase(databaseName, 2).apply {
            execSQL(
                """
                INSERT INTO cards(
                    card_id, source_id, provider_card_id, passcode, canonical_name,
                    is_active, catalog_revision, updated_at_epoch_millis
                ) VALUES ('test:card', 'test', 'card', '89631139', 'Blue-Eyes White Dragon', 1, '2', 2)
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
                    'test:entry', 'test:card', 'test:printing', 'known', 'LOB-001', 'LOB001',
                    'en', 'ultra_rare', 'first_edition', 'near_mint', 3, 'keep this inventory', 2, 2
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
                WHERE entry_id = 'test:entry'
                """.trimIndent(),
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(3, cursor.getInt(0))
                assertEquals("keep this inventory", cursor.getString(1))
                assertEquals("LOB001", cursor.getString(2))
            }

            database.execSQL(
                """
                INSERT INTO catalog_update_state(
                    source_id, phase, last_attempt_at_epoch_millis, last_success_at_epoch_millis,
                    last_failure_at_epoch_millis, safe_error_text
                ) VALUES ('test-source', 'failed', 10, NULL, 11, 'Catalog update unavailable')
                """.trimIndent(),
            )
            database.query(
                """
                SELECT phase, last_attempt_at_epoch_millis, last_success_at_epoch_millis,
                    last_failure_at_epoch_millis, safe_error_text
                FROM catalog_update_state
                WHERE source_id = 'test-source'
                """.trimIndent(),
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("failed", cursor.getString(0))
                assertEquals(10L, cursor.getLong(1))
                assertTrue(cursor.isNull(2))
                assertEquals(11L, cursor.getLong(3))
                assertEquals("Catalog update unavailable", cursor.getString(4))
            }
        }
    }
}
