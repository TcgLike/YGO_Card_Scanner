package com.ygocardscanner.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

/** Durable state for the local, app-private copy of one catalog artwork. */
@Entity(
    tableName = "card_artwork_cache",
    foreignKeys = [
        ForeignKey(
            entity = Card::class,
            parentColumns = ["card_id"],
            childColumns = ["card_id"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
)
data class CardArtworkCache(
    @PrimaryKey
    @ColumnInfo(name = "card_id")
    val cardId: String,
    @ColumnInfo(name = "remote_url_snapshot")
    val remoteUrlSnapshot: String,
    @ColumnInfo(name = "local_file_name")
    val localFileName: String?,
    @ColumnInfo(name = "download_state")
    val downloadState: String,
    @ColumnInfo(name = "last_attempt_at_epoch_millis")
    val lastAttemptAtEpochMillis: Long?,
    @ColumnInfo(name = "last_success_at_epoch_millis")
    val lastSuccessAtEpochMillis: Long?,
    @ColumnInfo(name = "safe_error_text")
    val safeErrorText: String?,
) {
    companion object {
        const val MAX_SAFE_ERROR_TEXT_LENGTH = 240
    }
}
