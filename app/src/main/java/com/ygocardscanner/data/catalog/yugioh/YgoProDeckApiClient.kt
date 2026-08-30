package com.ygocardscanner.data.catalog.yugioh
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

enum class YgoProDeckLanguage(val apiLanguageCode: String?) {
    ENGLISH(null),
    GERMAN("de"),
}

/**
 * Testable boundary around YGOPRODeck's public v7 endpoints. It returns network DTOs rather than
 * Room records, so a failed or evolving upstream response cannot mutate the local collection.
 */
interface YgoProDeckApiClient {
    suspend fun fetchCards(
        language: YgoProDeckLanguage,
        pageSize: Int,
        offset: Int,
    ): YgoProDeckCardPageDto

    suspend fun fetchDatabaseVersion(): YgoProDeckDatabaseVersionDto
}

/**
 * Minimal standard-library HTTP implementation. It deliberately requests no images, prices, or
 * `tcgplayer_data`; the latter is not needed for the inventory catalog and would tempt the app to
 * infer print editions that the ordinary card response does not provide.
 */
class HttpYgoProDeckApiClient(
    private val baseUrl: String = DEFAULT_BASE_URL,
    private val connectTimeoutMillis: Int = DEFAULT_CONNECT_TIMEOUT_MILLIS,
    private val readTimeoutMillis: Int = DEFAULT_READ_TIMEOUT_MILLIS,
    private val json: Json = Json {
        ignoreUnknownKeys = true
    },
) : YgoProDeckApiClient {
    override suspend fun fetchCards(
        language: YgoProDeckLanguage,
        pageSize: Int,
        offset: Int,
    ): YgoProDeckCardPageDto {
        require(pageSize > 0) { "Page size must be positive." }
        require(offset >= 0) { "Page offset cannot be negative." }
        val query = buildMap {
            put("num", pageSize.toString())
            put("offset", offset.toString())
            language.apiLanguageCode?.let { put("language", it) }
        }
        return decode("cardinfo.php", query)
    }

    override suspend fun fetchDatabaseVersion(): YgoProDeckDatabaseVersionDto =
        withContext(Dispatchers.IO) {
            // This endpoint returns a one-element JSON array, unlike the paginated card endpoint.
            // Keeping that shape at the network boundary prevents it from leaking into Room code.
            json.decodeFromString<List<YgoProDeckDatabaseVersionDto>>(request("checkDBVer.php"))
                .singleOrNull()
                ?: error("YGOPRODeck did not return exactly one database version record.")
        }

    private suspend inline fun <reified T> decode(
        path: String,
        query: Map<String, String> = emptyMap(),
    ): T = withContext(Dispatchers.IO) {
        json.decodeFromString(request(path, query))
    }

    private fun request(path: String, query: Map<String, String> = emptyMap()): String {
        val encodedQuery = query.entries.joinToString("&") { (key, value) ->
            "${encode(key)}=${encode(value)}"
        }
        val separator = if (baseUrl.endsWith('/')) "" else "/"
        val url = URL("$baseUrl$separator$path${if (encodedQuery.isEmpty()) "" else "?$encodedQuery"}")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = connectTimeoutMillis
            readTimeout = readTimeoutMillis
            setRequestProperty("Accept", "application/json")
        }

        try {
            val responseCode = connection.responseCode
            val body = (if (responseCode in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream
            })?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (responseCode !in 200..299) {
                throw IOException("YGOPRODeck request to $path failed with HTTP $responseCode: $body")
            }
            return body
        } finally {
            connection.disconnect()
        }
    }

    private fun encode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.name())

    companion object {
        const val DEFAULT_BASE_URL = "https://db.ygoprodeck.com/api/v7"
        const val DEFAULT_CONNECT_TIMEOUT_MILLIS = 20_000
        const val DEFAULT_READ_TIMEOUT_MILLIS = 30_000
    }
}
