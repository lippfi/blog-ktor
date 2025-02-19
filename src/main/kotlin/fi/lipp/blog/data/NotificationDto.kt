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

    @Serializable
    data class FriendRequest(
        @Serializable(with = UUIDSerializer::class)
        override val id: UUID,
        val senderLogin: String,
        @Serializable(with = UUIDSerializer::class)
        val requestId: UUID,
    ) : NotificationDto {
        override val type: NotificationType = NotificationType.FRIEND_REQUEST
    }

    @Serializable
    data class PrivateMessage(
        @Serializable(with = UUIDSerializer::class)
        override val id: UUID,
        val senderLogin: String,
        @Serializable(with = UUIDSerializer::class)
        val dialogId: UUID,
    ) : NotificationDto {
        override val type: NotificationType = NotificationType.PRIVATE_MESSAGE
    }

    @Serializable
    data class Repost(
        @Serializable(with = UUIDSerializer::class)
        override val id: UUID,
        val diaryLogin: String,
        val postUri: String,
    ) : NotificationDto {
        override val type: NotificationType = NotificationType.REPOST
    }
}
