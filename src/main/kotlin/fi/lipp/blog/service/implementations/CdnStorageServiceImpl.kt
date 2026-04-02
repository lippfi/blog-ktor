package fi.lipp.blog.service.implementations

import fi.lipp.blog.data.BlogFile
import fi.lipp.blog.data.FileUploadData
import fi.lipp.blog.model.exceptions.InternalServerError
import fi.lipp.blog.service.ApplicationProperties
import java.io.InputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.security.MessageDigest
import java.util.*

class CdnStorageServiceImpl(properties: ApplicationProperties) : BaseStorageServiceImpl(properties) {
    private val httpClient = HttpClient.newHttpClient()

    override fun baseFileUrl(): String = properties.cdnBaseUrl

    override fun openFileStream(file: BlogFile): InputStream {
        val url = "${properties.cdnBaseUrl}/${file.storageKey}"

        val request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .GET()
            .build()

        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream())

        if (response.statusCode() != 200) {
            throw InternalServerError()
        }

        return response.body()
    }

    override fun persistFile(userId: UUID, fileId: UUID, storageKey: String, file: FileUploadData): PersistResult {
        uploadToCdn(storageKey, file.bytes, file.mimeType)

        val hash = sha256Hex(file.bytes)
        val physicalFileName = storageKey.substringAfterLast('/')
        val blogFile = BlogFile(fileId, userId, physicalFileName, file.type, storageKey)
        return PersistResult(blogFile, hash)
    }

    override fun cleanupFile(storageKey: String) {
        // CDN cleanup could be implemented if the CDN supports DELETE operations
    }

    private fun uploadToCdn(storageKey: String, bytes: ByteArray, mimeType: String) {
        val url = "${properties.cdnBaseUrl}/$storageKey"
        val request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Content-Type", mimeType)
            .header("Authorization", "Bearer ${properties.cdnApiKey}")
            .PUT(HttpRequest.BodyPublishers.ofByteArray(bytes))
            .build()
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() !in 200..299) {
            throw InternalServerError()
        }
    }

    private fun sha256Hex(bytes: ByteArray): String {
        val md = MessageDigest.getInstance("SHA-256")
        md.update(bytes)
        return md.digest().joinToString("") { "%02x".format(it) }
    }
}
