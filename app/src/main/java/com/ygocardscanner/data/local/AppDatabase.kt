package com.ygocardscanner.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.ygocardscanner.data.local.dao.ArtworkDao
import com.ygocardscanner.data.local.dao.CatalogDao
import com.ygocardscanner.data.local.dao.CatalogUpdateStateDao
import com.ygocardscanner.data.local.dao.InventoryDao
import com.ygocardscanner.data.local.dao.OfficialDeckDao
import com.ygocardscanner.data.local.dao.PriceDao
import com.ygocardscanner.data.local.entity.ArtworkPackState
import com.ygocardscanner.data.local.entity.Card
import com.ygocardscanner.data.local.entity.CardArtwork
import com.ygocardscanner.data.local.entity.CardArtworkCache
import com.ygocardscanner.data.local.entity.CardText
import com.ygocardscanner.data.local.entity.CatalogMetadata
import com.ygocardscanner.data.local.entity.CatalogUpdateState
import com.ygocardscanner.data.local.entity.InventoryEntry
import com.ygocardscanner.data.local.entity.OfficialDeckCard
import com.ygocardscanner.data.local.entity.OfficialDeckCatalogState
import com.ygocardscanner.data.local.entity.OfficialDeckProduct
import com.ygocardscanner.data.local.entity.OfficialDeckVariant
import com.ygocardscanner.data.local.entity.PriceSnapshot
import com.ygocardscanner.data.local.entity.Printing

@Database(
    entities = [
        Card::class,
        CardText::class,
        Printing::class,
        InventoryEntry::class,
        CatalogMetadata::class,
        CatalogUpdateState::class,
        CardArtwork::class,
        CardArtworkCache::class,
        ArtworkPackState::class,
        PriceSnapshot::class,
        OfficialDeckCatalogState::class,
        OfficialDeckProduct::class,
        OfficialDeckVariant::class,
        OfficialDeckCard::class,
    ],
    version = 7,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun catalogDao(): CatalogDao
    abstract fun catalogUpdateStateDao(): CatalogUpdateStateDao
    abstract fun artworkDao(): ArtworkDao
    abstract fun inventoryDao(): InventoryDao
    abstract fun priceDao(): PriceDao
    abstract fun officialDeckDao(): OfficialDeckDao

    companion object {
        const val DATABASE_NAME = "ygo-card-scanner.db"

        fun create(context: Context, databaseName: String = DATABASE_NAME): AppDatabase = Room.databaseBuilder(
            context.applicationContext,
            AppDatabase::class.java,
            databaseName,
        ).addMigrations(
            AppDatabaseMigrations.MIGRATION_1_2,
            AppDatabaseMigrations.MIGRATION_2_3,
            AppDatabaseMigrations.MIGRATION_3_4,
            AppDatabaseMigrations.MIGRATION_4_5,
            AppDatabaseMigrations.MIGRATION_5_6,
            AppDatabaseMigrations.MIGRATION_6_7,
        ).build()
    }
}