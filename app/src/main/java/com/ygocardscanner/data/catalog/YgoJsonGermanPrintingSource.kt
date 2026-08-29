package com.ygocardscanner.data.catalog

import com.ygocardscanner.data.util.CatalogNormalizers
import java.io.BufferedInputStream
import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream

/**
 * Separate optional input for verified German physical printing rows.
 *
 * This deliberately is not a [CatalogSource]: it never owns cards, text, artwork, or inventory.
 * Its records are joined to the installed primary catalog by passcode by the enrichment repository.
 */
interface GermanPrintingSource {
    val sourceId: String

    suspend fun fetchRevision(): CatalogRevision

    suspend fun loadGermanPrintings(): GermanPrintingPayload
}

data class GermanPrintingPayload(
    val sourceId: String,
    val catalogRevision: String,
    val contentHash: String?,
    val printings: List<GermanPrintingRecord>,
)

data class GermanPrintingRecord(
    val providerPrintingId: String,
    val passcode: String,
    val setCode: String,
    val setName: String?,
    val rarityCode: String?,
    val editionCode: String,
)

/** Testable network boundary for the public YGOJSON aggregate release. */
interface YgoJsonApiClient {
    suspend fun fetchLatestRelease(): YgoJsonRelease

    suspend fun openAggregateArchive(): YgoJsonArchiveDownload
}

data class YgoJsonRelease(
    val assetUrl: String,
    val assetSizeBytes: Long,
    val updatedAt: String,
    val sha256: String?,
) {
    fun toRevision(sourceId: String): CatalogRevision {
        require(assetUrl.isNotBlank()) { "YGOJSON aggregate download URL is missing." }
        require(assetSizeBytes > 0) { "YGOJSON aggregate download has no usable size." }
        require(updatedAt.isNotBlank()) { "YGOJSON aggregate update timestamp is missing." }
        return CatalogRevision(
            sourceId = sourceId,
            revision = "$updatedAt|$assetSizeBytes",
            contentHash = sha256,
        )
    }
}

class YgoJsonArchiveDownload(
    val inputStream: InputStream,
    private val closeAction: () -> Unit,
) : Closeable {
    override fun close() {
        try {
            inputStream.close()
        } finally {
            closeAction()
        }
    }
}

/**
 * Public GitHub release client. The opt-in worker is the only caller; this client sends no user
 * data and does not download card artwork from the community source.
 */
