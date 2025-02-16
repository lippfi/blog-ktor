package fi.lipp.blog.service

import java.nio.file.Path
import fi.lipp.blog.data.StorageQuota

interface ApplicationProperties {
    val emailHost: String
    val emailPort: String
    val emailAddress: String
    val emailPassword: String

    fun avatarsDirectory(userLogin: String): Path
    fun imagesDirectory(userLogin: String): Path
    fun videosDirectory(userLogin: String): Path
    fun audiosDirectory(userLogin: String): Path
    fun stylesDirectory(userLogin: String): Path
    fun otherDirectory(userLogin: String): Path
    fun reactionsDirectory(userLogin: String): Path

    val avatarsUrl: String
    val imagesUrl: String
    val videosUrl: String
    val audiosUrl: String
    val stylesUrl: String
    val otherUrl: String
    val reactionsUrl: String

    /**
     * Get storage quota limit in bytes for the specified quota tier
     */
    fun getQuotaLimit(quota: StorageQuota): Long?
}
