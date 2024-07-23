package fi.lipp.blog.stubs

import fi.lipp.blog.service.ApplicationProperties
import java.nio.file.Path
import kotlin.io.path.Path

class ApplicationPropertiesStub : ApplicationProperties {
    val basePath = Path("src", "test", "output")

    override val emailHost = "emailHost"
    override val emailPort = "emailPort"
    override val emailAddress = "emailAddress"
    override val emailPassword = "emailPassword"

    override val imagesDirectory: Path = basePath.resolve("images")
    override val videosDirectory: Path = basePath.resolve("videos")
    override val audiosDirectory: Path = basePath.resolve("audios")
    override val stylesDirectory: Path = basePath.resolve("styles")
    override val otherDirectory: Path = basePath.resolve("other")

    val baseUrl = "https://blog.com"

    override val imagesUrl = "$baseUrl/images"
    override val videosUrl = "$baseUrl/videos"
    override val audiosUrl = "$baseUrl/audios"
    override val stylesUrl = "$baseUrl/styles"
    override val otherUrl = "$baseUrl/other"
}