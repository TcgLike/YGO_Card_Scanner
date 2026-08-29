package com.ygocardscanner.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Explicit, additive migrations only. There is intentionally no destructive fallback.
 */
object AppDatabaseMigrations {
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                "ALTER TABLE inventory_entries ADD COLUMN normalized_set_code_snapshot TEXT",
            )
            database.execSQL(
                """
                CREATE INDEX IF NOT EXISTS index_inventory_entries_normalized_set_code_snapshot
                ON inventory_entries(normalized_set_code_snapshot)
                """.trimIndent(),
            )
        }
    }

    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `catalog_update_state` (
                    `source_id` TEXT NOT NULL,
                    `phase` TEXT NOT NULL,
                    `last_attempt_at_epoch_millis` INTEGER,
                    `last_success_at_epoch_millis` INTEGER,
                    `last_failure_at_epoch_millis` INTEGER,
                    `safe_error_text` TEXT,
                    PRIMARY KEY(`source_id`)
                )
                """.trimIndent(),
            )
        }
    }

    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `card_artworks` (
                    `artwork_id` TEXT NOT NULL,
                    `card_id` TEXT NOT NULL,
                    `source_id` TEXT NOT NULL,
                    `provider_artwork_id` TEXT NOT NULL,
                    `remote_url` TEXT NOT NULL,
                    `is_active` INTEGER NOT NULL,
                    `catalog_revision` TEXT NOT NULL,
                    `updated_at_epoch_millis` INTEGER NOT NULL,
                    PRIMARY KEY(`artwork_id`),
                    FOREIGN KEY(`card_id`) REFERENCES `cards`(`card_id`) ON UPDATE NO ACTION ON DELETE RESTRICT
                )
                """.trimIndent(),
            )
            database.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_card_artworks_card_id` ON `card_artworks` (`card_id`)",
            )
            database.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_card_artworks_source_id` ON `card_artworks` (`source_id`)",
            )
            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `card_artwork_cache` (
                    `card_id` TEXT NOT NULL,
                    `remote_url_snapshot` TEXT NOT NULL,
                    `local_file_name` TEXT,
                    `download_state` TEXT NOT NULL,
                    `last_attempt_at_epoch_millis` INTEGER,
                    `last_success_at_epoch_millis` INTEGER,
                    `safe_error_text` TEXT,
                    PRIMARY KEY(`card_id`),
                    FOREIGN KEY(`card_id`) REFERENCES `cards`(`card_id`) ON UPDATE NO ACTION ON DELETE RESTRICT
                )
                """.trimIndent(),
            )
        }
    }
    val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `artwork_pack_state` (
                    `source_id` TEXT NOT NULL,
                    `phase` TEXT NOT NULL,
                    `total_artwork_count` INTEGER NOT NULL,
                    `completed_artwork_count` INTEGER NOT NULL,
                    `failed_artwork_count` INTEGER NOT NULL,
                    `next_offset` INTEGER NOT NULL,
                    `cached_bytes` INTEGER NOT NULL,
                    `updated_at_epoch_millis` INTEGER NOT NULL,
                    `safe_error_text` TEXT,
                    PRIMARY KEY(`source_id`)
                )
                """.trimIndent(),
            )
        }
    }
}