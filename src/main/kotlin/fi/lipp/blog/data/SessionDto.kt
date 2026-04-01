package fi.lipp.blog.data

import fi.lipp.blog.util.UUIDSerializer
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class TokenPair(
    val accessToken: String,
    val refreshToken: String,
)

@Serializable
data class DeviceSessionDto(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID,
    val deviceName: String,
    val location: String,
    val firstSeen: LocalDateTime,
    val lastSeen: LocalDateTime,
    val isMobile: Boolean,
    val isCurrent: Boolean,
)

@Serializable
data class RefreshRequest(
    val refreshToken: String,
)
