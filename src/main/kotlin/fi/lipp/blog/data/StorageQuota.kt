package fi.lipp.blog.data

import fi.lipp.blog.service.ApplicationProperties
import kotlinx.serialization.Serializable

/**
 * Represents different storage quota tiers for users.
 * Each tier has a daily upload limit in bytes.
 */
@Serializable
enum class StorageQuota {
    BASIC,
    STANDARD,
    MAX,
    UNLIMITED;

    fun getDailyLimitBytes(properties: ApplicationProperties): Long? = properties.getQuotaLimit(this)

    companion object {
        fun getQuota(properties: ApplicationProperties, dailyUploadBytes: Long?): StorageQuota {
            if (dailyUploadBytes == null) return UNLIMITED
            return values().firstOrNull { quota ->
                val limit = quota.getDailyLimitBytes(properties)
                limit != null && dailyUploadBytes <= limit
            } ?: UNLIMITED
        }
    }
}
