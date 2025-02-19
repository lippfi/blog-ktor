package fi.lipp.blog.service

import fi.lipp.blog.data.NotificationDto
import java.util.UUID

interface NotificationService {
    /**
     * Get paginated list of notifications for a user
     */
    fun getNotifications(userId: UUID): List<NotificationDto>

    /**
     * Get notification by ID
     */
    fun getNotification(userId: UUID, notificationId: UUID): NotificationDto

    /**
     * Mark notification as read
     */
    fun markAsRead(userId: UUID, notificationId: UUID)

    /**
     * Mark all notifications as read for a user
     */
    fun markAllAsRead(userId: UUID)

    /**
     * Notify every subscribed user about new comment in a post
     * Also notifies original comment author about a reply
     */
    fun notifyAboutComment(
        postId: UUID,
        authorId: UUID,
        commentId: UUID,
    )

    /**
     * Notify about a reaction on a post
     */
    fun notifyAboutPostReaction(postId: UUID)

    /**
     * Notify about a repost of a post
     */
    fun notifyAboutRepost(userId: UUID, repostId: UUID)

    /**
     * Notify about a reaction on a comment
     */
    fun notifyAboutCommentReaction(commentId: UUID)

    /**
     * Notify user about being mentioned in a post
     */
    fun notifyAboutPostMention(userId: UUID, postId: UUID, mentionLogin: String)

    /**
     * Notify user about being mentioned in a comment
     */
    fun notifyAboutCommentMention(userId: UUID, commentId: UUID, mentionLogin: String)

    /**
     * Subscribe to post comments
     */
    fun subscribeToComments(userId: UUID, postId: UUID)

    /**
     * Unsubscribe from post comments
     */
    fun unsubscribeFromComments(userId: UUID, postId: UUID)

    /**
     * Check if user is subscribed to post comments
     */
    fun isSubscribedToComments(userId: UUID, postId: UUID): Boolean

    fun readAllPostNotifications(userId: UUID, postId: UUID)

    /**
     * Notify user about a friend request
     */
    fun notifyAboutFriendRequest(recipientId: UUID, requestId: UUID, senderLogin: String)

    /**
     * Mark all friend request notifications as read for a user
     */
    fun readAllFriendRequestNotifications(userId: UUID)

    /**
     * Mark friend request notification as read for a specific request
     */
    fun markFriendRequestNotificationAsRead(userId: UUID, requestId: UUID)

    /**
     * Notify user about a new private message.
     * If there is already an unread notification about new message from the same user,
     * do not send another notification.
     *
     * @param recipientId ID of the message recipient
     * @param messageId ID of the new message
     */
    fun notifyAboutPrivateMessage(recipientId: UUID, dialogId: UUID)

    /**
     * Remove private message notification for a specific dialog.
     * This should be called when there are no more unread messages in the dialog.
     *
     * @param recipientId ID of the notification recipient
     * @param dialogId ID of the dialog
     */
    fun removePrivateMessageNotification(recipientId: UUID, dialogId: UUID)
}
