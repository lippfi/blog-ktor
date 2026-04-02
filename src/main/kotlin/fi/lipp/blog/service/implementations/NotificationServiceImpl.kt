package fi.lipp.blog.service.implementations

import fi.lipp.blog.data.NotificationDto
import fi.lipp.blog.domain.*
import fi.lipp.blog.model.exceptions.NotificationNotFoundException
import fi.lipp.blog.model.exceptions.PostNotFoundException
import fi.lipp.blog.model.exceptions.WrongUserException
import fi.lipp.blog.repository.*
import fi.lipp.blog.service.NotificationService
import fi.lipp.blog.service.NotificationWebSocketService
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.*

class NotificationServiceImpl(
    private val notificationWebSocketService: NotificationWebSocketService,
) : NotificationService {

    /**
     * Check if there is a mutual ignore between two users (either direction).
     * Returns true if either user ignores the other.
     */
    private fun isIgnored(userId1: UUID, userId2: UUID): Boolean {
        return IgnoreList.select {
            ((IgnoreList.user eq userId1) and (IgnoreList.ignoredUser eq userId2)) or
            ((IgnoreList.user eq userId2) and (IgnoreList.ignoredUser eq userId1))
        }.count() > 0
    }

    /**
     * Get the set of user IDs that the given user ignores or is ignored by.
     */
    private fun getIgnoredUserIds(userId: UUID): Set<UUID> {
        val ignored = IgnoreList.select { IgnoreList.user eq userId }
            .map { it[IgnoreList.ignoredUser].value }
        val ignoredBy = IgnoreList.select { IgnoreList.ignoredUser eq userId }
            .map { it[IgnoreList.user].value }
        return (ignored + ignoredBy).toSet()
    }

    private fun getNotificationSettingsEntity(userId: UUID): NotificationSettingsEntity {
        return NotificationSettingsEntity.find {
            NotificationSettings.user eq userId
        }.single()
    }

    private fun isNotificationEnabled(userId: UUID, setting: (NotificationSettingsEntity) -> Boolean): Boolean {
        val entity = getNotificationSettingsEntity(userId)
        return entity.let(setting)
    }

    override fun getNotificationSettings(userId: UUID): fi.lipp.blog.data.NotificationSettings = transaction {
        val entity = getNotificationSettingsEntity(userId)
        fi.lipp.blog.data.NotificationSettings(
            notifyAboutComments = entity.notifyAboutComments,
            notifyAboutReplies = entity.notifyAboutReplies,
            notifyAboutPostReactions = entity.notifyAboutPostReactions,
            notifyAboutCommentReactions = entity.notifyAboutCommentReactions,
            notifyAboutPrivateMessages = entity.notifyAboutPrivateMessages,
            notifyAboutMentions = entity.notifyAboutMentions,
            notifyAboutNewPosts = entity.notifyAboutNewPosts,
            notifyAboutFriendRequests = entity.notifyAboutFriendRequests,
            notifyAboutReposts = entity.notifyAboutReposts,
        )
    }

    override fun getNotifications(userId: UUID): List<NotificationDto> = transaction {
        val ignoredUserIds = getIgnoredUserIds(userId)

        val query = if (ignoredUserIds.isEmpty()) {
            Notifications.select { Notifications.recipient eq userId }
        } else {
            Notifications.select {
                (Notifications.recipient eq userId) and
                (Notifications.sender notInList ignoredUserIds)
            }
        }

        query.orderBy(Notifications.createdAt to SortOrder.DESC)
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

    override fun deleteNotification(userId: UUID, notificationId: UUID) = transaction {
        val notification = NotificationEntity.findById(notificationId)
            ?: throw NotificationNotFoundException()

        if (notification.recipient.id.value != userId) {
            throw NotificationNotFoundException()
        }

        notification.delete()
    }

    override fun deleteAllNotifications(userId: UUID): Unit = transaction {
        Notifications.deleteWhere { recipient eq userId }
    }

    /**
     * Check if a comment would be hidden from a given viewer due to ignore list + comment dependencies.
     * A comment is hidden if any user in its dependency set is in the viewer's ignore list (either direction).
     */
    private fun isCommentHiddenForUser(commentId: UUID, viewerUserId: UUID): Boolean {
        val dependencyUserIds = CommentDependencies
            .select { CommentDependencies.comment eq commentId }
            .map { it[CommentDependencies.user].value }
            .toSet()

        if (dependencyUserIds.isEmpty()) return false

        val count = IgnoreList.select {
            ((IgnoreList.user eq viewerUserId) and (IgnoreList.ignoredUser inList dependencyUserIds)) or
            ((IgnoreList.ignoredUser eq viewerUserId) and (IgnoreList.user inList dependencyUserIds))
        }.count()

        return count > 0
    }

    override fun notifyAboutComment(
        postId: UUID,
        authorId: UUID,
        commentId: UUID,
    ) {
        transaction {
            val subscribedUsers = PostSubscriptionEntity.find { PostSubscriptions.post eq postId }
                .map { it.user.id.value }
                .toMutableSet()
            subscribedUsers.remove(authorId)

            // Filter out users who would not see this comment due to ignore list + comment dependencies
            subscribedUsers.removeAll { userId -> isCommentHiddenForUser(commentId, userId) }

            val parentCommentUser = CommentEntity.findById(commentId)?.parentComment?.authorId
            if (parentCommentUser != null && parentCommentUser != authorId && !isCommentHiddenForUser(commentId, parentCommentUser)) {
                val shouldNotifyReply = isNotificationEnabled(parentCommentUser) { it.notifyAboutReplies }
                if (shouldNotifyReply) {
                    subscribedUsers.remove(parentCommentUser)
                    val notificationRow = Notifications.insertAndGetId {
                        it[type] = NotificationType.COMMENT_REPLY
                        it[sender] = EntityID(authorId, Users)
                        it[recipient] = parentCommentUser
                        it[relatedPost] = EntityID(postId, Posts)
                        it[relatedComment] = EntityID(commentId, Comments)
                    }
                    pushNotification(parentCommentUser, notificationRow.value)
                }
            }

            val usersToNotify = subscribedUsers.filter { userId ->
                isNotificationEnabled(userId) { it.notifyAboutComments }
            }

            Notifications.batchInsert(usersToNotify) { userId ->
                this[Notifications.type] = NotificationType.COMMENT
                this[Notifications.sender] = EntityID(authorId, Users)
                this[Notifications.recipient] = userId
                this[Notifications.relatedPost] = EntityID(postId, Posts)
                this[Notifications.relatedComment] = EntityID(commentId, Comments)
            }.forEach { row ->
                pushNotification(row[Notifications.recipient].value, row[Notifications.id].value)
            }
        }
    }

    override fun notifyAboutPostReaction(userId: UUID, postId: UUID) {
        transaction {
            val postAuthor = PostEntity.findById(postId)?.authorId ?: return@transaction
            if (userId == postAuthor) return@transaction
            if (isIgnored(userId, postAuthor)) return@transaction
            if (!isNotificationEnabled(postAuthor) { it.notifyAboutPostReactions }) return@transaction

            val notificationId = Notifications.insertAndGetId {
                it[sender] = EntityID(userId, Users)
                it[type] = NotificationType.POST_REACTION
                it[recipient] = postAuthor
                it[relatedPost] = EntityID(postId, Posts)
            }
            pushNotification(postAuthor, notificationId.value)
        }
    }

    override fun notifyAboutRepost(userId: UUID, repostId: UUID) {
        transaction {
            val repostEntity = PostEntity.findById(repostId) ?: throw PostNotFoundException()
            val repostAuthor = repostEntity.authorId!!
            if (repostAuthor == userId) return@transaction
            if (isIgnored(userId, repostAuthor)) return@transaction
            if (!isNotificationEnabled(userId) { it.notifyAboutReposts }) return@transaction

            val notificationId = Notifications.insertAndGetId {
                it[type] = NotificationType.REPOST
                it[recipient] = userId
                it[sender] = repostAuthor
                it[relatedPost] = EntityID(repostId, Posts)
            }
            pushNotification(userId, notificationId.value)
        }
    }

    override fun notifyAboutCommentRepost(userId: UUID, repostId: UUID) {
        transaction {
            val repostEntity = PostEntity.findById(repostId) ?: throw PostNotFoundException()
            val repostAuthor = repostEntity.authorId!!
            if (repostAuthor == userId) return@transaction
            if (isIgnored(userId, repostAuthor)) return@transaction
            if (!isNotificationEnabled(userId) { it.notifyAboutReposts }) return@transaction

            val notificationId = Notifications.insertAndGetId {
                it[type] = NotificationType.COMMENT_REPOST
                it[recipient] = userId
                it[sender] = repostAuthor
                it[relatedPost] = EntityID(repostId, Posts)
            }
            pushNotification(userId, notificationId.value)
        }
    }

    override fun notifyAboutCommentReaction(commentId: UUID) {
        transaction {
            val commentAuthor = CommentEntity.findById(commentId)?.authorId ?: return@transaction
            if (!isNotificationEnabled(commentAuthor) { it.notifyAboutCommentReactions }) return@transaction

            val notificationId = Notifications.insertAndGetId {
                it[type] = NotificationType.COMMENT_REACTION
                it[recipient] = commentAuthor
                it[relatedComment] = EntityID(commentId, Comments)
            }
            pushNotification(commentAuthor, notificationId.value)
        }
    }

    override fun notifyAboutPostMention(userId: UUID, postId: UUID, mentionLogin: String) {
        transaction {
            val postEntity = PostEntity.findById(postId) ?: return@transaction
            if (postEntity.authorId != userId) throw WrongUserException()

            val mentionedUser = DiaryEntity.find { Diaries.login eq mentionLogin }.singleOrNull()?.owner ?: return@transaction
            val mentionedUserId = mentionedUser.value
            if (mentionedUserId == userId) return@transaction
            if (isIgnored(userId, mentionedUserId)) return@transaction
            if (!isNotificationEnabled(mentionedUserId) { it.notifyAboutMentions }) return@transaction

            val notificationId = Notifications.insertAndGetId {
                it[type] = NotificationType.POST_MENTION
                it[sender] = EntityID(userId, Users)
                it[recipient] = mentionedUserId
                it[relatedPost] = postEntity.id
            }
            pushNotification(mentionedUserId, notificationId.value)
        }
    }

    override fun notifyAboutCommentMention(userId: UUID, commentId: UUID, mentionLogin: String) {
        transaction {
            val commentEntity = CommentEntity.findById(commentId) ?: return@transaction
            if (commentEntity.authorId != userId) throw WrongUserException()

            val mentionedUser = DiaryEntity.find { Diaries.login eq mentionLogin }.singleOrNull()?.owner ?: return@transaction
            val mentionedUserId = mentionedUser.value
            if (mentionedUserId == userId) return@transaction
            if (isIgnored(userId, mentionedUserId)) return@transaction
            if (!isNotificationEnabled(mentionedUserId) { it.notifyAboutMentions }) return@transaction

            val notificationId = Notifications.insertAndGetId {
                it[type] = NotificationType.COMMENT_MENTION
                it[sender] = EntityID(userId, Users)
                it[recipient] = mentionedUserId
                it[relatedPost] = commentEntity.postId
            }
            pushNotification(mentionedUserId, notificationId.value)
        }
    }

    private fun toNotificationDto(row: ResultRow): NotificationDto {
        val isRead = row[Notifications.isRead]
        val createdAt = row[Notifications.createdAt]
        val relatedPost = row[Notifications.relatedPost]?.let { PostEntity[it] }
        val postUri = relatedPost?.uri
        val diaryLogin = relatedPost?.diaryId?.let { DiaryEntity.findById(it)?.login }

        return when (row[Notifications.type]) {
            NotificationType.NEW_POST -> NotificationDto.NewPost(
                id = row[Notifications.id].value,
                diaryLogin = diaryLogin!!,
                postUri = postUri!!,
                isRead = isRead,
                createdAt = createdAt,
            )
            NotificationType.COMMENT -> NotificationDto.Comment(
                id = row[Notifications.id].value,
                diaryLogin = diaryLogin!!,
                postUri = postUri!!,
                isRead = isRead,
                createdAt = createdAt,
            )
            NotificationType.COMMENT_REPLY -> NotificationDto.CommentReply(
                id = row[Notifications.id].value,
                diaryLogin = diaryLogin!!,
                postUri = postUri!!,
                isRead = isRead,
                createdAt = createdAt,
            )
            NotificationType.POST_REACTION -> NotificationDto.PostReaction(
                id = row[Notifications.id].value,
                diaryLogin = diaryLogin!!,
                postUri = postUri!!,
                isRead = isRead,
                createdAt = createdAt,
            )
            NotificationType.COMMENT_REACTION -> NotificationDto.CommentReaction(
                id = row[Notifications.id].value,
                diaryLogin = diaryLogin!!,
                postUri = postUri!!,
                isRead = isRead,
                createdAt = createdAt,
            )
            NotificationType.POST_MENTION -> NotificationDto.PostMention(
                id = row[Notifications.id].value,
                diaryLogin = diaryLogin!!,
                postUri = postUri!!,
                isRead = isRead,
                createdAt = createdAt,
            )
            NotificationType.COMMENT_MENTION -> NotificationDto.CommentMention(
                id = row[Notifications.id].value,
                diaryLogin = diaryLogin!!,
                postUri = postUri!!,
                isRead = isRead,
                createdAt = createdAt,
            )
            NotificationType.REPOST -> {
                val reposterDiary = DiaryEntity.find { Diaries.owner eq row[Notifications.sender] }.single()
                NotificationDto.Repost(
                    id = row[Notifications.id].value,
                    diaryLogin = reposterDiary.login,
                    postUri = postUri!!,
                    isRead = isRead,
                    createdAt = createdAt,
                )
            }
            NotificationType.COMMENT_REPOST -> {
                val reposterDiary = DiaryEntity.find { Diaries.owner eq row[Notifications.sender] }.single()
                NotificationDto.CommentRepost(
                    id = row[Notifications.id].value,
                    diaryLogin = reposterDiary.login,
                    postUri = postUri!!,
                    isRead = isRead,
                    createdAt = createdAt,
                )
            }
            NotificationType.FRIEND_REQUEST -> {
                val requestId = row[Notifications.relatedRequest]?.value ?: throw IllegalStateException("Friend request notification without request ID")
                val senderDiary = DiaryEntity.find { Diaries.owner eq row[Notifications.sender] }.single()
                NotificationDto.FriendRequest(
                    id = row[Notifications.id].value,
                    senderLogin = senderDiary.login,
                    requestId = requestId,
                    isRead = isRead,
                    createdAt = createdAt,
                )
            }
            NotificationType.PRIVATE_MESSAGE -> {
                val dialogId = row[Notifications.relatedDialog]?.value ?: throw IllegalStateException("Private message notification without dialog ID")
                val senderDiary = DiaryEntity.find { Diaries.owner eq row[Notifications.sender] }.single()
                NotificationDto.PrivateMessage(
                    id = row[Notifications.id].value,
                    senderLogin = senderDiary.login,
                    dialogId = dialogId,
                    isRead = isRead,
                    createdAt = createdAt,
                )
            }
        }
    }

    private fun toNotificationDto(notification: NotificationEntity): NotificationDto {
        val isRead = notification.isRead
        val createdAt = notification.createdAt
        val relatedPost = notification.relatedPost
        val postUri = relatedPost?.uri
        val diaryLogin = relatedPost?.diaryId?.let { DiaryEntity.findById(it)?.login }

        return when (notification.type) {
            NotificationType.NEW_POST -> NotificationDto.NewPost(
                id = notification.id.value,
                diaryLogin = diaryLogin!!,
                postUri = postUri!!,
                isRead = isRead,
                createdAt = createdAt,
            )
            NotificationType.COMMENT -> NotificationDto.Comment(
                id = notification.id.value,
                diaryLogin = diaryLogin!!,
                postUri = postUri!!,
                isRead = isRead,
                createdAt = createdAt,
            )
            NotificationType.COMMENT_REPLY -> NotificationDto.CommentReply(
                id = notification.id.value,
                diaryLogin = diaryLogin!!,
                postUri = postUri!!,
                isRead = isRead,
                createdAt = createdAt,
            )
            NotificationType.POST_REACTION -> NotificationDto.PostReaction(
                id = notification.id.value,
                diaryLogin = diaryLogin!!,
                postUri = postUri!!,
                isRead = isRead,
                createdAt = createdAt,
            )
            NotificationType.COMMENT_REACTION -> NotificationDto.CommentReaction(
                id = notification.id.value,
                diaryLogin = diaryLogin!!,
                postUri = postUri!!,
                isRead = isRead,
                createdAt = createdAt,
            )
            NotificationType.POST_MENTION -> NotificationDto.PostMention(
                id = notification.id.value,
                diaryLogin = diaryLogin!!,
                postUri = postUri!!,
                isRead = isRead,
                createdAt = createdAt,
            )
            NotificationType.COMMENT_MENTION -> NotificationDto.CommentMention(
                id = notification.id.value,
                diaryLogin = diaryLogin!!,
                postUri = postUri!!,
                isRead = isRead,
                createdAt = createdAt,
            )
            NotificationType.REPOST -> {
                val reposterDiary = DiaryEntity.find { Diaries.owner eq notification.sender.id }.single()
                NotificationDto.Repost(
                    id = notification.id.value,
                    diaryLogin = reposterDiary.login,
                    postUri = postUri!!,
                    isRead = isRead,
                    createdAt = createdAt,
                )
            }
            NotificationType.COMMENT_REPOST -> {
                val reposterDiary = DiaryEntity.find { Diaries.owner eq notification.sender.id }.single()
                NotificationDto.CommentRepost(
                    id = notification.id.value,
                    diaryLogin = reposterDiary.login,
                    postUri = postUri!!,
                    isRead = isRead,
                    createdAt = createdAt,
                )
            }
            NotificationType.FRIEND_REQUEST -> {
                val requestId = notification.relatedRequest?.id?.value ?: throw IllegalStateException("Friend request notification without request ID")
                val senderDiary = DiaryEntity.find { Diaries.owner eq notification.sender.id }.single()
                NotificationDto.FriendRequest(
                    id = notification.id.value,
                    senderLogin = senderDiary.login,
                    requestId = requestId,
                    isRead = isRead,
                    createdAt = createdAt,
                )
            }
            NotificationType.PRIVATE_MESSAGE -> {
                val senderDiary = DiaryEntity.find { Diaries.owner eq notification.sender.id }.single()
                NotificationDto.PrivateMessage(
                    id = notification.id.value,
                    senderLogin = senderDiary.login,
                    dialogId = notification.relatedDialog!!.id.value,
                    isRead = isRead,
                    createdAt = createdAt,
                )
            }
        }
    }

    /**
     * Helper to push a notification via WebSocket after inserting it.
     * Must be called inside a transaction.
     */
    private fun pushNotification(recipientId: UUID, notificationId: UUID) {
        val notification = NotificationEntity.findById(notificationId) ?: return
        val dto = toNotificationDto(notification)
        notificationWebSocketService.sendNotification(recipientId, dto)
    }

    override fun notifyAboutPrivateMessage(recipientId: UUID, dialogId: UUID) {
        transaction {
            val dialog = DialogEntity.findById(dialogId) ?: throw IllegalArgumentException("Dialog not found")
            val senderId = if (dialog.user1.id.value == recipientId) dialog.user2.id.value else dialog.user1.id.value

            if (isIgnored(senderId, recipientId)) return@transaction
            if (!isNotificationEnabled(recipientId) { it.notifyAboutPrivateMessages }) return@transaction

            val existingUnreadNotification = Notifications
                .select {
                    (Notifications.recipient eq recipientId) and
                    (Notifications.relatedDialog eq dialogId) and
                    (Notifications.type eq NotificationType.PRIVATE_MESSAGE) and
                    (Notifications.isRead eq false)
                }
                .firstOrNull()

            if (existingUnreadNotification == null) {
                val notificationId = Notifications.insertAndGetId {
                    it[type] = NotificationType.PRIVATE_MESSAGE
                    it[sender] = senderId
                    it[recipient] = recipientId
                    it[relatedDialog] = dialog.id
                }
                pushNotification(recipientId, notificationId.value)
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
            val senderId = senderDiary.owner.value

            if (isIgnored(senderId, recipientId)) return@transaction
            if (!isNotificationEnabled(recipientId) { it.notifyAboutFriendRequests }) return@transaction

            val notificationId = Notifications.insertAndGetId {
                it[type] = NotificationType.FRIEND_REQUEST
                it[sender] = senderDiary.owner
                it[recipient] = recipientId
                it[relatedRequest] = requestId
            }
            pushNotification(recipientId, notificationId.value)
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
                it[relatedRequest] = null
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
