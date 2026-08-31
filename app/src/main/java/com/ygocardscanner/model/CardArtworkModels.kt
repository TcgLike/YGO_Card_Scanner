package com.ygocardscanner.model

enum class CardArtworkDownloadState(val code: String) {
    NOT_DOWNLOADED("not_downloaded"),
    QUEUED("queued"),
    DOWNLOADING("downloading"),
    AVAILABLE("available"),
    FAILED("failed"),
    ;

    companion object {
        fun fromCode(code: String?): CardArtworkDownloadState =
            entries.firstOrNull { it.code == code } ?: NOT_DOWNLOADED
    }
}

/** A Room-backed reference to an English image cached in app-private storage. */
data class CardArtworkDetail(
    val localFileName: String?,
    val downloadState: CardArtworkDownloadState,
    val message: String? = null,
)

