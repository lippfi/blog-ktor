package fi.lipp.blog.service

import java.nio.file.Path

interface ApplicationProperties {
    val emailHost: String
    val emailPort: String
    val emailAddress: String
    val emailPassword: String

    val imagesDirectory: Path
    val videosDirectory: Path
    val audiosDirectory: Path
    val stylesDirectory: Path
    val otherDirectory: Path

    val imagesUrl: String
    val videosUrl: String
    val audiosUrl: String
    val stylesUrl: String
    val otherUrl: String
}