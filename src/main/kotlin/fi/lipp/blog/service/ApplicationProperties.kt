package fi.lipp.blog.service

import java.nio.file.Path

interface ApplicationProperties {
    val resendAPIKey: String
    val databaseUrl: String
    val databaseUser: String
    val databasePassword: String
    val databaseDriver: String

    val useCdn: Boolean
    val cdnBaseUrl: String
    val cdnApiKey: String

    fun storageBaseDir(): Path
    fun filesBaseUrl(): String
}
