package com.ygocardscanner.data.catalog.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Transport-only catalog representation.
 *
 * These models deliberately do not reference Room entities. A future public catalog provider
 * can deserialize into the same shape and then use [com.ygocardscanner.data.catalog.CatalogMapper]
 * to create database rows.
 */
@Serializable
data class CatalogPayload(
    @SerialName("source_id")
    val sourceId: String,
    @SerialName("catalog_revision")
    val catalogRevision: String,
    @SerialName("content_hash")
    val contentHash: String? = null,
    val cards: List<CatalogCardDto>,
)

@Serializable
data class CatalogCardDto(
    @SerialName("provider_card_id")
    val providerCardId: String,
    val passcode: String? = null,
    @SerialName("canonical_name")
    val canonicalName: String,
    val texts: List<CatalogCardTextDto>,
    val printings: List<CatalogPrintingDto>,
    /** Optional English canonical artwork metadata. The URL is never exposed to the UI. */
    val artwork: CatalogCardArtworkDto? = null,
)

@Serializable
data class CatalogCardTextDto(
    @SerialName("language_code")
    val languageCode: String,
    val name: String,
    val description: String? = null,
)

@Serializable
data class CatalogPrintingDto(
    @SerialName("provider_printing_id")
    val providerPrintingId: String,
    @SerialName("set_code")
    val setCode: String,
    @SerialName("set_name")
    val setName: String? = null,
    @SerialName("language_code")
    val languageCode: String,
    @SerialName("rarity_code")
    val rarityCode: String? = null,
    @SerialName("edition_code")
    val editionCode: String,
)

@Serializable
data class CatalogCardArtworkDto(
    @SerialName("provider_artwork_id")
    val providerArtworkId: String,
    @SerialName("image_url")
    val imageUrl: String,
)