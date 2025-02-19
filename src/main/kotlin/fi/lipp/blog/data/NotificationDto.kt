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
        override val type: NotificationType = NotificationType.NEW_POST,
    ) : NotificationDto

    @Serializable
    data class Comment(
        @Serializable(with = UUIDSerializer::class)
        override val id: UUID,
        val diaryLogin: String,
        val postUri: String,
        override val type: NotificationType = NotificationType.COMMENT,
    ) : NotificationDto

    @Serializable
    data class CommentReply(
        @Serializable(with = UUIDSerializer::class)
        override val id: UUID,
        val diaryLogin: String,
        val postUri: String,
        override val type: NotificationType = NotificationType.COMMENT_REPLY,
    ) : NotificationDto

    @Serializable
    data class PostReaction(
        @Serializable(with = UUIDSerializer::class)
        override val id: UUID,
        val diaryLogin: String,
        val postUri: String,
        override val type: NotificationType = NotificationType.POST_REACTION,
    ) : NotificationDto

    @Serializable
    data class CommentReaction(
        @Serializable(with = UUIDSerializer::class)
        override val id: UUID,
        val diaryLogin: String,
        val postUri: String,
        override val type: NotificationType = NotificationType.COMMENT_REACTION,
    ) : NotificationDto

    @Serializable
    data class FriendRequest(
        @Serializable(with = UUIDSerializer::class)
        override val id: UUID,
        val senderLogin: String,
        @Serializable(with = UUIDSerializer::class)
        val requestId: UUID,
        override val type: NotificationType = NotificationType.FRIEND_REQUEST,
    ) : NotificationDto

    @Serializable
    data class PrivateMessage(
        @Serializable(with = UUIDSerializer::class)
        override val id: UUID,
        val senderLogin: String,
        @Serializable(with = UUIDSerializer::class)
        val dialogId: UUID,
        override val type: NotificationType = NotificationType.PRIVATE_MESSAGE,
    ) : NotificationDto

    @Serializable
    data class Repost(
        @Serializable(with = UUIDSerializer::class)
        override val id: UUID,
        val diaryLogin: String,
        val postUri: String,
        override val type: NotificationType = NotificationType.REPOST,
    ) : NotificationDto
}
