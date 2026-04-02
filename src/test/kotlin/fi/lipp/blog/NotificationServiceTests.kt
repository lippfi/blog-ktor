package fi.lipp.blog

import fi.lipp.blog.data.*
import fi.lipp.blog.domain.*
import fi.lipp.blog.model.exceptions.NotificationNotFoundException
import fi.lipp.blog.repository.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.Assert.assertThrows
import java.util.*
import kotlin.test.*

class NotificationServiceTests : UnitTestBase() {

    private fun createPost(userId: UUID): PostDto.View {
        val diary = transaction { DiaryEntity.find { Diaries.owner eq userId }.first() }
        return postService.addPost(userId, PostDto.Create(
            uri = "test-post-${UUID.randomUUID()}",
            avatar = "",
            title = "Test Post",
            text = "Test Content",
            isPreface = false,
            isEncrypted = false,
            classes = "",
            tags = emptySet(),
            readGroupId = diary.defaultReadGroup.value,
            commentGroupId = diary.defaultCommentGroup.value,
            reactionGroupId = diary.defaultReadGroup.value,
            commentReactionGroupId = diary.defaultCommentGroup.value,
        ))
    }

    private fun createComment(userId: UUID, postId: UUID, parentCommentId: UUID? = null): CommentDto.View {
        return postService.addComment(userId, CommentDto.Create(
            postId = postId,
            avatar = "",
            text = "Test comment ${UUID.randomUUID()}",
            parentCommentId = parentCommentId,
        ))
    }

    @Test
    fun `get notification settings returns defaults for new user`() {
        val (user1Id, _) = signUsersUp()
        val settings = notificationService.getNotificationSettings(user1Id)

        assertTrue(settings.notifyAboutComments)
        assertTrue(settings.notifyAboutReplies)
        assertTrue(settings.notifyAboutPostReactions)
        assertTrue(settings.notifyAboutCommentReactions)
        assertTrue(settings.notifyAboutPrivateMessages)
        assertTrue(settings.notifyAboutMentions)
        assertTrue(settings.notifyAboutNewPosts)
        assertTrue(settings.notifyAboutFriendRequests)
        assertTrue(settings.notifyAboutReposts)
    }

    @Test
    fun `notifications list is empty for new user`() {
        val (user1Id, _) = signUsersUp()
        val notifications = notificationService.getNotifications(user1Id)
        assertTrue(notifications.isEmpty())
    }

    @Test
    fun `friend request creates notification`() {
        val (user1Id, user2Id) = signUsersUp()

        userService.sendFriendRequest(user1Id, FriendRequestDto.Create(
            toUser = testUser2.login,
            message = "Let's be friends",
            label = null
        ))

        val notifications = notificationService.getNotifications(user2Id)
        assertEquals(1, notifications.size)
        val notification = notifications[0]
        assertTrue(notification is NotificationDto.FriendRequest)
        assertEquals(testUser.login, notification.senderLogin)
        assertFalse(notification.isRead)
    }

    @Test
    fun `friend request notification respects ignore list`() {
        val (user1Id, user2Id) = signUsersUp()

        // user2 ignores user1
        userService.ignoreUser(user2Id, testUser.login)

        userService.sendFriendRequest(user1Id, FriendRequestDto.Create(
            toUser = testUser2.login,
            message = "Let's be friends",
            label = null
        ))

        val notifications = notificationService.getNotifications(user2Id)
        assertTrue(notifications.isEmpty())
    }

    @Test
    fun `friend request notification respects disabled setting`() {
        val (user1Id, user2Id) = signUsersUp()

        // Disable friend request notifications for user2
        userService.updateNotificationSettings(user2Id, fi.lipp.blog.data.NotificationSettings(
            notifyAboutComments = true,
            notifyAboutReplies = true,
            notifyAboutPostReactions = true,
            notifyAboutCommentReactions = true,
            notifyAboutPrivateMessages = true,
            notifyAboutMentions = true,
            notifyAboutNewPosts = true,
            notifyAboutFriendRequests = false,
            notifyAboutReposts = true,
        ))

        userService.sendFriendRequest(user1Id, FriendRequestDto.Create(
            toUser = testUser2.login,
            message = "Let's be friends",
            label = null
        ))

        val notifications = notificationService.getNotifications(user2Id)
        assertTrue(notifications.isEmpty())
    }

