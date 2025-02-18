package fi.lipp.blog.service.implementations

import fi.lipp.blog.data.NotificationDto
import fi.lipp.blog.domain.*
import fi.lipp.blog.model.exceptions.NotificationNotFoundException
import fi.lipp.blog.model.exceptions.PostNotFoundException
import fi.lipp.blog.model.exceptions.WrongUserException
import fi.lipp.blog.repository.*
import fi.lipp.blog.service.NotificationService
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.*

class NotificationServiceImpl : NotificationService {

    override fun getNotifications(userId: UUID): List<NotificationDto> = transaction {
        Notifications
            .select { Notifications.recipient eq userId }
            .orderBy(Notifications.createdAt to SortOrder.DESC)
            .limit(100)
            .map { toNotificationDto(it) }
    }

    override fun getNotification(userId: UUID, notificationId: UUID): NotificationDto = transaction {
        val notification = NotificationEntity.findById(notificationId)
            ?: throw NotificationNotFoundException()

        if (notification.recipient.id.value != userId) {
            throw NotificationNotFoundException()
        }

        toNotificationDto(notification)
    }

    override fun markAsRead(userId: UUID, notificationId: UUID) = transaction {
        val notification = NotificationEntity.findById(notificationId)
            ?: throw NotificationNotFoundException()

        if (notification.recipient.id.value != userId) {
            throw NotificationNotFoundException()
        }

        notification.isRead = true
    }

    override fun markAllAsRead(userId: UUID): Unit = transaction {
        Notifications.update({ Notifications.recipient eq userId }) {
            it[isRead] = true
        }
    }

    override fun notifyAboutComment(
        postId: UUID,
        authorId: UUID,
        commentId: UUID,
    ) {
        transaction {
            val subscribedUsers = PostSubscriptionEntity.find { PostSubscriptions.post eq postId }.map { it.user.id.value }.toMutableSet()
            subscribedUsers.remove(authorId)

            val parentCommentUser = CommentEntity.findById(commentId)?.parentComment?.value
                ?.takeIf { UserEntity.findById(it)?.notifyAboutReplies == true }
            if (parentCommentUser != null) {
                subscribedUsers.remove(parentCommentUser)
                if (parentCommentUser != authorId) {
                    Notifications.insert {
                        it[type] = NotificationType.COMMENT_REPLY
                        it[recipient] = parentCommentUser
                        it[relatedPost] = EntityID(postId, Posts)
                        it[relatedComment] = EntityID(commentId, Comments)
                    }
                }
            }

            Notifications.batchInsert(subscribedUsers) { userId ->
                this[Notifications.type] = NotificationType.COMMENT
                this[Notifications.recipient] = userId
                this[Notifications.relatedPost] = EntityID(postId, Posts)
                this[Notifications.relatedComment] = EntityID(commentId, Comments)
            }
        }
    }

    override fun notifyAboutPostReaction(postId: UUID) {
        transaction {
            val postAuthor = PostEntity.findById(postId)?.authorId ?: return@transaction
            val shouldBeNotified = UserEntity.findById(postAuthor)?.notifyAboutPostReactions ?: return@transaction
            if (shouldBeNotified) {
                Notifications.insert {
                    it[type] = NotificationType.POST_REACTION
                    it[recipient] = postAuthor
                    it[relatedPost] = EntityID(postId, Posts)
                }
            }
        }
    }

    override fun notifyAboutCommentReaction(commentId: UUID) {
        transaction {
            val commentAuthor = CommentEntity.findById(commentId)?.authorId ?: return@transaction
            val shouldBeNotified = UserEntity.findById(commentAuthor)?.notifyAboutCommentReactions ?: return@transaction
            if (shouldBeNotified) {
                Notifications.insert {
                    it[type] = NotificationType.COMMENT_REACTION
                    it[recipient] = commentAuthor
                    it[relatedComment] = EntityID(commentId, Comments)
                }
            }
        }
    }

    override fun notifyAboutPostMention(userId: UUID, postId: UUID, mentionLogin: String) {
        transaction {
            val postEntity = PostEntity.findById(postId) ?: return@transaction
            if (postEntity.authorId.value != userId) throw WrongUserException()

            val diaryOwnerByLogin = DiaryEntity.find { Diaries.login eq mentionLogin }.singleOrNull()?.owner ?: return@transaction
            val mentionedUser = UserEntity.findById(diaryOwnerByLogin)!!
            val shouldBeNotified = mentionedUser.notifyAboutMentions
            if (shouldBeNotified) {
                Notifications.insert {
                    it[type] = NotificationType.POST_MENTION
                    it[recipient] = userId
                    it[relatedPost] = postEntity.id
                }
            }
        }
    }

