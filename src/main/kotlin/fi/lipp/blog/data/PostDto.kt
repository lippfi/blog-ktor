package fi.lipp.blog.data

import fi.lipp.blog.util.UUIDSerializer
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Serializable
import java.util.UUID
import fi.lipp.blog.data.ReactionDto

sealed interface PostDto {
    @Serializable
    data class View(
        @Serializable(with = UUIDSerializer::class)
        val id: UUID,
        val uri : String,

        val avatar : String,
        val authorNickname : String,
        val authorLogin: String,
        val diaryLogin: String,

        val title : String,
        val text : String,
        val creationTime : LocalDateTime,

        val isPreface : Boolean,
        val isEncrypted: Boolean,

        val classes : String,
        var tags : Set<String>,

        val isReactable: Boolean,
        val reactions: List<ReactionDto.ReactionInfo>,
        val isCommentable: Boolean,
        val comments: List<CommentDto.View>,

        @Serializable(with = UUIDSerializer::class)
        val readGroupId: UUID,
        @Serializable(with = UUIDSerializer::class)
        val commentGroupId: UUID,
        @Serializable(with = UUIDSerializer::class)
        val reactionGroupId: UUID,
        @Serializable(with = UUIDSerializer::class)
        val commentReactionGroupId: UUID,
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
        @Serializable(with = UUIDSerializer::class)
        val commentReactionGroupId: UUID,

        @Serializable
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
        @Serializable(with = UUIDSerializer::class)
        val commentReactionGroupId: UUID,

        var tags : Set<String>,
        val classes : String,

        val isEncrypted: Boolean,
    ) : PostDto
}
