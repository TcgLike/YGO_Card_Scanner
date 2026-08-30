package com.ygocardscanner.data.catalog.pokemon
import com.ygocardscanner.data.catalog.universal.CatalogCardArtworkDto
import com.ygocardscanner.data.catalog.universal.CatalogCardDto
import com.ygocardscanner.data.catalog.universal.CatalogCardTextDto
import com.ygocardscanner.data.catalog.universal.CatalogPayload
import com.ygocardscanner.data.catalog.universal.CatalogPrintingDto
import com.ygocardscanner.data.catalog.universal.CatalogSource
import java.io.IOException
import java.net.URI
import java.security.MessageDigest
import kotlinx.coroutines.delay

/** English-only Pokémon catalog. German records are deliberately not inferred from price metadata. */
class PokemonTcgCatalogSource(
    private val apiClient: PokemonTcgApiClient,
    private val pageSize: Int = DEFAULT_PAGE_SIZE,
    private val pageRequestDelayMillis: Long = DEFAULT_PAGE_REQUEST_DELAY_MILLIS,
    private val now: () -> Long = System::currentTimeMillis,
) : CatalogSource {
    override val sourceId: String = SOURCE_ID

    override suspend fun loadCatalog(): CatalogPayload {
        val cards = mutableListOf<PokemonTcgCardDto>()
        var page = 1
        var totalCount: Int? = null
        do {
            val response = apiClient.fetchCards(page, pageSize)
            require(response.page == page) { "Pokémon catalog returned an unexpected page." }
            require(response.pageSize in 1..pageSize) { "Pokémon catalog returned an invalid page size." }
            totalCount = totalCount ?: response.totalCount
            require(response.totalCount == totalCount) { "Pokémon catalog changed during pagination." }
            cards += response.data
            if (cards.size < totalCount && response.data.isEmpty()) {
                throw IOException("Pokémon catalog pagination ended before all cards were received.")
            }
            page += 1
            if (cards.size < totalCount && pageRequestDelayMillis > 0) delay(pageRequestDelayMillis)
        } while (cards.size < requireNotNull(totalCount))

        val mapped = cards.distinctBy(PokemonTcgCardDto::id).map(::toCatalogCard)
        require(mapped.isNotEmpty()) { "Pokémon catalog did not contain any usable cards." }
        return CatalogPayload(
            sourceId = sourceId,
            catalogRevision = "download-${now()}",
            contentHash = mapped.joinToString("|") { it.providerCardId }.sha256(),
            cards = mapped,
        )
    }

    private fun toCatalogCard(card: PokemonTcgCardDto): CatalogCardDto {
        val providerId = card.id.trim().requireNotEmpty("Pokémon card ID")
        val setId = card.set.id.trim().requireNotEmpty("Pokémon set ID")
        val number = card.number.trim().requireNotEmpty("Pokémon card number")
        val setCode = "$setId-$number"
        return CatalogCardDto(
            providerCardId = providerId,
            canonicalName = card.name.trim().requireNotEmpty("Pokémon card name"),
            texts = listOf(CatalogCardTextDto("en", card.name.trim())),
            printings = listOf(
                CatalogPrintingDto(
                    providerPrintingId = providerId,
                    setCode = setCode,
                    setName = card.set.name?.trim()?.takeIf(String::isNotEmpty),
                    languageCode = "en",
                    rarityCode = card.rarity?.trim()?.takeIf(String::isNotEmpty),
                    editionCode = "unknown",
                ),
            ),
            artwork = sequenceOf(card.images?.large, card.images?.small)
                .mapNotNull { imageUrl ->
                    imageUrl?.trim()?.takeIf(::isPokemonImageUrl)?.let {
                        CatalogCardArtworkDto(providerArtworkId = providerId, imageUrl = it)
                    }
                }
                .firstOrNull(),
        )
    }

    private fun String.requireNotEmpty(label: String): String = takeIf(String::isNotEmpty)
        ?: throw IllegalArgumentException("$label is missing.")

    private fun isPokemonImageUrl(value: String): Boolean = try {
        val uri = URI(value)
        uri.scheme.equals("https", true) && uri.host == IMAGE_HOST
    } catch (_: Exception) {
        false
    }

    private fun String.sha256(): String = MessageDigest.getInstance("SHA-256")
        .digest(toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

    private companion object {
        const val SOURCE_ID = "pokemon-tcg-v2"
        const val IMAGE_HOST = "images.pokemontcg.io"
        const val DEFAULT_PAGE_SIZE = 250
        // The public unauthenticated limit is 30 requests/minute; this stays comfortably below it.
        const val DEFAULT_PAGE_REQUEST_DELAY_MILLIS = 2_100L
    }
}
