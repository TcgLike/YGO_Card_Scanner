package com.ygocardscanner.data.catalog

import com.ygocardscanner.data.catalog.network.CatalogCardArtworkDto
import com.ygocardscanner.data.catalog.network.CatalogCardDto
import com.ygocardscanner.data.catalog.network.CatalogPriceDto
import com.ygocardscanner.data.catalog.network.CatalogCardTextDto
import com.ygocardscanner.data.catalog.network.CatalogPayload
import com.ygocardscanner.data.catalog.network.CatalogPrintingDto
import com.ygocardscanner.data.catalog.network.YgoProDeckApiClient
import com.ygocardscanner.data.catalog.network.YgoProDeckCardDto
import com.ygocardscanner.data.catalog.network.YgoProDeckCardImageDto
import com.ygocardscanner.data.catalog.network.YgoProDeckCardPricesDto
import com.ygocardscanner.data.catalog.network.YgoProDeckCardSetDto
import com.ygocardscanner.data.catalog.network.YgoProDeckDatabaseVersionDto
import com.ygocardscanner.data.catalog.network.YgoProDeckLanguage
import java.io.IOException
import java.net.URI
import kotlinx.coroutines.delay
import kotlinx.serialization.json.contentOrNull

/**
 * Public YGOPRODeck v7 source for constrained catalog updates.
 *
 * English page data supplies canonical names and printing rows. German pages are used only for
 * localized card text, matched to English with a zero-padded passcode. In particular, German
 * `card_sets` are ignored: the API does not provide enough reliable data here to manufacture
 * German printings or edition values. Every imported English printing therefore has the explicit
 * `unknown` edition instead of an inferred one.
 */
