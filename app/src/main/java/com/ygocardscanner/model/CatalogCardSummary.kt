package com.ygocardscanner.model

/** One canonical, active public-catalog card with only local display metadata. */
data class CatalogCardSummary(
    val cardId: String,
    val displayName: String,
    val passcode: String?,
    val artwork: CardArtworkDetail?,
    /** True when any positive-quantity local inventory entry references this canonical card. */
    val isOwned: Boolean,
)

