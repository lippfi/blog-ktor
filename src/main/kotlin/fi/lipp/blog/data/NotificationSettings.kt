package fi.lipp.blog.data

import kotlinx.serialization.Serializable

@Serializable
data class NotificationSettings(
    val notifyAboutComments: Boolean = true,
    val notifyAboutReplies: Boolean = true,
    val notifyAboutPostReactions: Boolean = true,
    val notifyAboutCommentReactions: Boolean = true,
    val notifyAboutPrivateMessages: Boolean = true,
    val notifyAboutMentions: Boolean = true,
    val notifyAboutNewPosts: Boolean = true,
    val notifyAboutFriendRequests: Boolean = true,
    val notifyAboutReposts: Boolean = true,
)
