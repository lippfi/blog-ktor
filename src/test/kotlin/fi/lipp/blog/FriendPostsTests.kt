package fi.lipp.blog

import fi.lipp.blog.data.*
import fi.lipp.blog.domain.*
import fi.lipp.blog.model.Pageable
import org.jetbrains.exposed.sql.SortOrder
import fi.lipp.blog.repository.*
import fi.lipp.blog.service.PostService
import fi.lipp.blog.service.UserService
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.*
import kotlin.test.*

class FriendPostsTests : UnitTestBase() {
    private lateinit var user1Id: UUID
    private lateinit var user2Id: UUID

    @BeforeTest
    fun setUp() {
        cleanDatabase()
    }

    @Test
    fun `test get friends posts`() {
        transaction {
            // Create users
            val (id1, id2) = signUsersUp()
            user1Id = id1
            user2Id = id2

            // Create a post from user2
            val diary = DiaryEntity.find { Diaries.owner eq user2Id }.first()
            postService.addPost(user2Id, PostDto.Create(
                uri = "friend-post",
                avatar = "",
                title = "Friend Post",
                text = "Friend Content",
                isPreface = false,
                isEncrypted = false,
                classes = "",
                tags = emptySet(),
                readGroupId = diary.defaultReadGroup.value,
                commentGroupId = diary.defaultCommentGroup.value,
                reactionGroupId = diary.defaultReadGroup.value,
                commentReactionGroupId = diary.defaultCommentGroup.value
            ))

            // Initially, they are not friends, so no posts should be returned
            var friendsPosts = postService.getFriendsPosts(user1Id, Pageable(0, 10, SortOrder.DESC))
            assertEquals(0, friendsPosts.content.size)

            // Establish friendship
            userService.sendFriendRequest(user1Id, FriendRequestDto.Create(testUser2.login, "Hi", null))
            val requestId = userService.getReceivedFriendRequests(user2Id).first().id
            userService.acceptFriendRequest(user2Id, requestId, null)

            // Now they are friends, user1 should see user2's post
            friendsPosts = postService.getFriendsPosts(user1Id, Pageable(0, 10, SortOrder.DESC))
            assertEquals(1, friendsPosts.content.size)
            assertEquals("Friend Post", friendsPosts.content[0].title)
            assertEquals(testUser2.login, friendsPosts.content[0].authorLogin)

            // User2 should also see User1's posts (if any)
            friendsPosts = postService.getFriendsPosts(user2Id, Pageable(0, 10, SortOrder.DESC))
            assertEquals(0, friendsPosts.content.size) // User1 hasn't posted yet

            rollback()
        }
    }
}
