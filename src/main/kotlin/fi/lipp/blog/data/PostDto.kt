package fi.lipp.blog.data

import fi.lipp.blog.util.UUIDSerializer
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Serializable
import java.util.UUID

sealed interface PostDto {
    @Serializable
    data class ReactionInfo(
        @Serializable(with = UUIDSerializer::class)
        val reactionId: UUID,
        val name: String,
        val iconUri: String,
        val count: Int,
        val userLogins: List<String>,
        val anonymousCount: Int
    )

    @Serializable
    data class View(
        @Serializable(with = UUIDSerializer::class)
        val id: UUID,
        val uri : String,

        val avatar : String,
        val authorNickname : String,
        val authorLogin: String,

        val title : String,
        val text : String,
        val creationTime : LocalDateTime,

        val isPreface : Boolean,
        val isEncrypted: Boolean,

        val classes : String,
        var tags : Set<String>,

        val dislikeCount: Int,
        val isDislikedByMe: Boolean,

        val isReactable: Boolean,
        val reactions: List<ReactionInfo>,
        val isCommentable: Boolean,
        val comments: List<CommentDto.View>,

        @Serializable(with = UUIDSerializer::class)
        val readGroupId: UUID,
        @Serializable(with = UUIDSerializer::class)
        val commentGroupId: UUID,
        @Serializable(with = UUIDSerializer::class)
        val reactionGroupId: UUID,
    ) : PostDto

    @Serializable
    data class Create(
        val uri : String,
        val avatar : String,

        val title : String,
        val text : String,

        @Serializable(with = UUIDSerializer::class)
        val readGroupId: UUID,
        @Serializable(with = UUIDSerializer::class)
        val commentGroupId: UUID,
        @Serializable(with = UUIDSerializer::class)
        val reactionGroupId: UUID,

        var tags : Set<String>,
        val classes : String,

        val isPreface : Boolean,
        val isEncrypted: Boolean,
    ) : PostDto

    @Serializable
    data class Update(
        @Serializable(with = UUIDSerializer::class)
        val id: UUID,
        val uri : String,
        val avatar : String,

        val title : String,
        val text : String,

        @Serializable(with = UUIDSerializer::class)
        val readGroupId: UUID,
        @Serializable(with = UUIDSerializer::class)
        val commentGroupId: UUID,
        @Serializable(with = UUIDSerializer::class)
        val reactionGroupId: UUID,

        var tags : Set<String>,
        val classes : String,

        val isEncrypted: Boolean,
    ) : PostDto
}
