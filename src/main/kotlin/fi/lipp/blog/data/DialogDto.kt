package fi.lipp.blog.data

import java.util.UUID

sealed class DialogDto {
    data class View(
        val id: UUID,
        val user: UserDto.View,
        val lastMessage: MessageDto.View?,
        val isUnread: Boolean,
    )

    data class Create(
        val userId: UUID  // ID of the user to start dialog with
    )
}