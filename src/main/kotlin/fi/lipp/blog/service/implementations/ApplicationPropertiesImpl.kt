package fi.lipp.blog.service.implementations

import fi.lipp.blog.service.ApplicationProperties
import io.ktor.server.application.*
import java.nio.file.Path
import kotlin.io.path.Path

class ApplicationPropertiesImpl(private val environment: ApplicationEnvironment) : ApplicationProperties {
    override val resendAPIKey = environment.config.property("mail.api_key").getString()

    override fun storageBaseDir(): Path = Path(environment.config.property("storage.base_dir").getString())
    override fun filesBaseUrl(): String = environment.config.property("storage.base_url").getString().removeSuffix("/")
}
