package fi.lipp.blog.service.implementations

import fi.lipp.blog.service.ApplicationProperties
import io.ktor.server.application.*
import kotlin.io.path.Path

class ApplicationPropertiesImpl(environment: ApplicationEnvironment) : ApplicationProperties {
    override val emailHost = environment.config.property("mail.host").getString()
    override val emailPort = environment.config.property("mail.port").getString()
    override val emailAddress = environment.config.property("mail.email").getString()
    override val emailPassword = environment.config.property("mail.password").getString()

    override val imagesDirectory = Path(environment.config.property("directory.images").getString())
    override val videosDirectory = Path(environment.config.property("directory.videos").getString())
    override val audiosDirectory = Path(environment.config.property("directory.audios").getString())
    override val stylesDirectory = Path(environment.config.property("directory.styles").getString())
    override val otherDirectory = Path(environment.config.property("directory.other").getString())

    override val imagesUrl = environment.config.property("url.images").getString()
    override val videosUrl = environment.config.property("url.videos").getString()
    override val audiosUrl = environment.config.property("url.audios").getString()
    override val stylesUrl = environment.config.property("url.styles").getString()
    override val otherUrl = environment.config.property("url.other").getString()
}