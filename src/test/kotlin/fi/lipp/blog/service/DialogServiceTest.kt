package fi.lipp.blog.service

import fi.lipp.blog.UnitTestBase
import fi.lipp.blog.data.DialogDto
import fi.lipp.blog.data.Language
import fi.lipp.blog.data.MessageDto
import fi.lipp.blog.data.UserDto
import fi.lipp.blog.domain.MessageEntity
import fi.lipp.blog.model.Pageable
import fi.lipp.blog.model.exceptions.*
import fi.lipp.blog.repository.Messages
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.transactions.transaction
import kotlin.time.Duration.Companion.minutes
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DialogServiceTest : UnitTestBase() {
    @Test
    fun `test send and get message`() {
        // Create first user
        userService.signUp(testUser, "")
        val user1 = findUserByLogin(testUser.login)!!

        // Create second user with invite code
        val inviteCode = userService.generateInviteCode(user1.id)
        userService.signUp(testUser2, inviteCode)
        val user2 = findUserByLogin(testUser2.login)!!

        // Send message
        val messageText = "Hello, how are you?"
        dialogService.sendMessage(user1.id, user2.login, MessageDto.Create(avatarUri = "test-avatar.jpg", content = messageText))

        // Get dialogs and verify
        val dialogs = dialogService.getDialogs(user1.id, Pageable(0, 10, SortOrder.DESC))
        assertEquals(1, dialogs.content.size)

        val dialog = dialogs.content.first()
        assertEquals(user2.login, dialog.user.login)

        // Get messages and verify
        val messages = dialogService.getMessages(user1.id, dialog.id, Pageable(0, 10, SortOrder.DESC))
        assertEquals(1, messages.content.size)
        assertEquals(messageText, messages.content.first().content)
    }

    @Test
    fun `test get messages by user login`() {
        // Create first user
        userService.signUp(testUser, "")
        val user1 = findUserByLogin(testUser.login)!!

        // Create second user with invite code
        val inviteCode = userService.generateInviteCode(user1.id)
        userService.signUp(testUser2, inviteCode)
        val user2 = findUserByLogin(testUser2.login)!!

        // Send messages
        val messageText1 = "Hello!"
        val messageText2 = "How are you?"
        dialogService.sendMessage(user1.id, user2.login, MessageDto.Create(avatarUri = "test-avatar.jpg", content = messageText1))
        dialogService.sendMessage(user2.id, user1.login, MessageDto.Create(avatarUri = "test-avatar.jpg", content = messageText2))

        // Get messages by user login and verify
        val messages = dialogService.getMessages(user1.id, user2.login, Pageable(0, 10, SortOrder.DESC))
        assertEquals(2, messages.content.size)
        assertEquals(messageText2, messages.content[0].content)
        assertEquals(messageText1, messages.content[1].content)
    }

    @Test
    fun `test error - get messages with non-existent user`() {
        // Create user
        userService.signUp(testUser, "")
        val user = findUserByLogin(testUser.login)!!

        // Try to get messages with non-existent user
        assertFailsWith<UserNotFoundException> {
            dialogService.getMessages(user.id, "non-existent-user", Pageable(0, 10, SortOrder.DESC))
        }
    }

    @Test
    fun `test update message`() {
        // Create first user
        userService.signUp(testUser, "")
        val user1 = findUserByLogin(testUser.login)!!

        // Create second user with invite code
        val inviteCode = userService.generateInviteCode(user1.id)
        userService.signUp(testUser2, inviteCode)
        val user2 = findUserByLogin(testUser2.login)!!

        val initialText = "Initial message"
        dialogService.sendMessage(user1.id, user2.login, MessageDto.Create(avatarUri = "test-avatar.jpg", content = initialText))

        // Get message ID
        val dialog = dialogService.getDialogs(user1.id, Pageable(0, 10, SortOrder.DESC)).content.first()
        val message = dialogService.getMessages(user1.id, dialog.id, Pageable(0, 10, SortOrder.DESC)).content.first()

        // Update message
        val updatedText = "Updated message"
        dialogService.updateMessage(user1.id, message.id, MessageDto.Update(content = updatedText))

        // Verify update
        val updatedMessage = dialogService.getMessages(user1.id, dialog.id, Pageable(0, 10, SortOrder.DESC)).content.first()
        assertEquals(updatedText, updatedMessage.content)
        assertEquals(user1.login, updatedMessage.sender.login)
    }

    @Test
    fun `test delete message`() {
        // Create first user
        userService.signUp(testUser, "")
        val user1 = findUserByLogin(testUser.login)!!

        // Create second user with invite code
        val inviteCode = userService.generateInviteCode(user1.id)
        userService.signUp(testUser2, inviteCode)
        val user2 = findUserByLogin(testUser2.login)!!

        dialogService.sendMessage(user1.id, user2.login, MessageDto.Create(avatarUri = "test-avatar.jpg", content = "Message to delete"))

        // Get message ID
        val dialog = dialogService.getDialogs(user1.id, Pageable(0, 10, SortOrder.DESC)).content.first()
        val message = dialogService.getMessages(user1.id, dialog.id, Pageable(0, 10, SortOrder.DESC)).content.first()
        assertEquals(user1.login, message.sender.login)

        // Delete message
        dialogService.deleteMessage(user1.id, message.id)

        // Verify deletion
        val messages = dialogService.getMessages(user1.id, dialog.id, Pageable(0, 10, SortOrder.DESC))
        assertEquals(0, messages.content.size)
    }

    @Test
    fun `test hide dialog`() {
        // Create first user
        userService.signUp(testUser, "")
        val user1 = findUserByLogin(testUser.login)!!

        // Create second user with invite code
        val inviteCode = userService.generateInviteCode(user1.id)
        userService.signUp(testUser2, inviteCode)
        val user2 = findUserByLogin(testUser2.login)!!

        dialogService.sendMessage(user1.id, user2.login, MessageDto.Create(avatarUri = "test-avatar.jpg", content = "Test message"))

        // Get dialog and verify it exists
        val dialog = dialogService.getDialogs(user1.id, Pageable(0, 10, SortOrder.DESC)).content.first()
        assertEquals(user2.login, dialog.user.login)

        // Hide dialog
        dialogService.hideDialog(user1.id, dialog.id)

        // Verify dialog is hidden
        val dialogs = dialogService.getDialogs(user1.id, Pageable(0, 10, SortOrder.DESC))
        assertEquals(0, dialogs.content.size)
    }

    @Test
    fun `test error - non participant cannot access dialog`() {
        // Create first user
        userService.signUp(testUser, "")
        val user1 = findUserByLogin(testUser.login)!!

        // Create second user with invite code from first user
        val inviteCode1 = userService.generateInviteCode(user1.id)
        userService.signUp(testUser2, inviteCode1)
        val user2 = findUserByLogin(testUser2.login)!!

        // Create third user with invite code from first user
        val inviteCode2 = userService.generateInviteCode(user1.id)
        userService.signUp(UserDto.Registration(
            login = "thirduser",
            email = "third@mail.com",
            password = "password123",
            nickname = "third_user",
            language = Language.EN,
            timezone = "Europe/Moscow"
        ), inviteCode2)
        val user3 = findUserByLogin("thirduser")!!

        // Create dialog between user1 and user2
        dialogService.sendMessage(user1.id, user2.login, MessageDto.Create(avatarUri = "test-avatar.jpg", content = "Test message"))
        val dialog = dialogService.getDialogs(user1.id, Pageable(0, 10, SortOrder.DESC)).content.first()
        assertEquals(user2.login, dialog.user.login)

        // Verify user3 cannot access the dialog
        assertFailsWith<NotDialogParticipantException> {
            dialogService.getMessages(user3.id, dialog.id, Pageable(0, 10, SortOrder.DESC))
        }
    }

    @Test
    fun `test error - cannot update others message`() {
        // Create first user
        userService.signUp(testUser, "")
        val user1 = findUserByLogin(testUser.login)!!

        // Create second user with invite code
        val inviteCode = userService.generateInviteCode(user1.id)
        userService.signUp(testUser2, inviteCode)
        val user2 = findUserByLogin(testUser2.login)!!

        dialogService.sendMessage(user1.id, user2.login, MessageDto.Create(avatarUri = "test-avatar.jpg", content = "Original message"))

        // Get message ID
        val dialog = dialogService.getDialogs(user1.id, Pageable(0, 10, SortOrder.DESC)).content.first()
        val message = dialogService.getMessages(user1.id, dialog.id, Pageable(0, 10, SortOrder.DESC)).content.first()
        assertEquals(user1.login, message.sender.login)

        // Try to update message as user2
        assertFailsWith<NotMessageSenderException> {
            dialogService.updateMessage(user2.id, message.id, MessageDto.Update(content = "Attempted update"))
        }
    }

    @Test
    fun `test error - cannot send message to non-existent user`() {
        // Create user
        userService.signUp(testUser, "")
        val user = findUserByLogin(testUser.login)!!

        // Try to send message to non-existent user
        assertFailsWith<DiaryNotFoundException> {
            dialogService.sendMessage(user.id, "non-existent-user", MessageDto.Create(avatarUri = "test-avatar.jpg", content = "Test message"))
        }
    }

    @Test
    fun `test message read status behavior`() {
        // Create first user
        userService.signUp(testUser, "")
        val user1 = findUserByLogin(testUser.login)!!

        // Create second user with invite code
        val inviteCode = userService.generateInviteCode(user1.id)
        userService.signUp(testUser2, inviteCode)
        val user2 = findUserByLogin(testUser2.login)!!

        // Send message from user1 to user2
        val messageText = "Test message"
        dialogService.sendMessage(user1.id, user2.login, MessageDto.Create(avatarUri = "test-avatar.jpg", content = messageText))

        // Get dialog and message IDs
        val dialog = dialogService.getDialogs(user1.id, Pageable(0, 10, SortOrder.DESC)).content.first()
        val message = dialogService.getMessages(user1.id, dialog.id, Pageable(0, 10, SortOrder.DESC)).content.first()

        // Verify message is not read when viewed by sender
        assertEquals(false, message.isRead)

        // Get message as recipient (user2) which should mark it as read
        val messageAsRecipient = dialogService.getMessages(user2.id, dialog.id, Pageable(0, 10, SortOrder.DESC)).content.first()
        assertEquals(true, messageAsRecipient.isRead)

        // Verify message is now read when viewed by sender
        val messageAfterRead = dialogService.getMessages(user1.id, dialog.id, Pageable(0, 10, SortOrder.DESC)).content.first()
        assertEquals(true, messageAfterRead.isRead)

        // Try to delete read message
        assertFailsWith<MessageReadException> {
            dialogService.deleteMessage(user1.id, message.id)
        }

        // Try to update read message (should succeed if less than 30 minutes old)
        dialogService.updateMessage(user1.id, message.id, MessageDto.Update(content = "Updated message"))

        // Set message timestamp to more than 30 minutes ago
        transaction {
            val messageEntity = MessageEntity.findById(EntityID(message.id, Messages))!!
            messageEntity.timestamp = Clock.System.now()
                .minus(31.minutes)
                .toLocalDateTime(TimeZone.currentSystemDefault())
        }

        // Try to update old read message
        assertFailsWith<MessageUpdateException> {
            dialogService.updateMessage(user1.id, message.id, MessageDto.Update(content = "Another update"))
        }
    }

    @Test
    fun `test multiple messages in dialog with ordering`() {
        // Create first user
        userService.signUp(testUser, "")
        val user1 = findUserByLogin(testUser.login)!!

        // Create second user with invite code
        val inviteCode = userService.generateInviteCode(user1.id)
        userService.signUp(testUser2, inviteCode)
        val user2 = findUserByLogin(testUser2.login)!!

        // Send multiple messages from both users
        val messages = listOf(
            Triple(user1.id, user2.login, "First message from user1"),
            Triple(user2.id, user1.login, "First message from user2"),
            Triple(user1.id, user2.login, "Second message from user1"),
            Triple(user2.id, user1.login, "Second message from user2"),
            Triple(user1.id, user2.login, "Third message from user1")
        )

        messages.forEach { (senderId, receiverLogin, content) ->
            dialogService.sendMessage(senderId, receiverLogin, MessageDto.Create(avatarUri = "test-avatar.jpg", content = content))
        }

        // Get dialog
        val dialog = dialogService.getDialogs(user1.id, Pageable(0, 10, SortOrder.DESC)).content.first()

        // Get messages and verify order (newest first)
        val dialogMessages = dialogService.getMessages(user1.id, dialog.id, Pageable(0, 10, SortOrder.DESC)).content
        assertEquals(5, dialogMessages.size)
        assertEquals("Third message from user1", dialogMessages[0].content)
        assertEquals("Second message from user2", dialogMessages[1].content)
        assertEquals("Second message from user1", dialogMessages[2].content)
        assertEquals("First message from user2", dialogMessages[3].content)
        assertEquals("First message from user1", dialogMessages[4].content)

        // Verify sender information
        dialogMessages.filter { it.content.contains("from user1") }.forEach { message ->
            assertEquals(user1.login, message.sender.login)
        }
        dialogMessages.filter { it.content.contains("from user2") }.forEach { message ->
            assertEquals(user2.login, message.sender.login)
        }

        // Verify that only messages from the other user are marked as read after viewing
        val messagesAfterRead = dialogService.getMessages(user1.id, dialog.id, Pageable(0, 10, SortOrder.DESC)).content
        assertTrue(messagesAfterRead.filter { it.sender.login == user2.login }.all { it.isRead })
        assertTrue(messagesAfterRead.filter { it.sender.login == user1.login }.none { it.isRead })
    }

    @Test
    fun `test multiple dialogs with different users`() {
        // Create first user (main user)
        userService.signUp(testUser, "")
        val user1 = findUserByLogin(testUser.login)!!

        // Create second user
        val inviteCode1 = userService.generateInviteCode(user1.id)
        userService.signUp(testUser2, inviteCode1)
        val user2 = findUserByLogin(testUser2.login)!!

        // Create third user
        val inviteCode2 = userService.generateInviteCode(user1.id)
        userService.signUp(UserDto.Registration(
            login = "thirduser",
            email = "third@mail.com",
            password = "password123",
            nickname = "third_user",
            language = Language.EN,
            timezone = "Europe/Moscow"
        ), inviteCode2)
        val user3 = findUserByLogin("thirduser")!!

        // Create dialogs by sending messages
        dialogService.sendMessage(user1.id, user2.login, MessageDto.Create(avatarUri = "test-avatar.jpg", content = "Message to user2"))
        dialogService.sendMessage(user1.id, user3.login, MessageDto.Create(avatarUri = "test-avatar.jpg", content = "Message to user3"))

        // Verify user has two dialogs
        var dialogs = dialogService.getDialogs(user1.id, Pageable(0, 10, SortOrder.DESC)).content
        assertEquals(2, dialogs.size)

        // Send a new message in the first dialog to change its order
        dialogService.sendMessage(user2.id, user1.login, MessageDto.Create(avatarUri = "test-avatar.jpg", content = "Reply from user2"))

        // Verify dialog order (newest first)
        dialogs = dialogService.getDialogs(user1.id, Pageable(0, 10, SortOrder.DESC)).content
        assertEquals(user2.login, dialogs[0].user.login) // Dialog with user2 should be first (newest)
        assertEquals(user3.login, dialogs[1].user.login) // Dialog with user3 should be second

        // Hide dialog with user2
        dialogService.hideDialog(user1.id, dialogs[0].id)

        // Verify only dialog with user3 is visible
        dialogs = dialogService.getDialogs(user1.id, Pageable(0, 10, SortOrder.DESC)).content
        assertEquals(1, dialogs.size)
        assertEquals(user3.login, dialogs[0].user.login)
    }

    @Test
    fun `test dialog state after message deletion`() {
        // Create first user
        userService.signUp(testUser, "")
        val user1 = findUserByLogin(testUser.login)!!

        // Create second user with invite code
        val inviteCode = userService.generateInviteCode(user1.id)
        userService.signUp(testUser2, inviteCode)
        val user2 = findUserByLogin(testUser2.login)!!

        // Send multiple messages
        dialogService.sendMessage(user1.id, user2.login, MessageDto.Create(avatarUri = "test-avatar.jpg", content = "First message"))
        dialogService.sendMessage(user2.id, user1.login, MessageDto.Create(avatarUri = "test-avatar.jpg", content = "Second message"))
        dialogService.sendMessage(user1.id, user2.login, MessageDto.Create(avatarUri = "test-avatar.jpg", content = "Third message"))

        // Get dialog and verify initial state
        var dialog = dialogService.getDialogs(user1.id, Pageable(0, 10, SortOrder.DESC)).content.first()
        var messages = dialogService.getMessages(user1.id, dialog.id, Pageable(0, 10, SortOrder.DESC)).content
        assertEquals(3, messages.size)
        assertEquals("Third message", dialog.lastMessage?.content)

        // Delete the last message
        dialogService.deleteMessage(user1.id, messages[0].id)

        // Verify dialog state after last message deletion
        dialog = dialogService.getDialogs(user1.id, Pageable(0, 10, SortOrder.DESC)).content.first()
        messages = dialogService.getMessages(user1.id, dialog.id, Pageable(0, 10, SortOrder.DESC)).content
        assertEquals(2, messages.size)
        assertEquals("Second message", dialog.lastMessage?.content)

        // Verify that read messages cannot be deleted
        assertFailsWith<MessageReadException> {
            dialogService.deleteMessage(user2.id, messages[0].id)
        }

        // Delete remaining unread messages (messages from user1)
        messages.filter { it.sender.login == user1.login }.forEach { message ->
            dialogService.deleteMessage(user1.id, message.id)
        }

        // Verify final dialog state
        dialog = dialogService.getDialogs(user1.id, Pageable(0, 10, SortOrder.DESC)).content.first()
        messages = dialogService.getMessages(user1.id, dialog.id, Pageable(0, 10, SortOrder.DESC)).content

        // Only the read message from user2 should remain
        assertEquals(1, messages.size)
        assertEquals(user2.login, messages[0].sender.login)
        assertEquals("Second message", messages[0].content)
        assertTrue(messages[0].isRead)

        // Last message in dialog should match the remaining message
        assertEquals(messages[0].content, dialog.lastMessage?.content)
    }

    @Test
    fun `test message pagination`() {
        // Create first user
        userService.signUp(testUser, "")
        val user1 = findUserByLogin(testUser.login)!!

        // Create second user with invite code
        val inviteCode = userService.generateInviteCode(user1.id)
        userService.signUp(testUser2, inviteCode)
        val user2 = findUserByLogin(testUser2.login)!!

        // Send multiple messages to create enough data for pagination
        val messageCount = 15
        for (i in 1..messageCount) {
            dialogService.sendMessage(user1.id, user2.login, MessageDto.Create(
                avatarUri = "test-avatar.jpg",
                content = "Message $i"
            ))
        }

        // Get dialog
        val dialog = dialogService.getDialogs(user1.id, Pageable(0, 10, SortOrder.DESC)).content.first()

        // Test first page (newest messages)
        val firstPage = dialogService.getMessages(user1.id, dialog.id, Pageable(0, 5, SortOrder.DESC))
        assertEquals(5, firstPage.content.size)
        for (i in 0..4) {
            assertEquals("Message ${messageCount - i}", firstPage.content[i].content)
        }

        // Test middle page
        val middlePage = dialogService.getMessages(user1.id, dialog.id, Pageable(1, 5, SortOrder.DESC))
        assertEquals(5, middlePage.content.size)
        for (i in 0..4) {
            assertEquals("Message ${messageCount - 5 - i}", middlePage.content[i].content)
        }

        // Test last page (oldest messages)
        val lastPage = dialogService.getMessages(user1.id, dialog.id, Pageable(2, 5, SortOrder.DESC))
        assertEquals(5, lastPage.content.size)
        for (i in 0..4) {
            assertEquals("Message ${5 - i}", lastPage.content[i].content)
        }

        // Test page with custom size
        val customSizePage = dialogService.getMessages(user1.id, dialog.id, Pageable(0, 7, SortOrder.DESC))
        assertEquals(7, customSizePage.content.size)
        for (i in 0..6) {
            assertEquals("Message ${messageCount - i}", customSizePage.content[i].content)
        }
    }

    @Test
    fun `test error - cannot hide already hidden dialog`() {
        // Create first user
        userService.signUp(testUser, "")
        val user1 = findUserByLogin(testUser.login)!!

        // Create second user with invite code
        val inviteCode = userService.generateInviteCode(user1.id)
        userService.signUp(testUser2, inviteCode)
        val user2 = findUserByLogin(testUser2.login)!!

        // Create a dialog by sending a message
        dialogService.sendMessage(user1.id, user2.login, MessageDto.Create(avatarUri = "test-avatar.jpg", content = "Test message"))
        val dialog = dialogService.getDialogs(user1.id, Pageable(0, 10, SortOrder.DESC)).content.first()

        // Hide dialog
        dialogService.hideDialog(user1.id, dialog.id)

        // Try to hide dialog again
        assertFailsWith<DialogAlreadyHiddenException> {
            dialogService.hideDialog(user1.id, dialog.id)
        }

        // Verify dialog is still hidden (not visible in the list)
        val dialogs = dialogService.getDialogs(user1.id, Pageable(0, 10, SortOrder.DESC))
        assertEquals(0, dialogs.content.size)
    }
}
