package fi.lipp.blog.service.implementations

import fi.lipp.blog.data.DialogDto
import fi.lipp.blog.data.MessageDto
import fi.lipp.blog.data.UserDto
import fi.lipp.blog.domain.DialogEntity
import fi.lipp.blog.domain.DiaryEntity
import fi.lipp.blog.domain.HiddenDialogEntity
import fi.lipp.blog.domain.MessageEntity
import fi.lipp.blog.domain.UserEntity
import fi.lipp.blog.repository.Diaries
import fi.lipp.blog.model.exceptions.DialogAlreadyHiddenException
import fi.lipp.blog.model.exceptions.DialogNotFoundException
import fi.lipp.blog.model.exceptions.MessageReadException
import fi.lipp.blog.model.exceptions.MessageUpdateException
import fi.lipp.blog.model.exceptions.NotDialogParticipantException
import fi.lipp.blog.model.exceptions.NotMessageSenderException
import fi.lipp.blog.model.Page
import fi.lipp.blog.model.Pageable
import fi.lipp.blog.repository.Dialogs
import fi.lipp.blog.repository.HiddenDialogs
import fi.lipp.blog.repository.Messages
import fi.lipp.blog.repository.Users
import fi.lipp.blog.service.DialogService
import fi.lipp.blog.service.NotificationService
import fi.lipp.blog.service.UserService
import fi.lipp.blog.service.implementations.UserServiceImpl
import fi.lipp.blog.model.exceptions.UserNotFoundException
import kotlinx.datetime.toKotlinLocalDateTime
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration.Companion.minutes
import java.util.*

