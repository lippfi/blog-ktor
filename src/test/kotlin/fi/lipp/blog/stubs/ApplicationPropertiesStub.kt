package fi.lipp.blog.stubs

import fi.lipp.blog.service.ApplicationProperties
import java.nio.file.Path
import kotlin.io.path.Path

class ApplicationPropertiesStub : ApplicationProperties {
    val basePath = Path("src", "test", "output")
    val baseUrl = "https://blog.com"

    override val resendAPIKey: String = ""
    override fun storageBaseDir(): Path = basePath
    override fun filesBaseUrl(): String = baseUrl

}
