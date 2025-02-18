package fi.lipp.blog.data

import kotlinx.datetime.LocalDateTime
import java.util.UUID

sealed class MessageDto {
    data class View(
        val id: UUID,
        val dialogId: UUID,
        val sender: UserDto.View,
        val content: String,
        val timestamp: LocalDateTime,
        val isRead: Boolean,
    )

    data class Create(
        val avatarUri: String,
        val content: String
    )

    data class Update(
        val content: String
    )
}
