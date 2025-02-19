package fi.lipp.blog.domain

import kotlinx.serialization.Serializable

@Serializable
enum class NotificationType {
    COMMENT,
    NEW_POST,
    COMMENT_REPLY,
    POST_REACTION,
    COMMENT_REACTION,
    POST_MENTION,
    COMMENT_MENTION,
    PRIVATE_MESSAGE,
    FRIEND_REQUEST,
    REPOST,
}