    override fun notifyAboutCommentMention(userId: UUID, commentId: UUID, mentionLogin: String) {
        transaction {
            val commentEntity = CommentEntity.findById(commentId) ?: return@transaction
            if (commentEntity.authorId.value != userId) throw WrongUserException()

            val diaryOwnerByLogin = DiaryEntity.find { Diaries.login eq mentionLogin }.singleOrNull()?.owner ?: return@transaction
            val mentionedUser = UserEntity.findById(diaryOwnerByLogin)!!
            val shouldBeNotified = mentionedUser.notifyAboutMentions
            if (shouldBeNotified) {
                Notifications.insert {
                    it[type] = NotificationType.COMMENT_MENTION
                    it[recipient] = userId
                    it[relatedPost] = commentEntity.postId
                }
            }
        }
    }

    private fun toNotificationDto(row: ResultRow): NotificationDto {
        val relatedPost = row[Notifications.relatedPost]?.let { PostEntity[it] }
        val postUri = relatedPost?.uri
        val diaryLogin = relatedPost?.diaryId?.let { DiaryEntity.findById(it)?.login }

        return when (row[Notifications.type]) {
            NotificationType.NEW_POST -> NotificationDto.NewPost(
                id = row[Notifications.id].value,
                diaryLogin = diaryLogin!!,
                postUri = postUri!!,
            )
            NotificationType.COMMENT -> NotificationDto.Comment(
                id = row[Notifications.id].value,
                diaryLogin = diaryLogin!!,
                postUri = postUri!!,
            )
            NotificationType.COMMENT_REPLY -> NotificationDto.CommentReply(
                id = row[Notifications.id].value,
                diaryLogin = diaryLogin!!,
                postUri = postUri!!,
            )
            NotificationType.POST_REACTION -> NotificationDto.PostReaction(
                id = row[Notifications.id].value,
                diaryLogin = diaryLogin!!,
                postUri = postUri!!,
            )
            NotificationType.COMMENT_REACTION -> NotificationDto.CommentReaction(
                id = row[Notifications.id].value,
                diaryLogin = diaryLogin!!,
                postUri = postUri!!,
            )
            NotificationType.FRIEND_REQUEST -> {
                val requestId = row[Notifications.relatedRequest]?.value ?: throw IllegalStateException("Friend request notification without request ID")
                val senderDiary = DiaryEntity.find { Diaries.owner eq row[Notifications.sender] }.single()
                NotificationDto.FriendRequest(
                    id = row[Notifications.id].value,
                    senderLogin = senderDiary.login,
                    requestId = requestId,
                )
            }
            NotificationType.PRIVATE_MESSAGE -> {
                val dialogId = row[Notifications.relatedDialog]?.value ?: throw IllegalStateException("Private message notification without dialog ID")
                val senderDiary = DiaryEntity.find { Diaries.owner eq row[Notifications.sender] }.single()
                NotificationDto.PrivateMessage(
                    id = row[Notifications.id].value,
                    senderLogin = senderDiary.login,
                    dialogId = dialogId,
                )
            }
            else -> TODO()
        }
    }

    private fun toNotificationDto(notification: NotificationEntity): NotificationDto {
        val relatedPost = notification.relatedPost
        val postUri = relatedPost?.uri
        val diaryLogin = relatedPost?.diaryId?.let { DiaryEntity.findById(it)?.login }

        return when (notification.type) {
            NotificationType.NEW_POST -> NotificationDto.NewPost(
                id = notification.id.value,
                diaryLogin = diaryLogin!!,
                postUri = postUri!!,
            )
            NotificationType.COMMENT -> NotificationDto.Comment(
                id = notification.id.value,
                diaryLogin = diaryLogin!!,
                postUri = postUri!!,
            )
            NotificationType.COMMENT_REPLY -> NotificationDto.CommentReply(
                id = notification.id.value,
                diaryLogin = diaryLogin!!,
                postUri = postUri!!,
            )
            NotificationType.POST_REACTION -> NotificationDto.PostReaction(
                id = notification.id.value,
                diaryLogin = diaryLogin!!,
                postUri = postUri!!,
            )
            NotificationType.COMMENT_REACTION -> NotificationDto.CommentReaction(
                id = notification.id.value,
                diaryLogin = diaryLogin!!,
                postUri = postUri!!,
            )
            NotificationType.FRIEND_REQUEST -> {
                val requestId = notification.relatedRequest?.id?.value ?: throw IllegalStateException("Friend request notification without request ID")
                val senderDiary = DiaryEntity.find { Diaries.owner eq notification.sender.id }.single()
                NotificationDto.FriendRequest(
                    id = notification.id.value,
                    senderLogin = senderDiary.login,
                    requestId = requestId,
                )
            }
            NotificationType.PRIVATE_MESSAGE -> {
                val senderDiary = DiaryEntity.find { Diaries.owner eq notification.sender.id }.single()
                NotificationDto.PrivateMessage(
                    id = notification.id.value,
                    senderLogin = senderDiary.login,
                    dialogId = notification.relatedDialog!!.id.value,
                )
            }
            else -> TODO()
        }
    }

