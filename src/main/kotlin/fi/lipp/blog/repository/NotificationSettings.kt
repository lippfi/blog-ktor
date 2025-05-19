package fi.lipp.blog.repository

import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.ReferenceOption

object NotificationSettings : UUIDTable() {
    val user = reference("user_id", Users, onDelete = ReferenceOption.CASCADE).uniqueIndex()
    val notifyAboutComments = bool("notify_about_comments").default(true)
    val notifyAboutReplies = bool("notify_about_replies").default(true)
    val notifyAboutPostReactions = bool("notify_about_post_reactions").default(true)
    val notifyAboutCommentReactions = bool("notify_about_comment_reactions").default(true)
    val notifyAboutPrivateMessages = bool("notify_about_private_messages").default(true)
    val notifyAboutMentions = bool("notify_about_mentions").default(true)
}