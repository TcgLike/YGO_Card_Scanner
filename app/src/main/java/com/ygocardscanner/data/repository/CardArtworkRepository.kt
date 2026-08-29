package com.ygocardscanner.data.repository

/** Coordinates local-only artwork cache state; worker code is the only network caller. */
interface CardArtworkRepository {
    /** Returns false when no catalog artwork exists or a matching local file is already available. */
    suspend fun queueDownload(cardId: String): Boolean

    /** Downloads one English catalog artwork into app-private storage. */
    suspend fun downloadArtwork(cardId: String)

    suspend fun markRetry(cardId: String)

    suspend fun markFailed(cardId: String)
}
