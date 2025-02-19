package fi.lipp.blog.service

import fi.lipp.blog.data.DialogDto
import fi.lipp.blog.data.MessageDto
import fi.lipp.blog.model.Page
import fi.lipp.blog.model.Pageable
import fi.lipp.blog.model.exceptions.DialogAlreadyHiddenException
import fi.lipp.blog.model.exceptions.DialogNotFoundException
import fi.lipp.blog.model.exceptions.MessageReadException
import fi.lipp.blog.model.exceptions.MessageUpdateException
import fi.lipp.blog.model.exceptions.NotDialogParticipantException
import fi.lipp.blog.model.exceptions.NotMessageSenderException
import fi.lipp.blog.model.exceptions.UserNotFoundException
import java.util.UUID
import kotlin.jvm.Throws

interface DialogService {
    /**
     * Get all dialogs for the current user
     * @param userId ID of the current user
     * @return List of dialogs
     */
    fun getDialogs(userId: UUID, pageable: Pageable): Page<DialogDto.View>

    /**
     * Get messages in a dialog
     * @param userId ID of the current user
     * @param dialogId ID of the dialog
     * @throws DialogNotFoundException if dialog doesn't exist
     * @throws NotDialogParticipantException if user is not a participant of the dialog
     * @return List of messages
     */
    @Throws(DialogNotFoundException::class, NotDialogParticipantException::class)
    fun getMessages(userId: UUID, dialogId: UUID, pageable: Pageable): Page<MessageDto.View>

    /**
     * Get messages between the current user and another user identified by login
     * @param userId ID of the current user
     * @param userLogin Login of the other user
     * @param pageable Pagination parameters
     * @throws UserNotFoundException if user with given login doesn't exist
     * @return List of messages between the users
     */
    @Throws(UserNotFoundException::class)
    fun getMessages(userId: UUID, userLogin: String, pageable: Pageable): Page<MessageDto.View>

    /**
     * Send a message to another user
     * @param userId ID of the current user (sender)
     * @param receiverLogin Login of the message recipient
     * @param message Message to send
     * @throws UserNotFoundException if receiver doesn't exist
     */
    fun sendMessage(userId: UUID, receiverLogin: String, message: MessageDto.Create)

    /**
     * Delete an unread message from a dialog
     * @param userId ID of the current user
     * @param messageId ID of the message to delete
     * @throws DialogNotFoundException if dialog doesn't exist
     * @throws NotDialogParticipantException if user is not a participant of the dialog
     * @throws MessageReadException if attempting to delete a read message
     * @throws NotMessageSenderException if user is not the sender of the message
     */
    @Throws(DialogNotFoundException::class, NotDialogParticipantException::class, MessageReadException::class, NotMessageSenderException::class)
    fun deleteMessage(userId: UUID, messageId: UUID)

    /**
     * Update a message in a dialog. Message can only be updated if:
     * 1. It is unread, OR
     * 2. It was sent less than 30 minutes ago
     * @param userId ID of the current user
     * @param messageId ID of the message to update
     * @param update Update data containing new content
     * @throws DialogNotFoundException if dialog doesn't exist
     * @throws NotDialogParticipantException if user is not a participant of the dialog
     * @throws NotMessageSenderException if user is not the sender of the message
     * @throws MessageUpdateException if message cannot be updated (read and older than 30 minutes)
     * @return Updated message
     */
    @Throws(DialogNotFoundException::class, NotDialogParticipantException::class, NotMessageSenderException::class, MessageUpdateException::class)
    fun updateMessage(userId: UUID, messageId: UUID, update: MessageDto.Update)

    /**
     * Hides a dialog for the specified user. Hidden dialogs won't appear in the user's dialog list.
     *
     * @param userId ID of the user who wants to hide the dialog
     * @param dialogId ID of the dialog to hide
     * @throws DialogNotFoundException if dialog with given ID doesn't exist
     * @throws NotDialogParticipantException if user is not a participant in the dialog
     * @throws DialogAlreadyHiddenException if dialog is already hidden for this user
     */
    @Throws(DialogNotFoundException::class, NotDialogParticipantException::class, DialogAlreadyHiddenException::class)
    fun hideDialog(userId: UUID, dialogId: UUID)
}
