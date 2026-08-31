package com.ygocardscanner.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/** Durable progress for the user-requested full English artwork download. */
@Entity(tableName = "artwork_pack_state")
data class ArtworkPackState(
    @PrimaryKey
    @ColumnInfo(name = "source_id")
    val sourceId: String,
    val phase: String,
    @ColumnInfo(name = "total_artwork_count")
    val totalArtworkCount: Int,
    @ColumnInfo(name = "completed_artwork_count")
    val completedArtworkCount: Int,
    @ColumnInfo(name = "failed_artwork_count")
    val failedArtworkCount: Int,
    @ColumnInfo(name = "next_offset")
    val nextOffset: Int,
    @ColumnInfo(name = "cached_bytes")
    val cachedBytes: Long,
    @ColumnInfo(name = "updated_at_epoch_millis")
    val updatedAtEpochMillis: Long,
    @ColumnInfo(name = "safe_error_text")
    val safeErrorText: String?,
) {
    companion object {
        const val MAX_SAFE_ERROR_TEXT_LENGTH = 240
    }
}

