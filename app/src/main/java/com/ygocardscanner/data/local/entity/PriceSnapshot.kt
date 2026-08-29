package com.ygocardscanner.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Latest locally cached public price observation.
 *
 * A row can describe a specific printing (when [printingId] is set) or a canonical card-level
 * vendor reference. Amounts are integer minor units to avoid floating-point currency errors.
 */
@Entity(
    tableName = "price_snapshots",
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
        Index(value = ["source_id", "provider_id"]),
    ],
)
data class PriceSnapshot(
    @PrimaryKey
    @ColumnInfo(name = "price_snapshot_id")
    val priceSnapshotId: String,
    @ColumnInfo(name = "card_id")
    val cardId: String,
    @ColumnInfo(name = "printing_id")
    val printingId: String?,
    @ColumnInfo(name = "source_id")
    val sourceId: String,
    /** Stable provider/vendor key, for example `set_price` or `cardmarket`. */
    @ColumnInfo(name = "provider_id")
    val providerId: String,
    @ColumnInfo(name = "currency_code")
    val currencyCode: String,
    @ColumnInfo(name = "amount_minor")
    val amountMinor: Long,
    /** Device time when this complete public catalog response was mapped locally. */
    @ColumnInfo(name = "observed_at_epoch_millis")
    val observedAtEpochMillis: Long,
)
