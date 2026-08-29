package com.ygocardscanner.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "cards",
    indices = [
        Index(value = ["source_id", "provider_card_id"], unique = true),
        Index(value = ["passcode"]),
    ],
)
data class Card(
    @PrimaryKey
    @ColumnInfo(name = "card_id")
    val cardId: String,
    @ColumnInfo(name = "source_id")
    val sourceId: String,
    @ColumnInfo(name = "provider_card_id")
    val providerCardId: String,
    val passcode: String?,
    @ColumnInfo(name = "canonical_name")
    val canonicalName: String,
    @ColumnInfo(name = "is_active")
    val isActive: Boolean,
    @ColumnInfo(name = "catalog_revision")
    val catalogRevision: String,
    @ColumnInfo(name = "updated_at_epoch_millis")
    val updatedAtEpochMillis: Long,
)