class YgoProDeckCatalogSource(
    private val apiClient: YgoProDeckApiClient,
    private val pageSize: Int = DEFAULT_PAGE_SIZE,
    private val pageRequestDelayMillis: Long = DEFAULT_PAGE_REQUEST_DELAY_MILLIS,
) : CatalogSource, VersionedCatalogSource {
    override val sourceId: String = SOURCE_ID
    override val supersededSourceIds: Set<String> = setOf(DevelopmentCatalogSource.SOURCE_ID)

    init {
        require(pageSize > 0) { "Page size must be positive." }
        require(pageRequestDelayMillis >= 0) { "Page request delay cannot be negative." }
    }

    override suspend fun fetchRevision(): CatalogRevision =
        apiClient.fetchDatabaseVersion().toCatalogRevision()

    override suspend fun loadCatalog(): CatalogPayload {
        val startedRevision = fetchRevision()
        val englishCards = fetchAllPages(YgoProDeckLanguage.ENGLISH)
        val germanCards = fetchAllPages(YgoProDeckLanguage.GERMAN)
        val completedRevision = fetchRevision()
        if (completedRevision.revision != startedRevision.revision) {
            throw IOException("YGOPRODeck catalog changed while pages were being downloaded.")
        }
        return CatalogPayload(
            sourceId = sourceId,
            catalogRevision = startedRevision.revision,
            contentHash = startedRevision.contentHash,
            cards = mergeByPasscode(englishCards, germanCards),
        )
    }

    private suspend fun fetchAllPages(language: YgoProDeckLanguage): List<YgoProDeckCardDto> {
        val cards = mutableListOf<YgoProDeckCardDto>()
        val visitedOffsets = mutableSetOf<Int>()
        var offset = 0

        while (true) {
            check(visitedOffsets.add(offset)) {
                "YGOPRODeck pagination repeated offset $offset for ${language.name}."
            }
            val page = apiClient.fetchCards(language, pageSize, offset)
            cards += page.data

            val nextOffset = page.meta?.nextPageOffset ?: break
            check(nextOffset > offset) {
                "YGOPRODeck returned non-advancing offset $nextOffset for ${language.name}."
            }
            if (pageRequestDelayMillis > 0) {
                // The documented public API limit is 20 requests/second. This keeps sequential
                // page fetches below it even if cache hits make individual responses very quick.
                delay(pageRequestDelayMillis)
            }
            offset = nextOffset
        }
        return cards
    }

    private fun mergeByPasscode(
        englishCards: List<YgoProDeckCardDto>,
        germanCards: List<YgoProDeckCardDto>,
    ): List<CatalogCardDto> {
        val englishByPasscode = englishCards.groupByPasscode()
        val germanByPasscode = germanCards.groupByPasscode()

        return (englishByPasscode.keys + germanByPasscode.keys)
            .sorted()
            .mapNotNull { passcode ->
                val english = englishByPasscode[passcode]?.mergeRecords()
                val german = germanByPasscode[passcode]?.mergeRecords()
                val canonicalName = english?.name ?: german?.name ?: return@mapNotNull null
                val texts = buildList {
                    english?.toLocalizedText(ENGLISH_LANGUAGE_CODE)?.let(::add)
                    german?.toLocalizedText(GERMAN_LANGUAGE_CODE)?.let(::add)
                }
                if (texts.isEmpty()) return@mapNotNull null

                CatalogCardDto(
                    providerCardId = passcode,
                    passcode = passcode,
                    canonicalName = canonicalName,
                    texts = texts,
                    printings = english?.cardSets.orEmpty().toEnglishPrintings(passcode),
                    prices = english?.cardPrices.orEmpty().toCardPrices(),
                    artwork = english?.cardImages.orEmpty().toEnglishArtwork(passcode),
                )
            }
    }

    private fun List<YgoProDeckCardDto>.groupByPasscode(): Map<String, List<YgoProDeckCardDto>> =
        asSequence()
            .mapNotNull { card -> card.paddedPasscode()?.let { it to card } }
            .groupBy({ it.first }, { it.second })

    private fun YgoProDeckCardDto.paddedPasscode(): String? =
        id.takeIf { it >= 0 }?.toString()?.padStart(PASSCODE_LENGTH, '0')

    private fun List<YgoProDeckCardDto>.mergeRecords(): MergedRemoteCard? {
        val namedRecord = firstOrNull { it.name.isNotBlank() } ?: return null
        return MergedRemoteCard(
            name = namedRecord.name.trim(),
            description = firstNotNullOfOrNull { it.desc?.trim()?.takeIf(String::isNotEmpty) },
            cardSets = flatMap { it.cardSets.orEmpty() },
            cardPrices = flatMap { it.cardPrices.orEmpty() },
            cardImages = flatMap { it.cardImages.orEmpty() },
        )
    }

    private fun MergedRemoteCard.toLocalizedText(languageCode: String): CatalogCardTextDto =
        CatalogCardTextDto(
            languageCode = languageCode,
            name = name,
            description = description,
        )

    private fun List<YgoProDeckCardSetDto>.toEnglishPrintings(
        passcode: String,
    ): List<CatalogPrintingDto> =
        mapNotNull { set ->
            val setCode = set.setCode?.trim()?.takeIf(String::isNotEmpty) ?: return@mapNotNull null
            val rarity = set.setRarity?.trim()?.takeIf(String::isNotEmpty)
            val rarityCode = set.setRarityCode?.trim()?.takeIf(String::isNotEmpty)
            CatalogPrintingDto(
                // This deterministic ID comes entirely from the EN response.
                providerPrintingId = "$passcode|$setCode|${rarityCode.orEmpty()}|${rarity.orEmpty()}",
                setCode = setCode,
                setName = set.setName?.trim()?.takeIf(String::isNotEmpty),
                languageCode = ENGLISH_LANGUAGE_CODE,
                rarityCode = rarity,
                editionCode = UNKNOWN_EDITION_CODE,
                setPriceUsd = set.setPrice?.trim()?.takeIf(String::isNotEmpty),
            )
        }.distinctBy { it.providerPrintingId }

    private fun List<YgoProDeckCardPricesDto>.toCardPrices(): List<CatalogPriceDto> =
        asSequence()
            .flatMap { prices ->
                sequenceOf(
                    CARDMARKET to prices.cardmarketPrice,
                    COOL_STUFF_INC to prices.coolstuffincPrice,
                    TCGPLAYER to prices.tcgplayerPrice,
                    EBAY to prices.ebayPrice,
                    AMAZON to prices.amazonPrice,
                )
            }
            .mapNotNull { (providerId, amount) ->
                amount?.trim()?.takeIf(String::isNotEmpty)?.let {
                    CatalogPriceDto(
                        providerId = providerId,
                        currencyCode = if (providerId == CARDMARKET) EUR else USD,
                        amount = it,
                    )
                }
            }
            .distinctBy(CatalogPriceDto::providerId)
            .toList()

    /** The first artwork in the English response is the provider default artwork. */
    private fun List<YgoProDeckCardImageDto>.toEnglishArtwork(
        passcode: String,
    ): CatalogCardArtworkDto? = asSequence()
        .mapNotNull { image ->
            val url = image.imageUrl?.trim()?.takeIf(::isSupportedProviderArtworkUrl)
                ?: return@mapNotNull null
            CatalogCardArtworkDto(
                providerArtworkId = "$passcode:${image.id}",
                imageUrl = url,
            )
        }
        .firstOrNull()

    private fun isSupportedProviderArtworkUrl(value: String): Boolean = try {
        val uri = URI(value)
        uri.scheme.equals("https", ignoreCase = true) && uri.host == PROVIDER_IMAGE_HOST
    } catch (_: Exception) {
        false
    }
    private fun YgoProDeckDatabaseVersionDto.toCatalogRevision(): CatalogRevision {
        val version = databaseVersion?.contentOrNull?.trim()?.takeIf(String::isNotEmpty)
        val timestamp = date?.trim()?.takeIf(String::isNotEmpty)
            ?: lastUpdate?.trim()?.takeIf(String::isNotEmpty)
        val revision = listOfNotNull(version, timestamp).joinToString(separator = "|")
        require(revision.isNotEmpty()) { "YGOPRODeck did not return a usable database revision." }
        return CatalogRevision(
            sourceId = sourceId,
            revision = revision,
            contentHash = null,
        )
    }

    private data class MergedRemoteCard(
        val name: String,
        val description: String?,
        val cardSets: List<YgoProDeckCardSetDto>,
        val cardPrices: List<YgoProDeckCardPricesDto>,
        val cardImages: List<YgoProDeckCardImageDto>,
    )

    private companion object {
        const val SOURCE_ID = "ygoprodeck-v7"
        const val ENGLISH_LANGUAGE_CODE = "en"
        const val GERMAN_LANGUAGE_CODE = "de"
        const val UNKNOWN_EDITION_CODE = "unknown"
        const val PROVIDER_IMAGE_HOST = "images.ygoprodeck.com"
        const val PASSCODE_LENGTH = 8
        const val USD = "USD"
        const val EUR = "EUR"
        const val CARDMARKET = "cardmarket"
        const val COOL_STUFF_INC = "coolstuffinc"
        const val TCGPLAYER = "tcgplayer"
        const val EBAY = "ebay"
        const val AMAZON = "amazon"
        const val DEFAULT_PAGE_SIZE = 1000
        const val DEFAULT_PAGE_REQUEST_DELAY_MILLIS = 75L
    }
}
