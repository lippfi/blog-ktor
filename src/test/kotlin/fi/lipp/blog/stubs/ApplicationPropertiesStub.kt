package fi.lipp.blog.stubs

import fi.lipp.blog.data.StorageQuota
import fi.lipp.blog.service.ApplicationProperties
import java.nio.file.Path
import kotlin.io.path.Path

class ApplicationPropertiesStub : ApplicationProperties {
    val basePath = Path("src", "test", "output")

    override val resendAPIKey: String = ""

    override fun avatarsDirectory(userLogin: String): Path = basePath.resolve("avatars")
    override fun imagesDirectory(userLogin: String): Path = basePath.resolve("images")
    override fun videosDirectory(userLogin: String): Path = basePath.resolve("videos")
    override fun audiosDirectory(userLogin: String): Path = basePath.resolve("audios")
    override fun stylesDirectory(userLogin: String): Path = basePath.resolve("styles")
    override fun otherDirectory(userLogin: String): Path = basePath.resolve("other")
    override fun reactionsDirectory(userLogin: String): Path = basePath.resolve("reactions")

    val baseUrl = "https://blog.com"

    override fun avatarsUrl(userLogin: String) = "$baseUrl/$userLogin/avatars"
    override fun imagesUrl(userLogin: String) = "$baseUrl/$userLogin/images"
    override fun videosUrl(userLogin: String) = "$baseUrl/$userLogin/videos"
    override fun audiosUrl(userLogin: String) = "$baseUrl/$userLogin/audios"
    override fun stylesUrl(userLogin: String) = "$baseUrl/$userLogin/styles"
    override fun otherUrl(userLogin: String) = "$baseUrl/$userLogin/other"
    override fun reactionsUrl(userLogin: String) = "$baseUrl/$userLogin/reactions"

    override fun getQuotaLimit(quota: StorageQuota): Long? = when (quota) {
        StorageQuota.BASIC -> 10L * 1024 * 1024     // 10MB
        StorageQuota.STANDARD -> 32L * 1024 * 1024  // 32MB
        StorageQuota.MAX -> 64L * 1024 * 1024       // 64MB
        StorageQuota.UNLIMITED -> null
    }
}
