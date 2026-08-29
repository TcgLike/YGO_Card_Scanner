package com.ygocardscanner.data.catalog.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive

/** Transport models for the documented YGOPRODeck v7 public API only. */
@Serializable
data class YgoProDeckCardPageDto(
    val data: List<YgoProDeckCardDto> = emptyList(),
    val meta: YgoProDeckPageMetaDto? = null,
)

@Serializable
data class YgoProDeckPageMetaDto(
    @SerialName("next_page_offset")
    val nextPageOffset: Int? = null,
)

@Serializable
data class YgoProDeckCardDto(
    /** Numeric card passcode; source mapping left-pads it to the conventional eight digits. */
    val id: Long,
    val name: String,
    val desc: String? = null,
    @SerialName("card_sets")
    val cardSets: List<YgoProDeckCardSetDto>? = null,
    @SerialName("card_images")
    val cardImages: List<YgoProDeckCardImageDto>? = null,
    @SerialName("card_prices")
    val cardPrices: List<YgoProDeckCardPricesDto>? = null,
)

/** Only the English primary artwork is used; localized card pages are never queried for images. */
@Serializable
data class YgoProDeckCardImageDto(
    val id: Long,
    @SerialName("image_url")
    val imageUrl: String? = null,
)

@Serializable
data class YgoProDeckCardSetDto(
    @SerialName("set_name")
    val setName: String? = null,
    @SerialName("set_code")
    val setCode: String? = null,
    @SerialName("set_rarity")
    val setRarity: String? = null,
    @SerialName("set_rarity_code")
    val setRarityCode: String? = null,
    @SerialName("set_price")
    val setPrice: String? = null,
)

/** Provider-reported lowest card price across versions, not a valuation of a physical printing. */
@Serializable
data class YgoProDeckCardPricesDto(
    @SerialName("cardmarket_price")
    val cardmarketPrice: String? = null,
    @SerialName("coolstuffinc_price")
    val coolstuffincPrice: String? = null,
    @SerialName("tcgplayer_price")
    val tcgplayerPrice: String? = null,
    @SerialName("ebay_price")
    val ebayPrice: String? = null,
    @SerialName("amazon_price")
    val amazonPrice: String? = null,
)

@Serializable
data class YgoProDeckDatabaseVersionDto(
    @SerialName("database_version")
    val databaseVersion: JsonPrimitive? = null,
    val date: String? = null,
    @SerialName("last_update")
    val lastUpdate: String? = null,
)