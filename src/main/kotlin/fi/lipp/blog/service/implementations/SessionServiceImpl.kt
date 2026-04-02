package fi.lipp.blog.service.implementations

import fi.lipp.blog.data.DeviceSessionDto
import fi.lipp.blog.data.TokenPair
import fi.lipp.blog.data.UserPermission
import fi.lipp.blog.domain.UserSessionEntity
import fi.lipp.blog.model.exceptions.SessionNotFoundException
import fi.lipp.blog.model.exceptions.WrongUserException
import fi.lipp.blog.plugins.createAccessToken
import fi.lipp.blog.repository.UserPermissions
import fi.lipp.blog.repository.UserSessions
import fi.lipp.blog.service.SessionService
import kotlinx.datetime.toKotlinLocalDateTime
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDateTime
import java.util.UUID

private const val REFRESH_TOKEN_LIFETIME_DAYS = 30L

private val MOBILE_USER_AGENT_REGEX = Regex("Mobile|Android|iPhone|iPad|iPod|webOS|BlackBerry|Opera Mini|IEMobile", RegexOption.IGNORE_CASE)

private fun isMobileUserAgent(userAgent: String): Boolean {
    return MOBILE_USER_AGENT_REGEX.containsMatchIn(userAgent)
}

class SessionServiceImpl : SessionService {

    private fun loadUserPermissionsInTransaction(userId: UUID): Set<UserPermission> {
        return UserPermissions.select { UserPermissions.user eq userId }
            .mapNotNull { row -> runCatching { row[UserPermissions.permission] }.getOrNull() }
            .toSet()
    }

    override fun createSession(userId: UUID, deviceName: String, location: String, userAgent: String): TokenPair {
        val refreshToken = UUID.randomUUID().toString()
        val session = transaction {
            UserSessionEntity.new {
                this.user = org.jetbrains.exposed.dao.id.EntityID(userId, fi.lipp.blog.repository.Users)
                this.refreshToken = refreshToken
                this.deviceName = deviceName
                this.location = location
                this.userAgent = userAgent
            }
        }
        val permissions = transaction { loadUserPermissionsInTransaction(userId) }
        val accessToken = createAccessToken(userId, session.id.value, permissions)
        return TokenPair(accessToken = accessToken, refreshToken = refreshToken)
    }

    override fun refreshSession(refreshToken: String): TokenPair {
        return transaction {
            val session = UserSessionEntity.find {
                (UserSessions.refreshToken eq refreshToken) and (UserSessions.isRevoked eq false)
            }.firstOrNull() ?: throw SessionNotFoundException()

            val now = LocalDateTime.now()
            val nowKotlin = now.toKotlinLocalDateTime()

            // Check if refresh token has expired
            if (session.refreshTokenExpiresAt < nowKotlin) {
                session.isRevoked = true
                throw SessionNotFoundException()
            }

            // Rotate refresh token
            val newRefreshToken = UUID.randomUUID().toString()
            session.refreshToken = newRefreshToken
            session.refreshTokenExpiresAt = now.plusDays(REFRESH_TOKEN_LIFETIME_DAYS).toKotlinLocalDateTime()
            session.lastSeen = nowKotlin

            val userId = session.user.value
            val permissions = loadUserPermissionsInTransaction(userId)
            val accessToken = createAccessToken(userId, session.id.value, permissions)
            TokenPair(accessToken = accessToken, refreshToken = newRefreshToken)
        }
    }

    override fun revokeSession(userId: UUID, sessionId: UUID) {
        transaction {
            val session = UserSessionEntity.findById(sessionId) ?: throw SessionNotFoundException()
            if (session.user.value != userId) throw WrongUserException()
            session.isRevoked = true
        }
    }

    override fun revokeOtherSessions(userId: UUID, currentSessionId: UUID) {
        transaction {
            UserSessionEntity.find {
                (UserSessions.user eq userId) and (UserSessions.isRevoked eq false)
            }.forEach { session ->
                if (session.id.value != currentSessionId) {
                    session.isRevoked = true
                }
            }
        }
    }

    override fun revokeAllSessions(userId: UUID) {
        transaction {
            UserSessionEntity.find {
                (UserSessions.user eq userId) and (UserSessions.isRevoked eq false)
            }.forEach { session ->
                session.isRevoked = true
            }
        }
    }

    override fun getActiveSessions(userId: UUID, currentSessionId: UUID): List<DeviceSessionDto> {
        return transaction {
            UserSessionEntity.find {
                (UserSessions.user eq userId) and (UserSessions.isRevoked eq false)
            }.map { session ->
                DeviceSessionDto(
                    id = session.id.value,
                    deviceName = session.deviceName,
                    location = session.location,
                    firstSeen = session.firstSeen,
                    lastSeen = session.lastSeen,
                    isMobile = isMobileUserAgent(session.userAgent),
                    isCurrent = session.id.value == currentSessionId,
                )
            }
        }
    }

    override fun isSessionValid(sessionId: UUID): Boolean {
        return transaction {
            val session = UserSessionEntity.findById(sessionId)
            session != null && !session.isRevoked
        }
    }
}
