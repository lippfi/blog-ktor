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
}
