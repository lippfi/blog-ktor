package fi.lipp.blog.data

import fi.lipp.blog.util.UUIDSerializer
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import java.util.UUID
import fi.lipp.blog.data.ReactionDto

sealed interface CommentDto {
    @Serializable
    data class View(
        @Contextual
        val id: UUID,

        val avatar : String,
        val authorNickname : String,
        val authorLogin: String,

        val text: String,
        val creationTime : LocalDateTime,

        val isReactable: Boolean,
        val reactions: List<ReactionDto.ReactionInfo>,

        @Contextual
        val reactionGroupId: UUID,
    )

    @Serializable
    data class Create(
        @Contextual
        val postId: UUID,
        val avatar : String,
        val text: String,

        @Contextual
        val parentCommentId: UUID? = null,

        @Contextual
        val reactionGroupId: UUID? = null,  // If null, use post's reaction group
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