    @Test
    fun `mark notification as read`() {
        val (user1Id, user2Id) = signUsersUp()

        userService.sendFriendRequest(user1Id, FriendRequestDto.Create(
            toUser = testUser2.login,
            message = "Let's be friends",
            label = null
        ))

        val notifications = notificationService.getNotifications(user2Id)
        assertEquals(1, notifications.size)
        assertFalse(notifications[0].isRead)

        notificationService.markAsRead(user2Id, notifications[0].id)

        val updated = notificationService.getNotifications(user2Id)
        assertEquals(1, updated.size)
        assertTrue(updated[0].isRead)
    }

    @Test
    fun `mark all notifications as read`() {
        val users = signUsersUp(3)
        val user1Id = users[0].first
        val user2Id = users[1].first
        val user2Login = users[1].second
        val user3Id = users[2].first
        val user3Login = users[2].second

        // Send friend requests from user2 and user3 to user1
        userService.sendFriendRequest(user2Id, FriendRequestDto.Create(
            toUser = testUser.login,
            message = "Hello",
            label = null
        ))
        userService.sendFriendRequest(user3Id, FriendRequestDto.Create(
            toUser = testUser.login,
            message = "Hi",
            label = null
        ))

        val notifications = notificationService.getNotifications(user1Id)
        assertEquals(2, notifications.size)
        assertTrue(notifications.all { !it.isRead })

        notificationService.markAllAsRead(user1Id)

        val updated = notificationService.getNotifications(user1Id)
        assertEquals(2, updated.size)
        assertTrue(updated.all { it.isRead })
    }

    @Test
    fun `delete single notification`() {
        val (user1Id, user2Id) = signUsersUp()

        userService.sendFriendRequest(user1Id, FriendRequestDto.Create(
            toUser = testUser2.login,
            message = "Hello",
            label = null
        ))

        val notifications = notificationService.getNotifications(user2Id)
        assertEquals(1, notifications.size)

        notificationService.deleteNotification(user2Id, notifications[0].id)

        val updated = notificationService.getNotifications(user2Id)
        assertTrue(updated.isEmpty())
    }

    @Test
    fun `delete all notifications`() {
        val users = signUsersUp(3)
        val user1Id = users[0].first
        val user2Id = users[1].first
        val user3Id = users[2].first

        userService.sendFriendRequest(user2Id, FriendRequestDto.Create(
            toUser = testUser.login,
            message = "Hello",
            label = null
        ))
        userService.sendFriendRequest(user3Id, FriendRequestDto.Create(
            toUser = testUser.login,
            message = "Hi",
            label = null
        ))

        val notifications = notificationService.getNotifications(user1Id)
        assertEquals(2, notifications.size)

        notificationService.deleteAllNotifications(user1Id)

        val updated = notificationService.getNotifications(user1Id)
        assertTrue(updated.isEmpty())
    }

    @Test
    fun `delete notification of another user throws exception`() {
        val (user1Id, user2Id) = signUsersUp()

        userService.sendFriendRequest(user1Id, FriendRequestDto.Create(
            toUser = testUser2.login,
            message = "Hello",
            label = null
        ))

        val notifications = notificationService.getNotifications(user2Id)
        assertEquals(1, notifications.size)

        assertThrows(NotificationNotFoundException::class.java) {
            notificationService.deleteNotification(user1Id, notifications[0].id)
        }
    }

    @Test
    fun `comment notification is created for subscribed users`() {
        val (user1Id, user2Id) = signUsersUp()

        val post = createPost(user1Id)
        notificationService.subscribeToComments(user2Id, post.id)

        createComment(user1Id, post.id)

        // user1 comments on their own post - no notification for user1, notification for user2
        val user2Notifications = notificationService.getNotifications(user2Id)
        assertEquals(1, user2Notifications.size)
        assertTrue(user2Notifications[0] is NotificationDto.Comment)
    }

    @Test
    fun `comment notification not created for ignored users`() {
        val (user1Id, user2Id) = signUsersUp()

        val post = createPost(user1Id)
        notificationService.subscribeToComments(user2Id, post.id)

        // user2 ignores user1
        userService.ignoreUser(user2Id, testUser.login)

        createComment(user1Id, post.id)

        val user2Notifications = notificationService.getNotifications(user2Id)
        assertTrue(user2Notifications.isEmpty())
    }

