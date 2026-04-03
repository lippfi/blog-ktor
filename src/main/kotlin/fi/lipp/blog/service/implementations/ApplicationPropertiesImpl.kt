package fi.lipp.blog.service.implementations

import fi.lipp.blog.service.ApplicationProperties
import io.ktor.server.application.*
import java.nio.file.Path
import kotlin.io.path.Path

class ApplicationPropertiesImpl(private val environment: ApplicationEnvironment) : ApplicationProperties {
    override val resendAPIKey = environment.config.property("mail.api_key").getString()
    override val databaseUrl = environment.config.propertyOrNull("database.url")?.getString() ?: "jdbc:h2:file:./data/blog;DB_CLOSE_DELAY=-1"
    override val databaseUser = environment.config.propertyOrNull("database.user")?.getString() ?: "root"
    override val databasePassword = environment.config.propertyOrNull("database.password")?.getString() ?: ""
    override val databaseDriver = environment.config.propertyOrNull("database.driver")?.getString() ?: "org.h2.Driver"

    override val useCdn = environment.config.propertyOrNull("storage.use_cdn")?.getString()?.toBoolean() ?: false
    override val cdnBaseUrl = environment.config.propertyOrNull("storage.cdn_base_url")?.getString()?.removeSuffix("/") ?: ""
    override val cdnApiKey = environment.config.propertyOrNull("storage.cdn_api_key")?.getString() ?: ""

    override fun storageBaseDir(): Path = Path(environment.config.property("storage.base_dir").getString())
    override fun filesBaseUrl(): String = environment.config.property("storage.base_url").getString().removeSuffix("/")

    override val maxImageSize = environment.config.propertyOrNull("storage.max_file_sizes.image")?.getString()?.toInt() ?: (10 * 1024 * 1024)
    override val maxVideoSize = environment.config.propertyOrNull("storage.max_file_sizes.video")?.getString()?.toInt() ?: (100 * 1024 * 1024)
    override val maxAudioSize = environment.config.propertyOrNull("storage.max_file_sizes.audio")?.getString()?.toInt() ?: (20 * 1024 * 1024)
    override val maxStyleSize = environment.config.propertyOrNull("storage.max_file_sizes.style")?.getString()?.toInt() ?: (256 * 1024)
    override val maxOtherSize = environment.config.propertyOrNull("storage.max_file_sizes.other")?.getString()?.toInt() ?: (5 * 1024 * 1024)
    override val maxAvatarSize = environment.config.propertyOrNull("storage.max_file_sizes.avatar")?.getString()?.toInt() ?: (1 * 1024 * 1024)
    override val maxReactionSize = environment.config.propertyOrNull("storage.max_file_sizes.reaction")?.getString()?.toInt() ?: (512 * 1024)
    override val bcryptCost = environment.config.propertyOrNull("security.bcrypt_cost")?.getString()?.toInt() ?: 15
    override val requireInviteCode = environment.config.propertyOrNull("registration.require_invite_code")?.getString()?.toBoolean() ?: true
    override val adminLogin = environment.config.propertyOrNull("admin.login")?.getString() ?: ""
}
