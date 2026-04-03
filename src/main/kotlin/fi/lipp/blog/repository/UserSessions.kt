package fi.lipp.blog.repository

import kotlinx.datetime.Clock
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp
import kotlin.time.Duration.Companion.days

private const val REFRESH_TOKEN_LIFETIME_DAYS = 30

object UserSessions : UUIDTable() {
    val user = reference("user_id", Users, onDelete = ReferenceOption.CASCADE)
    val refreshToken = varchar("refresh_token", 500).uniqueIndex("idx_session_refresh_token")
    val deviceName = varchar("device_name", 200)
    val location = varchar("location", 200)
    val firstSeen = timestamp("first_seen").clientDefault { Clock.System.now() }
    val lastSeen = timestamp("last_seen").clientDefault { Clock.System.now() }
    val refreshTokenExpiresAt = timestamp("refresh_token_expires_at").clientDefault {
        Clock.System.now().plus(REFRESH_TOKEN_LIFETIME_DAYS.days)
    }
    val userAgent = varchar("user_agent", 500).default("unknown")
    val isRevoked = bool("is_revoked").default(false)
}
