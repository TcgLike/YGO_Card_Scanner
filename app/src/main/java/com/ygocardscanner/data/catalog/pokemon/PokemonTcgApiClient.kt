package com.ygocardscanner.data.catalog.pokemon
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

interface PokemonTcgApiClient {
    suspend fun fetchCards(page: Int, pageSize: Int): PokemonTcgCardPageDto
}

/** Public, English-only Pokémon TCG API v2 client. No request is made from the UI layer. */
class HttpPokemonTcgApiClient(
    private val baseUrl: String = DEFAULT_BASE_URL,
    private val connectTimeoutMillis: Int = DEFAULT_CONNECT_TIMEOUT_MILLIS,
    private val readTimeoutMillis: Int = DEFAULT_READ_TIMEOUT_MILLIS,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : PokemonTcgApiClient {
    override suspend fun fetchCards(page: Int, pageSize: Int): PokemonTcgCardPageDto {
        require(page > 0) { "Page must be positive." }
        require(pageSize in 1..MAX_PAGE_SIZE) { "Page size must be between 1 and $MAX_PAGE_SIZE." }
        return withContext(Dispatchers.IO) {
            val url = URL("$baseUrl?page=$page&pageSize=$pageSize")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = connectTimeoutMillis
                readTimeout = readTimeoutMillis
                setRequestProperty("Accept", "application/json")
            }
            try {
                val status = connection.responseCode
                val body = (if (status in 200..299) connection.inputStream else connection.errorStream)
                    ?.bufferedReader()?.use { it.readText() }.orEmpty()
                if (status !in 200..299) throw IOException("Pokémon catalog request failed with HTTP $status.")
                json.decodeFromString(body)
            } finally {
                connection.disconnect()
            }
        }
    }

    companion object {
        const val DEFAULT_BASE_URL = "https://api.pokemontcg.io/v2/cards"
        const val MAX_PAGE_SIZE = 250
        private const val DEFAULT_CONNECT_TIMEOUT_MILLIS = 20_000
        private const val DEFAULT_READ_TIMEOUT_MILLIS = 30_000
    }
}
