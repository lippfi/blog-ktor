package fi.lipp.blog.repository

import kotlinx.datetime.toKotlinLocalDateTime
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.kotlin.datetime.datetime
import java.time.LocalDateTime

private const val REFRESH_TOKEN_LIFETIME_DAYS = 30L

object UserSessions : UUIDTable() {
    val user = reference("user_id", Users, onDelete = ReferenceOption.CASCADE)
    val refreshToken = varchar("refresh_token", 500).uniqueIndex("idx_session_refresh_token")
    val deviceName = varchar("device_name", 200)
    val location = varchar("location", 200)
    val firstSeen = datetime("first_seen").clientDefault { LocalDateTime.now().toKotlinLocalDateTime() }
    val lastSeen = datetime("last_seen").clientDefault { LocalDateTime.now().toKotlinLocalDateTime() }
    val refreshTokenExpiresAt = datetime("refresh_token_expires_at").clientDefault {
        LocalDateTime.now().plusDays(REFRESH_TOKEN_LIFETIME_DAYS).toKotlinLocalDateTime()
    }
    val userAgent = varchar("user_agent", 500).default("unknown")
    val isRevoked = bool("is_revoked").default(false)
}
