package fi.lipp.blog.data

import fi.lipp.blog.domain.NotificationType
import fi.lipp.blog.util.UUIDSerializer
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
sealed interface NotificationDto {
    val id: @Serializable(with = UUIDSerializer::class) UUID
    val type: NotificationType
    val isRead: Boolean
    val createdAt: Instant

    @Serializable
    @SerialName("NewPost")
    data class NewPost(
        @Serializable(with = UUIDSerializer::class)
        override val id: UUID,
        val diaryLogin: String,
        val postUri: String,
        override val isRead: Boolean = false,
        override val createdAt: Instant,
        override val type: NotificationType = NotificationType.NEW_POST,
    ) : NotificationDto

    @Serializable
    @SerialName("Comment")
    data class Comment(
        @Serializable(with = UUIDSerializer::class)
        override val id: UUID,
        val diaryLogin: String,
        val postUri: String,
        override val isRead: Boolean = false,
        override val createdAt: Instant,
        override val type: NotificationType = NotificationType.COMMENT,
    ) : NotificationDto

    @Serializable
    @SerialName("CommentReply")
    data class CommentReply(
        @Serializable(with = UUIDSerializer::class)
        override val id: UUID,
        val diaryLogin: String,
        val postUri: String,
        override val isRead: Boolean = false,
        override val createdAt: Instant,
        override val type: NotificationType = NotificationType.COMMENT_REPLY,
    ) : NotificationDto

    @Serializable
    @SerialName("PostReaction")
    data class PostReaction(
        @Serializable(with = UUIDSerializer::class)
        override val id: UUID,
        val diaryLogin: String,
        val postUri: String,
        override val isRead: Boolean = false,
        override val createdAt: Instant,
        override val type: NotificationType = NotificationType.POST_REACTION,
    ) : NotificationDto

    @Serializable
    @SerialName("CommentReaction")
    data class CommentReaction(
        @Serializable(with = UUIDSerializer::class)
        override val id: UUID,
        val diaryLogin: String,
        val postUri: String,
        override val isRead: Boolean = false,
        override val createdAt: Instant,
        override val type: NotificationType = NotificationType.COMMENT_REACTION,
    ) : NotificationDto

    @Serializable
    @SerialName("PostMention")
    data class PostMention(
        @Serializable(with = UUIDSerializer::class)
        override val id: UUID,
        val diaryLogin: String,
        val postUri: String,
        override val isRead: Boolean = false,
        override val createdAt: Instant,
        override val type: NotificationType = NotificationType.POST_MENTION,
    ) : NotificationDto

    @Serializable
    @SerialName("CommentMention")
    data class CommentMention(
        @Serializable(with = UUIDSerializer::class)
        override val id: UUID,
        val diaryLogin: String,
        val postUri: String,
        override val isRead: Boolean = false,
        override val createdAt: Instant,
        override val type: NotificationType = NotificationType.COMMENT_MENTION,
    ) : NotificationDto

    @Serializable
    @SerialName("FriendRequest")
    data class FriendRequest(
        @Serializable(with = UUIDSerializer::class)
        override val id: UUID,
        val senderLogin: String,
        @Serializable(with = UUIDSerializer::class)
        val requestId: UUID,
        override val isRead: Boolean = false,
        override val createdAt: Instant,
        override val type: NotificationType = NotificationType.FRIEND_REQUEST,
    ) : NotificationDto

    @Serializable
    @SerialName("PrivateMessage")
    data class PrivateMessage(
        @Serializable(with = UUIDSerializer::class)
        override val id: UUID,
        val senderLogin: String,
        @Serializable(with = UUIDSerializer::class)
        val dialogId: UUID,
        override val isRead: Boolean = false,
        override val createdAt: Instant,
        override val type: NotificationType = NotificationType.PRIVATE_MESSAGE,
    ) : NotificationDto

    @Serializable
    @SerialName("Repost")
    data class Repost(
        @Serializable(with = UUIDSerializer::class)
        override val id: UUID,
        val diaryLogin: String,
        val postUri: String,
        override val isRead: Boolean = false,
        override val createdAt: Instant,
        override val type: NotificationType = NotificationType.REPOST,
    ) : NotificationDto

    @Serializable
    @SerialName("CommentRepost")
    data class CommentRepost(
        @Serializable(with = UUIDSerializer::class)
        override val id: UUID,
        val diaryLogin: String,
        val postUri: String,
        override val isRead: Boolean = false,
        override val createdAt: Instant,
        override val type: NotificationType = NotificationType.COMMENT_REPOST,
    ) : NotificationDto
}
