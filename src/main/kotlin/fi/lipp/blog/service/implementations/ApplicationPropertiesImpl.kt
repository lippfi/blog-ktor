package fi.lipp.blog.service.implementations

import fi.lipp.blog.data.StorageQuota
import fi.lipp.blog.service.ApplicationProperties
import io.ktor.server.application.*
import kotlin.io.path.Path

class ApplicationPropertiesImpl(private val environment: ApplicationEnvironment) : ApplicationProperties {
    override val resendAPIKey = environment.config.property("mail.api-key").getString()

    override fun avatarsDirectory(userLogin: String) = Path(environment.config.property("directory.avatars").getString().replace("{userLogin}", userLogin))
    override fun imagesDirectory(userLogin: String) = Path(environment.config.property("directory.images").getString().replace("{userLogin}", userLogin))
    override fun videosDirectory(userLogin: String) = Path(environment.config.property("directory.videos").getString().replace("{userLogin}", userLogin))
    override fun audiosDirectory(userLogin: String) = Path(environment.config.property("directory.audios").getString().replace("{userLogin}", userLogin))
    override fun stylesDirectory(userLogin: String) = Path(environment.config.property("directory.styles").getString().replace("{userLogin}", userLogin))
    override fun otherDirectory(userLogin: String) = Path(environment.config.property("directory.other").getString().replace("{userLogin}", userLogin))
    override fun reactionsDirectory(userLogin: String) = Path(environment.config.property("directory.reactions").getString().replace("{userLogin}", userLogin))

    override fun avatarsUrl(userLogin: String) = environment.config.property("url.avatars").getString().replace("{userLogin}", userLogin)
    override fun imagesUrl(userLogin: String) = environment.config.property("url.images").getString().replace("{userLogin}", userLogin)
    override fun videosUrl(userLogin: String) = environment.config.property("url.videos").getString().replace("{userLogin}", userLogin)
    override fun audiosUrl(userLogin: String) = environment.config.property("url.audios").getString().replace("{userLogin}", userLogin)
    override fun stylesUrl(userLogin: String) = environment.config.property("url.styles").getString().replace("{userLogin}", userLogin)
    override fun otherUrl(userLogin: String) = environment.config.property("url.other").getString().replace("{userLogin}", userLogin)
    override fun reactionsUrl(userLogin: String) = environment.config.property("url.reactions").getString().replace("{userLogin}", userLogin)

    override fun getQuotaLimit(quota: StorageQuota): Long? = when (quota) {
        StorageQuota.BASIC -> 1024 * 1024 * environment.config.property("storage.quota.basic").getString().toLong()
        StorageQuota.STANDARD -> 1024 * 1024 * environment.config.property("storage.quota.standard").getString().toLong()
        StorageQuota.MAX -> 1024 * 1024 * environment.config.property("storage.quota.max").getString().toLong()
        StorageQuota.UNLIMITED -> null
    }
}
