package com.ygocardscanner.data.deckimport.yugioh

data class YgoDeckAvailabilityPreview(
    val sourceLabel: String,
    val totalCardCount: Int,
    val cards: List<YgoDeckAvailabilityCard>,
) {
    val canBuild: Boolean get() = cards.all(YgoDeckAvailabilityCard::hasEnough)
    val ownedCardCount: Int get() = cards.count(YgoDeckAvailabilityCard::hasEnough)
}

data class YgoDeckAvailabilityCard(
    val passcode: String,
    val displayName: String?,
    val mainQuantity: Int,
    val extraQuantity: Int,
    val sideQuantity: Int,
    val ownedQuantity: Int,
) {
    val requiredQuantity: Int get() = mainQuantity + extraQuantity + sideQuantity
    val isInCatalog: Boolean get() = displayName != null
    val hasEnough: Boolean get() = isInCatalog && ownedQuantity >= requiredQuantity
    val missingQuantity: Int get() = (requiredQuantity - ownedQuantity).coerceAtLeast(0)
}