    @Test
    fun `comment notification not created when setting disabled`() {
        val (user1Id, user2Id) = signUsersUp()

        val post = createPost(user1Id)
        notificationService.subscribeToComments(user2Id, post.id)

        userService.updateNotificationSettings(user2Id, fi.lipp.blog.data.NotificationSettings(
            notifyAboutComments = false,
            notifyAboutReplies = true,
            notifyAboutPostReactions = true,
            notifyAboutCommentReactions = true,
            notifyAboutPrivateMessages = true,
            notifyAboutMentions = true,
            notifyAboutNewPosts = true,
            notifyAboutFriendRequests = true,
            notifyAboutReposts = true,
        ))

        createComment(user1Id, post.id)

        val user2Notifications = notificationService.getNotifications(user2Id)
        assertTrue(user2Notifications.isEmpty())
    }

    @Test
    fun `notifications from ignored users are filtered from get`() {
        val users = signUsersUp(3)
        val user1Id = users[0].first
        val user2Id = users[1].first
        val user2Login = users[1].second
        val user3Id = users[2].first

        // user2 and user3 send friend requests to user1
        userService.sendFriendRequest(user2Id, FriendRequestDto.Create(
            toUser = testUser.login,
            message = "Hello from user2",
            label = null
        ))
        userService.sendFriendRequest(user3Id, FriendRequestDto.Create(
            toUser = testUser.login,
            message = "Hello from user3",
            label = null
        ))

        // Verify both notifications exist
        assertEquals(2, notificationService.getNotifications(user1Id).size)

        // user1 ignores user2
        userService.ignoreUser(user1Id, user2Login)

        // Now only user3's notification should be visible
        val filtered = notificationService.getNotifications(user1Id)
        assertEquals(1, filtered.size)
        assertTrue(filtered[0] is NotificationDto.FriendRequest)
    }

    @Test
    fun `read all post notifications marks them as read`() {
        val (user1Id, user2Id) = signUsersUp()

        val post = createPost(user1Id)
        notificationService.subscribeToComments(user2Id, post.id)

        createComment(user1Id, post.id)
        createComment(user1Id, post.id)

        val notifications = notificationService.getNotifications(user2Id)
        assertEquals(2, notifications.size)
        assertTrue(notifications.all { !it.isRead })

        notificationService.readAllPostNotifications(user2Id, post.id)

        val updated = notificationService.getNotifications(user2Id)
        assertEquals(2, updated.size)
        assertTrue(updated.all { it.isRead })
    }

    @Test
    fun `notification has createdAt timestamp`() {
        val (user1Id, user2Id) = signUsersUp()

        userService.sendFriendRequest(user1Id, FriendRequestDto.Create(
            toUser = testUser2.login,
            message = "Hello",
            label = null
        ))

        val notifications = notificationService.getNotifications(user2Id)
        assertEquals(1, notifications.size)
        assertNow(notifications[0].createdAt)
    }

    @Test
    fun `get notification by id works`() {
        val (user1Id, user2Id) = signUsersUp()

        userService.sendFriendRequest(user1Id, FriendRequestDto.Create(
            toUser = testUser2.login,
            message = "Hello",
            label = null
        ))

        val notifications = notificationService.getNotifications(user2Id)
        val notification = notificationService.getNotification(user2Id, notifications[0].id)
        assertEquals(notifications[0].id, notification.id)
    }

    @Test
    fun `get notification by id for wrong user throws exception`() {
        val (user1Id, user2Id) = signUsersUp()

        userService.sendFriendRequest(user1Id, FriendRequestDto.Create(
            toUser = testUser2.login,
            message = "Hello",
            label = null
        ))

        val notifications = notificationService.getNotifications(user2Id)

        assertThrows(NotificationNotFoundException::class.java) {
            notificationService.getNotification(user1Id, notifications[0].id)
        }
    }

