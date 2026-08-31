package com.ygocardscanner.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "printings",
    foreignKeys = [
        ForeignKey(
            entity = Card::class,
            parentColumns = ["card_id"],
            childColumns = ["card_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["source_id", "provider_printing_id"], unique = true),
        Index(value = ["card_id"]),
        Index(value = ["normalized_set_code"]),
    ],
)
data class Printing(
    @PrimaryKey
    @ColumnInfo(name = "printing_id")
    val printingId: String,
    @ColumnInfo(name = "card_id")
    val cardId: String,
    @ColumnInfo(name = "source_id")
    val sourceId: String,
    @ColumnInfo(name = "provider_printing_id")
    val providerPrintingId: String,
    @ColumnInfo(name = "set_code")
    val setCode: String,
    @ColumnInfo(name = "normalized_set_code")
    val normalizedSetCode: String,
    @ColumnInfo(name = "set_name")
    val setName: String?,
    @ColumnInfo(name = "language_code")
    val languageCode: String,
    @ColumnInfo(name = "rarity_code")
    val rarityCode: String?,
    @ColumnInfo(name = "edition_code")
    val editionCode: String?,
    @ColumnInfo(name = "is_active")
    val isActive: Boolean,
    @ColumnInfo(name = "catalog_revision")
    val catalogRevision: String,
    @ColumnInfo(name = "updated_at_epoch_millis")
    val updatedAtEpochMillis: Long,
)

