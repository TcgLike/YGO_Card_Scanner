package com.ygocardscanner.data.artwork

import android.content.Context
import android.graphics.BitmapFactory
import android.os.StatFs
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Keeps public catalog artwork in app-private files. Remote URLs never reach Compose UI. */
class CardArtworkFileStore(
    context: Context,
    private val directoryName: String = DIRECTORY_NAME,
    private val providerImageHost: String = PROVIDER_IMAGE_HOST,
) {
    private val filesDirectory = context.applicationContext.filesDir
    private val directory = File(filesDirectory, directoryName)

    fun resolve(fileName: String?): File? {
        if (fileName.isNullOrBlank() || fileName.contains('/') || fileName.contains('\\')) return null
        return File(directory, fileName).takeIf(File::isFile)
    }

    fun cacheSizeBytes(): Long = directory.listFiles()?.sumOf { file ->
        if (file.isFile) file.length() else 0L
    } ?: 0L

    fun availableBytes(): Long = StatFs(filesDirectory.absolutePath).availableBytes

    suspend fun download(artworkId: String, remoteUrl: String): StoredArtwork = withContext(Dispatchers.IO) {
        requireProviderUrl(remoteUrl)
        if (!directory.exists() && !directory.mkdirs()) {
            throw IOException("The app-private artwork cache could not be created.")
        }

        val fileName = "${sha256(artworkId)}.img"
        val destination = File(directory, fileName)
        val temporary = File.createTempFile("artwork-", ".tmp", directory)
        try {
            downloadToTemporaryFile(remoteUrl, temporary)
            validateBitmap(temporary)
            val previousBytes = destination.takeIf(File::isFile)?.length() ?: 0L
            val resultingCacheBytes = cacheSizeBytes() - previousBytes + temporary.length()
            if (resultingCacheBytes > MAX_CACHE_BYTES) {
                throw ArtworkStorageQuotaExceededException()
            }
            if (destination.exists() && !destination.delete()) {
                throw IOException("The previous artwork file could not be replaced.")
            }
            if (!temporary.renameTo(destination)) {
                throw IOException("The artwork file could not be stored.")
            }
            StoredArtwork(fileName = fileName, cacheSizeBytes = resultingCacheBytes)
        } finally {
            temporary.delete()
        }
    }

    private fun downloadToTemporaryFile(remoteUrl: String, temporary: File) {
        val connection = (URI(remoteUrl).toURL().openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = CONNECT_TIMEOUT_MILLIS
            readTimeout = READ_TIMEOUT_MILLIS
            instanceFollowRedirects = false
            setRequestProperty("Accept", "image/*")
        }
        try {
            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                throw IOException("The public artwork source returned HTTP $responseCode.")
            }
            val declaredLength = connection.contentLengthLong
            if (declaredLength > MAX_IMAGE_BYTES) {
                throw IllegalArgumentException("The artwork file is larger than the cache limit.")
            }
            val contentType = connection.contentType?.lowercase().orEmpty()
            if (!contentType.startsWith("image/")) {
                throw IllegalArgumentException("The public artwork response was not an image.")
            }

            connection.inputStream.use { input ->
                FileOutputStream(temporary).use { output ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    var totalBytes = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        totalBytes += read
                        if (totalBytes > MAX_IMAGE_BYTES) {
                            throw IllegalArgumentException("The artwork file is larger than the cache limit.")
                        }
                        output.write(buffer, 0, read)
                    }
                }
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun validateBitmap(file: File) {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, options)
        require(options.outWidth in 1..MAX_DIMENSION && options.outHeight in 1..MAX_DIMENSION) {
            "The downloaded artwork is not a supported bitmap."
        }
    }

    private fun requireProviderUrl(remoteUrl: String) {
        val uri = try {
            URI(remoteUrl)
        } catch (_: Exception) {
            throw IllegalArgumentException("The catalog returned an invalid artwork URL.")
        }
        require(uri.scheme.equals("https", ignoreCase = true) && uri.host == providerImageHost) {
            "The catalog returned an unsupported artwork source."
        }
    }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

    data class StoredArtwork(
        val fileName: String,
        val cacheSizeBytes: Long,
    )

    class ArtworkStorageQuotaExceededException : IOException("The 4 GiB artwork cache limit was reached.")

    companion object {
        const val DIRECTORY_NAME = "card_artwork"
        const val PROVIDER_IMAGE_HOST = "images.ygoprodeck.com"
        const val MAX_CACHE_BYTES = 4L * 1024L * 1024L * 1024L
        const val MINIMUM_FREE_BYTES_FOR_FULL_PACK = 3_500L * 1024L * 1024L
        private const val CONNECT_TIMEOUT_MILLIS = 20_000
        private const val READ_TIMEOUT_MILLIS = 30_000
        private const val MAX_IMAGE_BYTES = 5L * 1024L * 1024L
        private const val MAX_DIMENSION = 4_096
        private const val BUFFER_SIZE = 8 * 1024
    }
}