package com.ygocardscanner.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Durable, source-specific progress for catalog updates. This deliberately keeps operational
 * state separate from [CatalogMetadata], which describes a successfully installed catalog.
 */
@Entity(tableName = "catalog_update_state")
data class CatalogUpdateState(
    @PrimaryKey
    @ColumnInfo(name = "source_id")
    val sourceId: String,
    val phase: String,
    @ColumnInfo(name = "last_attempt_at_epoch_millis")
    val lastAttemptAtEpochMillis: Long?,
    @ColumnInfo(name = "last_success_at_epoch_millis")
    val lastSuccessAtEpochMillis: Long?,
    @ColumnInfo(name = "last_failure_at_epoch_millis")
    val lastFailureAtEpochMillis: Long?,
    /**
     * A short, user-safe status supplied by the update layer. Do not persist exception messages,
     * URLs, response bodies, or other raw external data here.
     */
    @ColumnInfo(name = "safe_error_text")
    val safeErrorText: String?,
) {
    init {
        require(sourceId.isNotBlank()) { "sourceId must not be blank" }
        require(safeErrorText.isNullOrBlank() || safeErrorText.length <= MAX_SAFE_ERROR_TEXT_LENGTH) {
            "safeErrorText must be at most $MAX_SAFE_ERROR_TEXT_LENGTH characters"
        }
    }

    companion object {
        const val MAX_SAFE_ERROR_TEXT_LENGTH = 240
    }
}