    override fun notifyAboutPrivateMessage(recipientId: UUID, dialogId: UUID) {
        transaction {
            val dialog = DialogEntity.findById(dialogId) ?: throw IllegalArgumentException("Dialog not found")
            val senderId = if (dialog.user1.id.value == recipientId) dialog.user2.id.value else dialog.user1.id.value

            // Check if there's already an unread notification from this sender
            val existingUnreadNotification = Notifications
                .select {
                    (Notifications.recipient eq recipientId) and
                    (Notifications.relatedDialog eq dialogId) and
                    (Notifications.type eq NotificationType.PRIVATE_MESSAGE) and
                    (Notifications.isRead eq false)
                }
                .firstOrNull()

            // If there's no unread notification from this sender, create a new one
            if (existingUnreadNotification == null) {
                Notifications.insert {
                    it[type] = NotificationType.PRIVATE_MESSAGE
                    it[sender] = senderId
                    it[recipient] = recipientId
                    it[relatedDialog] = dialog.id
                }
            }
        }
    }

    override fun subscribeToComments(userId: UUID, postId: UUID) {
        transaction {
            val postEntity = PostEntity.findById(postId) ?: throw PostNotFoundException()

            if (!isSubscribedToComments(userId, postId)) {
                PostSubscriptionEntity.new {
                    user = UserEntity[userId]
                    post = postEntity
                }
            }
        }
    }

    override fun unsubscribeFromComments(userId: UUID, postId: UUID) {
        transaction {
            PostSubscriptionEntity.find {
                (PostSubscriptions.user eq userId) and (PostSubscriptions.post eq postId)
            }.firstOrNull()?.delete()
        }
    }

    override fun isSubscribedToComments(userId: UUID, postId: UUID): Boolean = transaction {
        PostSubscriptionEntity.find {
            (PostSubscriptions.user eq userId) and (PostSubscriptions.post eq postId)
        }.firstOrNull() != null
    }

    override fun readAllPostNotifications(userId: UUID, postId: UUID) {
        transaction {
            Notifications.update({ (Notifications.relatedPost eq postId) and (Notifications.recipient eq userId) }) {
                it[isRead] = true
            }
        }
    }

    override fun notifyAboutFriendRequest(recipientId: UUID, requestId: UUID, senderLogin: String) {
        transaction {
            val senderDiary = DiaryEntity.find { Diaries.login eq senderLogin }.single()
            Notifications.insert {
                it[type] = NotificationType.FRIEND_REQUEST
                it[sender] = senderDiary.owner
                it[recipient] = recipientId
                it[relatedRequest] = requestId
            }
        }
    }

    override fun readAllFriendRequestNotifications(userId: UUID) {
        transaction {
            Notifications.update({ (Notifications.type eq NotificationType.FRIEND_REQUEST) and (Notifications.recipient eq userId) }) {
                it[isRead] = true
            }
        }
    }

    override fun markFriendRequestNotificationAsRead(userId: UUID, requestId: UUID) {
        transaction {
            Notifications.update({ 
                (Notifications.type eq NotificationType.FRIEND_REQUEST) and 
                (Notifications.recipient eq userId) and 
                (Notifications.relatedRequest eq requestId)
            }) {
                it[isRead] = true
            }
        }
    }

    override fun removePrivateMessageNotification(recipientId: UUID, dialogId: UUID) {
        transaction {
            Notifications.deleteWhere {
                (Notifications.type eq NotificationType.PRIVATE_MESSAGE) and
                (Notifications.recipient eq recipientId) and
                (Notifications.relatedDialog eq dialogId)
            }
        }
    }
}
