package fi.lipp.blog.service

import java.nio.file.Path

interface ApplicationProperties {
    val resendAPIKey: String

    fun storageBaseDir(): Path
    fun filesBaseUrl(): String
}
