package fi.lipp.blog.data

import fi.lipp.blog.domain.NotificationType
import fi.lipp.blog.util.UUIDSerializer
import kotlinx.serialization.Serializable
import java.util.UUID

sealed interface NotificationDto {
    val id: UUID
    val type: NotificationType

    @Serializable
    data class NewPost(
        @Serializable(with = UUIDSerializer::class)
        override val id: UUID,
        val diaryLogin: String,
        val postUri: String,
    ) : NotificationDto {
        override val type: NotificationType = NotificationType.NEW_POST
    }

    @Serializable
    data class Comment(
        @Serializable(with = UUIDSerializer::class)
        override val id: UUID,
        val diaryLogin: String,
        val postUri: String,
    ) : NotificationDto {
        override val type: NotificationType = NotificationType.COMMENT
    }

    @Serializable
    data class CommentReply(
        @Serializable(with = UUIDSerializer::class)
        override val id: UUID,
        val diaryLogin: String,
        val postUri: String,
    ) : NotificationDto {
        override val type: NotificationType = NotificationType.COMMENT_REPLY
    }

    @Serializable
    data class PostReaction(
        @Serializable(with = UUIDSerializer::class)
        override val id: UUID,
        val diaryLogin: String,
        val postUri: String,
    ) : NotificationDto {
        override val type: NotificationType = NotificationType.POST_REACTION
    }

    @Serializable
    data class CommentReaction(
        @Serializable(with = UUIDSerializer::class)
        override val id: UUID,
        val diaryLogin: String,
        val postUri: String,
    ) : NotificationDto {
        override val type: NotificationType = NotificationType.COMMENT_REACTION
    }
}
