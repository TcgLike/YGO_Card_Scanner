package com.ygocardscanner.model

enum class ArtworkPackPhase(val code: String) {
    QUEUED("queued"),
    RUNNING("running"),
    RETRYING("retrying"),
    SUCCEEDED("succeeded"),
    QUOTA_REACHED("quota_reached"),
    FAILED("failed"),
    ;

    val isInProgress: Boolean
        get() = this == QUEUED || this == RUNNING || this == RETRYING

    companion object {
        fun fromCode(code: String): ArtworkPackPhase =
            entries.firstOrNull { it.code == code } ?: FAILED
    }
}

data class ArtworkPackStatus(
    val phase: ArtworkPackPhase,
    val totalArtworkCount: Int,
    val completedArtworkCount: Int,
    val failedArtworkCount: Int,
    val cachedBytes: Long,
    val message: String?,
)

