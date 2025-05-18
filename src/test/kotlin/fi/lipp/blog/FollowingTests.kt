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
import fi.lipp.blog.service.PostService
import fi.lipp.blog.service.UserService
import kotlinx.datetime.toKotlinLocalDateTime
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.Assert.assertThrows
import org.koin.java.KoinJavaComponent.inject
import java.util.*
import kotlin.test.*

class FollowingTests : UnitTestBase() {
    private val userService: UserService by inject(UserService::class.java)
    private val postService: PostService by inject(PostService::class.java)
    private val accessGroupService: AccessGroupService by inject(AccessGroupService::class.java)
    private lateinit var user1Id: UUID
    private lateinit var user2Id: UUID
    private lateinit var user3Id: UUID

    @BeforeTest
    fun setUp() {
        cleanDatabase()
    }

    @Test
    fun `test successful follow`() {
        transaction {
            // Create users
            val (id1, id2) = signUsersUp()
            user1Id = id1
            user2Id = id2

            // Test following
            userService.followUser(user1Id, testUser2.login)

            val following = userService.getFollowing(user1Id)
            assertEquals(1, following.size)
            assertEquals(testUser2.login, following[0].login)

            val followers = userService.getFollowers(user2Id)
            assertEquals(1, followers.size)
            assertEquals(testUser.login, followers[0].login)

            rollback()
        }
    }

    @Test
    fun `test follow nonexistent user`() {
        transaction {
            val (id1, _) = signUsersUp()
            user1Id = id1

            assertThrows(UserNotFoundException::class.java) {
                userService.followUser(user1Id, "nonexistent")
            }

            rollback()
        }
    }

    @Test
    fun `test follow already followed user`() {
        transaction {
            val (id1, id2) = signUsersUp()
            user1Id = id1
            user2Id = id2

            userService.followUser(user1Id, testUser2.login)

            assertThrows(AlreadyFollowingException::class.java) {
                userService.followUser(user1Id, testUser2.login)
            }

            rollback()
        }
    }

    @Test
    fun `test successful unfollow`() {
        transaction {
            val (id1, id2) = signUsersUp()
            user1Id = id1
            user2Id = id2

            userService.followUser(user1Id, testUser2.login)
            userService.unfollowUser(user1Id, testUser2.login)

            val following = userService.getFollowing(user1Id)
            assertEquals(0, following.size)

            val followers = userService.getFollowers(user2Id)
            assertEquals(0, followers.size)

            rollback()
        }
    }

    @Test
    fun `test unfollow not followed user`() {
        transaction {
            val (id1, id2) = signUsersUp()
            user1Id = id1
            user2Id = id2

            assertThrows(NotFollowingException::class.java) {
                userService.unfollowUser(user1Id, testUser2.login)
            }

            rollback()
        }
    }

    @Test
    fun `test get followed posts`() {
        transaction {
            val (id1, id2) = signUsersUp()
            user1Id = id1
            user2Id = id2

            // Create a post from user2
            val diary = DiaryEntity.find { Diaries.owner eq user2Id }.first()
            postService.addPost(user2Id, PostDto.Create(
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

            // User1 follows User2
            userService.followUser(user1Id, testUser2.login)

            // Get followed posts for User1
            val followedPosts = postService.getFollowedPosts(user1Id, Pageable(1, 10, SortOrder.DESC))
            assertEquals(1, followedPosts.content.size)
            assertEquals("Test Post", followedPosts.content[0].title)
            assertEquals(testUser2.login, followedPosts.content[0].authorLogin)

            rollback()
        }
    }

}
