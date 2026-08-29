package com.ygocardscanner.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "inventory_entries",
    foreignKeys = [
        ForeignKey(
            entity = Card::class,
            parentColumns = ["card_id"],
            childColumns = ["card_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = Printing::class,
            parentColumns = ["printing_id"],
            childColumns = ["printing_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["card_id"]),
        Index(value = ["printing_id"]),
        Index(value = ["set_code_snapshot"]),
        Index(value = ["normalized_set_code_snapshot"]),
    ],
)
data class InventoryEntry(
    @PrimaryKey
    @ColumnInfo(name = "entry_id")
    val entryId: String,
    @ColumnInfo(name = "card_id")
    val cardId: String,
    @ColumnInfo(name = "printing_id")
    val printingId: String?,
    @ColumnInfo(name = "printing_kind")
    val printingKind: String,
    @ColumnInfo(name = "set_code_snapshot")
    val setCodeSnapshot: String?,
    @ColumnInfo(name = "normalized_set_code_snapshot")
    val normalizedSetCodeSnapshot: String?,
    @ColumnInfo(name = "language_code")
    val languageCode: String,
    @ColumnInfo(name = "rarity_code")
    val rarityCode: String?,
    @ColumnInfo(name = "edition_code")
    val editionCode: String,
    @ColumnInfo(name = "condition_code")
    val conditionCode: String,
    val quantity: Int,
    val notes: String,
    @ColumnInfo(name = "created_at_epoch_millis")
    val createdAtEpochMillis: Long,
    @ColumnInfo(name = "updated_at_epoch_millis")
    val updatedAtEpochMillis: Long,
)
