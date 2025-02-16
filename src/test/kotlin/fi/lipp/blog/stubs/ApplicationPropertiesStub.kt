package fi.lipp.blog.stubs

import fi.lipp.blog.data.StorageQuota
import fi.lipp.blog.service.ApplicationProperties
import java.nio.file.Path
import kotlin.io.path.Path

class ApplicationPropertiesStub : ApplicationProperties {
    val basePath = Path("src", "test", "output")

    override val emailHost = "emailHost"
    override val emailPort = "emailPort"
    override val emailAddress = "emailAddress"
    override val emailPassword = "emailPassword"

    override fun avatarsDirectory(userLogin: String): Path = basePath.resolve("avatars")
    override fun imagesDirectory(userLogin: String): Path = basePath.resolve("images")
    override fun videosDirectory(userLogin: String): Path = basePath.resolve("videos")
    override fun audiosDirectory(userLogin: String): Path = basePath.resolve("audios")
    override fun stylesDirectory(userLogin: String): Path = basePath.resolve("styles")
    override fun otherDirectory(userLogin: String): Path = basePath.resolve("other")
    override fun reactionsDirectory(userLogin: String): Path = basePath.resolve("reactions")

    val baseUrl = "https://blog.com"

    override val avatarsUrl = "$baseUrl/avatars"
    override val imagesUrl = "$baseUrl/images"
    override val videosUrl = "$baseUrl/videos"
    override val audiosUrl = "$baseUrl/audios"
    override val stylesUrl = "$baseUrl/styles"
    override val otherUrl = "$baseUrl/other"
    override val reactionsUrl = "$baseUrl/reactions"

    override fun getQuotaLimit(quota: StorageQuota): Long? = when (quota) {
        StorageQuota.BASIC -> 10L * 1024 * 1024     // 10MB
        StorageQuota.STANDARD -> 32L * 1024 * 1024  // 32MB
        StorageQuota.MAX -> 64L * 1024 * 1024       // 64MB
        StorageQuota.UNLIMITED -> null
    }
}
