package fi.lipp.blog

import fi.lipp.blog.data.*
import fi.lipp.blog.domain.*
import fi.lipp.blog.model.Page
import fi.lipp.blog.model.Pageable
import org.jetbrains.exposed.sql.SortOrder
import fi.lipp.blog.model.exceptions.*
import fi.lipp.blog.repository.*
import fi.lipp.blog.repository.DiaryType
import fi.lipp.blog.service.AccessGroupService
import fi.lipp.blog.service.NotificationService
import fi.lipp.blog.service.PostService
import fi.lipp.blog.service.UserService
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.Assert.assertThrows
import org.koin.java.KoinJavaComponent.inject
import java.util.*
import kotlin.test.*

class SubscriptionTests : UnitTestBase() {
    private lateinit var user1Id: UUID
    private lateinit var user2Id: UUID
    private val notificationService: NotificationService by inject(NotificationService::class.java)

    @Test
    fun `test get subscribed posts`() {
        transaction {
            val (id1, id2) = signUsersUp()
            user1Id = id1
            user2Id = id2

            // Create a post from user2
            val diary = DiaryEntity.find { Diaries.owner eq user2Id }.first()
            val post = postService.addPost(user2Id, PostDto.Create(
                uri = "test-post",
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
                commentReactionGroupId = diary.defaultCommentGroup.value
            ))

            // User1 subscribes to the post
            notificationService.subscribeToComments(user1Id, post.id)

            // Get subscribed posts for User1
            val subscribedPosts = postService.getSubscribedPosts(user1Id, Pageable(1, 10, SortOrder.DESC))
            assertEquals(1, subscribedPosts.content.size)
            assertEquals("Test Post", subscribedPosts.content[0].title)
            assertEquals(testUser2.login, subscribedPosts.content[0].authorLogin)

            rollback()
        }
    }
}
