package fi.lipp.blog.service

import java.nio.file.Path
import fi.lipp.blog.data.StorageQuota

interface ApplicationProperties {
    val resendAPIKey: String

    fun avatarsDirectory(userLogin: String): Path
    fun imagesDirectory(userLogin: String): Path
    fun videosDirectory(userLogin: String): Path
    fun audiosDirectory(userLogin: String): Path
    fun stylesDirectory(userLogin: String): Path
    fun otherDirectory(userLogin: String): Path
    fun reactionsDirectory(userLogin: String): Path

    fun avatarsUrl(userLogin: String): String
    fun imagesUrl(userLogin: String): String
    fun videosUrl(userLogin: String): String
    fun audiosUrl(userLogin: String): String
    fun stylesUrl(userLogin: String): String
    fun otherUrl(userLogin: String): String
    fun reactionsUrl(userLogin: String): String

    /**
     * Get storage quota limit in bytes for the specified quota tier
     */
    fun getQuotaLimit(quota: StorageQuota): Long?
}
