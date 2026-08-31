package com.ygocardscanner.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** English canonical artwork metadata from the public catalog. Image bytes live in app-private files. */
@Entity(
    tableName = "card_artworks",
    foreignKeys = [
        ForeignKey(
            entity = Card::class,
            parentColumns = ["card_id"],
            childColumns = ["card_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index(value = ["card_id"], unique = true),
        Index(value = ["source_id"]),
    ],
)
data class CardArtwork(
    @PrimaryKey
    @ColumnInfo(name = "artwork_id")
    val artworkId: String,
    @ColumnInfo(name = "card_id")
    val cardId: String,
    @ColumnInfo(name = "source_id")
    val sourceId: String,
    @ColumnInfo(name = "provider_artwork_id")
    val providerArtworkId: String,
    @ColumnInfo(name = "remote_url")
    val remoteUrl: String,
    @ColumnInfo(name = "is_active")
    val isActive: Boolean,
    @ColumnInfo(name = "catalog_revision")
    val catalogRevision: String,
    @ColumnInfo(name = "updated_at_epoch_millis")
    val updatedAtEpochMillis: Long,
)