    @Test
    fun `mention notification sent to mentioned user not sender`() {
        val (user1Id, user2Id) = signUsersUp()

        val post = createPost(user1Id)

        notificationService.notifyAboutPostMention(user1Id, post.id, testUser2.login)

        // Notification should be for user2 (the mentioned user), not user1 (the sender)
        val user1Notifications = notificationService.getNotifications(user1Id)
        assertTrue(user1Notifications.isEmpty())

        val user2Notifications = notificationService.getNotifications(user2Id)
        assertEquals(1, user2Notifications.size)
        assertTrue(user2Notifications[0] is NotificationDto.PostMention)
    }

    @Test
    fun `mention notification respects ignore list`() {
        val (user1Id, user2Id) = signUsersUp()

        // user2 ignores user1
        userService.ignoreUser(user2Id, testUser.login)

        val post = createPost(user1Id)
        notificationService.notifyAboutPostMention(user1Id, post.id, testUser2.login)

        val user2Notifications = notificationService.getNotifications(user2Id)
        assertTrue(user2Notifications.isEmpty())
    }

    @Test
    fun `mention notification respects disabled setting`() {
        val (user1Id, user2Id) = signUsersUp()

        userService.updateNotificationSettings(user2Id, fi.lipp.blog.data.NotificationSettings(
            notifyAboutComments = true,
            notifyAboutReplies = true,
            notifyAboutPostReactions = true,
            notifyAboutCommentReactions = true,
            notifyAboutPrivateMessages = true,
            notifyAboutMentions = false,
            notifyAboutNewPosts = true,
            notifyAboutFriendRequests = true,
            notifyAboutReposts = true,
        ))

        val post = createPost(user1Id)
        notificationService.notifyAboutPostMention(user1Id, post.id, testUser2.login)

        val user2Notifications = notificationService.getNotifications(user2Id)
        assertTrue(user2Notifications.isEmpty())
    }

    @Test
    fun `self-mention does not create notification`() {
        val (user1Id, _) = signUsersUp()

        val post = createPost(user1Id)
        notificationService.notifyAboutPostMention(user1Id, post.id, testUser.login)

        val notifications = notificationService.getNotifications(user1Id)
        assertTrue(notifications.isEmpty())
    }

    @Test
    fun `comment notification not created when comment depends on ignored user`() {
        val users = signUsersUp(3)
        val user1Id = users[0].first
        val user1Login = users[0].second
        val user2Id = users[1].first
        val user2Login = users[1].second
        val user3Id = users[2].first

        val post = createPost(user1Id)

        // user3 subscribes to the post
        notificationService.subscribeToComments(user3Id, post.id)

        // user2 comments on the post
        val comment1 = createComment(user2Id, post.id)

        // user3 ignores user2
        userService.ignoreUser(user3Id, user2Login)

        // user1 replies to user2's comment — this comment depends on user2
        createComment(user1Id, post.id, comment1.id)

        // user3 should NOT get notification because the reply depends on user2 (ignored)
        val user3Notifications = notificationService.getNotifications(user3Id)
        // There may be 1 notification from user2's original comment (before ignore), but none from user1's reply
        val replyNotifications = user3Notifications.filter { it.type == fi.lipp.blog.domain.NotificationType.COMMENT }
        // The reply that depends on user2 should not generate a notification for user3
        assertTrue(replyNotifications.none { notification ->
            // Check that none of the comment notifications are for the reply
            transaction {
                val notifEntity = fi.lipp.blog.domain.NotificationEntity.findById(notification.id)
                notifEntity?.relatedComment?.id?.value != comment1.id
            }
        })
    }

    @Test
    fun `comment notification created when comment does not depend on ignored user`() {
        val users = signUsersUp(3)
        val user1Id = users[0].first
        val user2Id = users[1].first
        val user2Login = users[1].second
        val user3Id = users[2].first

        val post = createPost(user1Id)

        // user3 subscribes to the post
        notificationService.subscribeToComments(user3Id, post.id)

        // user3 ignores user2
        userService.ignoreUser(user3Id, user2Login)

        // user1 comments (no dependency on user2)
        createComment(user1Id, post.id)

        // user3 should get the notification since user1's comment doesn't depend on user2
        val user3Notifications = notificationService.getNotifications(user3Id)
        assertEquals(1, user3Notifications.size)
        assertTrue(user3Notifications[0] is NotificationDto.Comment)
    }
}
