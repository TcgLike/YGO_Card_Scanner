package com.ygocardscanner.data.repository

/**
 * Durable state for the public catalog download. The UI observes this only after it has been
 * written to Room; it never observes a network response or WorkManager directly.
 */
enum class CatalogUpdatePhase(val code: String) {
    QUEUED("queued"),
    RUNNING("running"),
    RETRYING("retrying"),
    SUCCEEDED("succeeded"),
    FAILED("failed"),
    ;

    val isInProgress: Boolean
        get() = this == QUEUED || this == RUNNING || this == RETRYING

    companion object {
        fun fromCode(code: String): CatalogUpdatePhase = entries.firstOrNull { it.code == code }
            ?: FAILED
    }
}

data class CatalogUpdateStatus(
    val sourceId: String,
    val phase: CatalogUpdatePhase,
    val lastAttemptAtEpochMillis: Long?,
    val lastSuccessAtEpochMillis: Long?,
    val lastFailureAtEpochMillis: Long?,
    val message: String?,
)

sealed interface CatalogRefreshResult {
    data object Updated : CatalogRefreshResult
    data object UpToDate : CatalogRefreshResult
}

