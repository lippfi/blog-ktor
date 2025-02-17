package fi.lipp.blog.data

import fi.lipp.blog.util.UUIDSerializer
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class FriendRequestDto(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID,
    val user: UserDto.View,
    val message: String,
    val label: String?,
    val createdAt: LocalDateTime
) {
    @Serializable
    data class Create(
        val toUser: String,
        val message: String,
        val label: String?
    )
}
