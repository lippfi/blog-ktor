package fi.lipp.blog.stubs

import fi.lipp.blog.service.ApplicationProperties
import java.nio.file.Path
import kotlin.io.path.Path

class ApplicationPropertiesStub : ApplicationProperties {
    val basePath = Path("src", "test", "output")
    val baseUrl = "https://blog.com"

    override val resendAPIKey: String = ""
    override val databaseUrl: String = "jdbc:h2:mem:test;DB_CLOSE_DELAY=-1"
    override val databaseUser: String = "sa"
    override val databasePassword: String = ""
    override val databaseDriver: String = "org.h2.Driver"

    override val useCdn: Boolean = false
    override val cdnBaseUrl: String = ""
    override val cdnApiKey: String = ""

    override fun storageBaseDir(): Path = basePath
    override fun filesBaseUrl(): String = baseUrl

    override val maxImageSize: Int = 10 * 1024 * 1024
    override val maxVideoSize: Int = 100 * 1024 * 1024
    override val maxAudioSize: Int = 20 * 1024 * 1024
    override val maxStyleSize: Int = 256 * 1024
    override val maxOtherSize: Int = 5 * 1024 * 1024
    override val maxAvatarSize: Int = 1 * 1024 * 1024
    override val maxReactionSize: Int = 512 * 1024
}
