package fi.lipp.blog.domain

import fi.lipp.blog.repository.UserSessions
import kotlinx.datetime.LocalDateTime
import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import java.util.UUID

class UserSessionEntity(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<UserSessionEntity>(UserSessions)

    var user by UserSessions.user
    var refreshToken by UserSessions.refreshToken
    var deviceName by UserSessions.deviceName
    var location by UserSessions.location
    var firstSeen by UserSessions.firstSeen
    var lastSeen by UserSessions.lastSeen
    var refreshTokenExpiresAt by UserSessions.refreshTokenExpiresAt
    var userAgent by UserSessions.userAgent
    var isRevoked by UserSessions.isRevoked
}
