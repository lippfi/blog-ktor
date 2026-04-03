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

    val maxImageSize: Int
    val maxVideoSize: Int
    val maxAudioSize: Int
    val maxStyleSize: Int
    val maxOtherSize: Int
    val maxAvatarSize: Int
    val maxReactionSize: Int
    val bcryptCost: Int
    val requireInviteCode: Boolean
    val adminLogin: String
}