class HttpYgoJsonApiClient(
    private val releaseApiUrl: String = DEFAULT_RELEASE_API_URL,
    private val connectTimeoutMillis: Int = DEFAULT_CONNECT_TIMEOUT_MILLIS,
    private val readTimeoutMillis: Int = DEFAULT_READ_TIMEOUT_MILLIS,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : YgoJsonApiClient {
    override suspend fun fetchLatestRelease(): YgoJsonRelease = withContext(Dispatchers.IO) {
        val response = requestText(releaseApiUrl)
        val release = json.decodeFromString<YgoJsonReleaseDto>(response)
        val aggregate = release.assets.firstOrNull { it.name == AGGREGATE_FILE_NAME }
            ?: error("YGOJSON did not publish $AGGREGATE_FILE_NAME.")
        YgoJsonRelease(
            assetUrl = aggregate.browser_download_url.orEmpty(),
            assetSizeBytes = aggregate.size ?: 0,
            updatedAt = aggregate.updated_at ?: release.updated_at.orEmpty(),
            sha256 = aggregate.digest?.removePrefix("sha256:"),
        )
    }

    override suspend fun openAggregateArchive(): YgoJsonArchiveDownload = withContext(Dispatchers.IO) {
        val release = fetchLatestRelease()
        val connection = (URL(release.assetUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = connectTimeoutMillis
            readTimeout = readTimeoutMillis
            instanceFollowRedirects = true
            setRequestProperty("Accept", "application/zip")
            setRequestProperty("User-Agent", USER_AGENT)
        }
        try {
            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                val body = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                throw IOException("YGOJSON archive request failed with HTTP $responseCode: $body")
            }
            YgoJsonArchiveDownload(connection.inputStream) { connection.disconnect() }
        } catch (error: Throwable) {
            connection.disconnect()
            throw error
        }
    }

    private fun requestText(url: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = connectTimeoutMillis
            readTimeout = readTimeoutMillis
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", USER_AGENT)
        }
        try {
            val responseCode = connection.responseCode
            val body = (if (responseCode in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()
                ?.use { it.readText() }
                .orEmpty()
            if (responseCode !in 200..299) {
                throw IOException("YGOJSON release request failed with HTTP $responseCode: $body")
            }
            return body
        } finally {
            connection.disconnect()
        }
    }

    @Serializable
    private data class YgoJsonReleaseDto(
        val assets: List<YgoJsonReleaseAssetDto> = emptyList(),
        val updated_at: String? = null,
    )

    @Serializable
    private data class YgoJsonReleaseAssetDto(
        val name: String? = null,
        val size: Long? = null,
        val updated_at: String? = null,
        val browser_download_url: String? = null,
        val digest: String? = null,
    )

    private companion object {
        const val DEFAULT_RELEASE_API_URL =
            "https://api.github.com/repos/iconmaster5326/YGOJSON/releases/latest"
        const val AGGREGATE_FILE_NAME = "aggregate.zip"
        const val USER_AGENT = "YgoCardScanner/0.1 (German-printing-enrichment)"
        const val DEFAULT_CONNECT_TIMEOUT_MILLIS = 20_000
        const val DEFAULT_READ_TIMEOUT_MILLIS = 60_000
    }
}

/**
 * Opt-in YGOJSON importer. The aggregate is ~35 MB compressed at the time of implementation.
 * Only its card passwords and German physical-printing metadata are parsed; names, effect text,
 * images, and every non-German locale are ignored.
 */
class YgoJsonGermanPrintingSource(
    private val apiClient: YgoJsonApiClient,
) : GermanPrintingSource {
    override val sourceId: String = SOURCE_ID

    override suspend fun fetchRevision(): CatalogRevision =
        apiClient.fetchLatestRelease().toRevision(sourceId)

    override suspend fun loadGermanPrintings(): GermanPrintingPayload {
        val startingRevision = fetchRevision()
        val printings = apiClient.openAggregateArchive().use { archive ->
            YgoJsonGermanPrintingParser.parse(archive.inputStream)
        }
        val completedRevision = fetchRevision()
        if (startingRevision.revision != completedRevision.revision ||
            startingRevision.contentHash != completedRevision.contentHash
        ) {
            throw IOException("YGOJSON changed while German printings were being downloaded.")
        }
        require(printings.isNotEmpty()) { "YGOJSON did not contain any usable German printings." }
        return GermanPrintingPayload(
            sourceId = sourceId,
            catalogRevision = startingRevision.revision,
            contentHash = startingRevision.contentHash,
            printings = printings,
        )
    }

    companion object {
        const val SOURCE_ID = "ygojson-german-printings-v1"
    }
}

/** Streaming parser for the two aggregate files needed by the opt-in enrichment. */
internal object YgoJsonGermanPrintingParser {
    private val json = Json { ignoreUnknownKeys = true }
    @OptIn(ExperimentalSerializationApi::class)
    fun parse(inputStream: InputStream): List<GermanPrintingRecord> {
        var cardPasscodes: Map<String, String>? = null
        var sets: List<YgoJsonSetDto>? = null
        ZipInputStream(BufferedInputStream(inputStream)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                when (entry.name) {
                    CARDS_FILE_NAME -> {
                        val cards = json.decodeFromStream<List<YgoJsonCardDto>>(zip)
                        cardPasscodes = cards.mapNotNull { card ->
                            card.id?.trim()?.takeIf(String::isNotEmpty)?.let { id ->
                                card.passwords.firstOrNull()
                                    ?.trim()
                                    ?.takeIf { it.all(Char::isDigit) }
                                    ?.padStart(PASSCODE_LENGTH, '0')
                                    ?.let { id to it }
                            }
                        }.toMap()
                    }

                    SETS_FILE_NAME -> sets = json.decodeFromStream<List<YgoJsonSetDto>>(zip)
                }
                zip.closeEntry()
            }
        }
        val passcodes = requireNotNull(cardPasscodes) { "YGOJSON archive is missing $CARDS_FILE_NAME." }
        val parsedSets = requireNotNull(sets) { "YGOJSON archive is missing $SETS_FILE_NAME." }
        return parsedSets.flatMap { set -> set.toGermanPrintings(passcodes) }
            .distinctBy { it.providerPrintingId }
    }

    private fun YgoJsonSetDto.toGermanPrintings(cardPasscodes: Map<String, String>): List<GermanPrintingRecord> {
        val germanLocale = locales[GERMAN_LANGUAGE_CODE] ?: return emptyList()
        val prefix = germanLocale.prefix?.trim()?.takeIf(String::isNotEmpty) ?: return emptyList()
        val setId = id?.trim()?.takeIf(String::isNotEmpty) ?: return emptyList()
        val setName = names[GERMAN_LANGUAGE_CODE]?.trim()?.takeIf(String::isNotEmpty)
            ?: names[ENGLISH_LANGUAGE_CODE]?.trim()?.takeIf(String::isNotEmpty)

        return contents.asSequence()
            .filter { GERMAN_LANGUAGE_CODE in it.locales }
            .flatMap { content ->
                content.cards.asSequence().flatMap { printing ->
                    val printingId = printing.id?.trim()?.takeIf(String::isNotEmpty) ?: return@flatMap emptySequence()
                    val passcode = printing.card?.let(cardPasscodes::get) ?: return@flatMap emptySequence()
                    val suffix = printing.suffix?.trim()?.takeIf(String::isNotEmpty) ?: return@flatMap emptySequence()
                    val setCode = "$prefix$suffix"
                    if (CatalogNormalizers.setCode(setCode) == null) return@flatMap emptySequence()
                    val editions = content.editions.map(::toEditionCode).ifEmpty { listOf(UNKNOWN_EDITION_CODE) }
                    editions.asSequence().map { editionCode ->
                        GermanPrintingRecord(
                            providerPrintingId = "$setId|$printingId|$editionCode",
                            passcode = passcode,
                            setCode = setCode,
                            setName = setName,
                            rarityCode = printing.rarity?.toDisplayRarity(),
                            editionCode = editionCode,
                        )
                    }
                }
            }
            .toList()
    }

    private fun toEditionCode(value: String): String = when (value.trim().lowercase()) {
        "1st", "first", "first_edition" -> FIRST_EDITION_CODE
        "unlimited", "unlim" -> UNLIMITED_EDITION_CODE
        "limited" -> LIMITED_EDITION_CODE
        else -> UNKNOWN_EDITION_CODE
    }

    private fun String.toDisplayRarity(): String = when (trim().lowercase()) {
        "common" -> "Common"
        "rare" -> "Rare"
        "super" -> "Super Rare"
        "ultra" -> "Ultra Rare"
        "secret" -> "Secret Rare"
        "ultimate" -> "Ultimate Rare"
        "ghost" -> "Ghost Rare"
        "starfoil" -> "Starfoil Rare"
        "mosaic" -> "Mosaic Rare"
        "platinum" -> "Platinum Rare"
        else -> trim().takeIf(String::isNotEmpty).orEmpty()
    }.takeIf(String::isNotEmpty).orEmpty()

    @Serializable
    private data class YgoJsonCardDto(
        val id: String? = null,
        val passwords: List<String> = emptyList(),
    )

    @Serializable
    private data class YgoJsonSetDto(
        val id: String? = null,
        val name: Map<String, String> = emptyMap(),
        val locales: Map<String, YgoJsonLocaleDto> = emptyMap(),
        val contents: List<YgoJsonContentsDto> = emptyList(),
    ) {
        val names: Map<String, String>
            get() = name
    }

    @Serializable
    private data class YgoJsonLocaleDto(val prefix: String? = null)

    @Serializable
    private data class YgoJsonContentsDto(
        val locales: List<String> = emptyList(),
        val editions: List<String> = emptyList(),
        val cards: List<YgoJsonPrintingDto> = emptyList(),
    )

    @Serializable
    private data class YgoJsonPrintingDto(
        val id: String? = null,
        val card: String? = null,
        val suffix: String? = null,
        val rarity: String? = null,
    )

    private const val CARDS_FILE_NAME = "cards.json"
    private const val SETS_FILE_NAME = "sets.json"
    private const val ENGLISH_LANGUAGE_CODE = "en"
    private const val GERMAN_LANGUAGE_CODE = "de"
    private const val PASSCODE_LENGTH = 8
    private const val FIRST_EDITION_CODE = "first_edition"
    private const val UNLIMITED_EDITION_CODE = "unlimited"
    private const val LIMITED_EDITION_CODE = "limited"
    private const val UNKNOWN_EDITION_CODE = "unknown"
}
