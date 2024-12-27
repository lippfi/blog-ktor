package fi.lipp.blog.data

import fi.lipp.blog.util.UUIDSerializer
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Serializable
import java.util.UUID

sealed interface CommentDto {
    @Serializable
    data class View(
        @Serializable(with = UUIDSerializer::class)
        val id: UUID,

        val avatar : String,
        val authorNickname : String,
        val authorLogin: String,

        val text: String,
        val creationTime : LocalDateTime,
    )

    @Serializable
    data class Create(
        @Serializable(with = UUIDSerializer::class)
        val postId: UUID,
        val avatar : String,
        val text: String,
    )

    @Serializable
    data class Update(
        @Serializable(with = UUIDSerializer::class)
        val id: UUID,
        @Serializable(with = UUIDSerializer::class)
        val postId: UUID,
        val avatar : String,
        val text: String,
    )
}