class DialogServiceImpl(
    private val userService: UserService,
    private val notificationService: NotificationService
) : DialogService {


    override fun getDialogs(userId: UUID, pageable: Pageable): Page<DialogDto.View> = transaction {
        val query = DialogEntity.find {
            ((Dialogs.user1 eq userId) or (Dialogs.user2 eq userId)) and
            not(exists(HiddenDialogs.slice(HiddenDialogs.id).select {
                (HiddenDialogs.dialog eq Dialogs.id) and (HiddenDialogs.user eq userId)
            }))
        }

        val totalCount = query.count()
        val totalPages = (totalCount + pageable.size - 1) / pageable.size

        val dialogs = query
            .orderBy(Dialogs.updatedAt to pageable.direction)
            .limit(pageable.size, offset = (pageable.page * pageable.size).toLong())
            .map { dialog ->
                getDialogView(dialog.id.value, userId)
            }

        Page(
            content = dialogs,
            currentPage = pageable.page,
            totalPages = totalPages.toInt()
        )
    }

    override fun getMessages(userId: UUID, dialogId: UUID, pageable: Pageable): Page<MessageDto.View> {
        return transaction {
            val dialog = DialogEntity.findById(EntityID(dialogId, Dialogs)) ?: throw DialogNotFoundException()

            if (dialog.user1.id.value != userId && dialog.user2.id.value != userId) {
                throw NotDialogParticipantException()
            }

            val query = MessageEntity.find { Messages.dialog eq dialogId }

            val totalCount = query.count()
            val totalPages = (totalCount + pageable.size - 1) / pageable.size

            val messages = query
                .orderBy(Messages.timestamp to pageable.direction)
                .limit(pageable.size, offset = (pageable.page * pageable.size).toLong())
                .map { message ->
                    if (message.sender.id.value != userId && !message.isRead) {
                        message.isRead = true
                    }

                    MessageDto.View(
                        id = message.id.value,
                        dialogId = message.dialog.id.value,
                        sender = userService.getUserView(message.sender.id.value).applyAvatar(message.avatarUri),
                        content = message.content,
                        timestamp = message.timestamp,
                        isRead = message.isRead,
                    )
                }

            Page(
                content = messages,
                currentPage = pageable.page,
                totalPages = totalPages.toInt()
            )
        }
    }

    private fun UserDto.View.applyAvatar(avatarUri: String?): UserDto.View {
        if (avatarUri == null) return this
        return this.copy(avatarUri = avatarUri)
    }

    override fun sendMessage(userId: UUID, receiverLogin: String, message: MessageDto.Create) {
        transaction {
            // This will throw UserNotFoundException if user doesn't exist
            userService.getUserInfo(receiverLogin)

            // Find receiver entity by diary login
            val diary = DiaryEntity.find { Diaries.login eq receiverLogin }.firstOrNull()
                ?: throw UserNotFoundException()
            val receiver = UserEntity.findById(diary.owner)
                ?: throw UserNotFoundException()
            val receiverId = receiver.id.value

            // Check if dialog already exists
            val existingDialog = DialogEntity.find {
                (Dialogs.user1 eq userId and (Dialogs.user2 eq receiverId)) or
                        (Dialogs.user1 eq receiverId and (Dialogs.user2 eq userId))
            }.firstOrNull()

            val dialog = existingDialog ?: DialogEntity.new {
                user1 = UserEntity[EntityID(userId, Users)]
                user2 = receiver
                createdAt = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
                updatedAt = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
            }

            // Update dialog's updatedAt timestamp
            dialog.updatedAt = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())

            val hasUnreadMessagesFromSender = MessageEntity.find {
                (Messages.dialog eq dialog.id) and
                        (Messages.sender eq EntityID(userId, Users)) and
                        (Messages.isRead eq false)
            }.count() > 0

            MessageEntity.new {
                this.dialog = dialog
                sender = UserEntity[EntityID(userId, Users)]
                content = message.content
                avatarUri = message.avatarUri
                timestamp = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
                isRead = false
            }

            if (!hasUnreadMessagesFromSender) {
                notificationService.notifyAboutPrivateMessage(receiverId, dialog.id.value)
            }
        }
    }

    override fun deleteMessage(userId: UUID, messageId: UUID) = transaction {
        // Find the message
        val message = MessageEntity.findById(EntityID(messageId, Messages))
            ?: throw DialogNotFoundException()

        // Check if user is a participant in the dialog
        val dialog = message.dialog
        if (dialog.user1.id.value != userId && dialog.user2.id.value != userId) {
            throw NotDialogParticipantException()
        }

        // Check if user is the sender of the message
        if (message.sender.id.value != userId) {
            throw NotMessageSenderException()
        }

        // Check if message is unread
        if (message.isRead) {
            throw MessageReadException()
        }

        // Delete the message
        message.delete()

        // Check if there are any remaining unread messages in the dialog
        val hasUnreadMessages = MessageEntity.find {
            (Messages.sender eq EntityID(userId, Users)) and
            (Messages.dialog eq dialog.id) and
            (Messages.isRead eq false)
        }.count() > 0

        // If there are no more unread messages, remove the notification for both participants
        if (!hasUnreadMessages) {
            val otherUserId = if (dialog.user1.id.value == userId) dialog.user2.id.value else dialog.user1.id.value
            notificationService.removePrivateMessageNotification(otherUserId, dialog.id.value)
        }
    }

    override fun updateMessage(userId: UUID, messageId: UUID, update: MessageDto.Update) {
        transaction {
            val message = MessageEntity.findById(EntityID(messageId, Messages))
                ?: throw DialogNotFoundException()

            if (message.sender.id.value != userId) {
                throw NotMessageSenderException()
            }

            val thirtyMinutesAgo = (Clock.System.now() - 30.minutes)
                .toLocalDateTime(TimeZone.currentSystemDefault())

            if (message.isRead && message.timestamp < thirtyMinutesAgo) {
                throw MessageUpdateException()
            }

            message.content = update.content

            MessageDto.View(
                id = message.id.value,
                dialogId = message.dialog.id.value,
                sender = userService.getUserView(userId).applyAvatar(message.avatarUri),
                content = message.content,
                timestamp = message.timestamp,
                isRead = message.isRead,
            )
        }
    }

    override fun hideDialog(userId: UUID, dialogId: UUID) {
        transaction {
            val dialog = DialogEntity.findById(EntityID(dialogId, Dialogs))
                ?: throw DialogNotFoundException()

            if (dialog.user1.id.value != userId && dialog.user2.id.value != userId) {
                throw NotDialogParticipantException()
            }

            if (HiddenDialogEntity.find {
                    (HiddenDialogs.dialog eq dialogId) and (HiddenDialogs.user eq userId)
                }.count() > 0) {
                throw DialogAlreadyHiddenException()
            }

            HiddenDialogEntity.new {
                this.dialog = dialog
                this.user = UserEntity[EntityID(userId, Users)]
            }
        }
    }

    private fun getDialogView(dialogId: UUID, userId: UUID): DialogDto.View = transaction {
        val dialog = DialogEntity.findById(EntityID(dialogId, Dialogs))
            ?: throw DialogNotFoundException()

        // Determine the conversation partner
        val partnerId = if (dialog.user1.id.value == userId) dialog.user2.id.value else dialog.user1.id.value

        val lastMessage = MessageEntity.find { Messages.dialog eq dialogId }
            .orderBy(Messages.timestamp to SortOrder.DESC)
            .limit(1)
            .map { message ->
                MessageDto.View(
                    id = message.id.value,
                    dialogId = message.dialog.id.value,
                    sender = userService.getUserView(message.sender.id.value).applyAvatar(message.avatarUri),
                    content = message.content,
                    timestamp = message.timestamp,
                    isRead = message.isRead
                )
            }.firstOrNull()

        // Get current user's login for comparison
        val currentUserLogin = userService.getUserView(userId).login

        // Determine if we should show the last message and if dialog is unread
        val isUnread = lastMessage != null && !lastMessage.isRead && lastMessage.sender.login != currentUserLogin
        val visibleLastMessage = if (lastMessage != null && (lastMessage.sender.login == currentUserLogin || lastMessage.isRead)) {
            lastMessage
        } else {
            null
        }

        DialogDto.View(
            id = dialog.id.value,
            user = userService.getUserView(partnerId),
            lastMessage = visibleLastMessage,
            isUnread = isUnread
        )
    }
}
