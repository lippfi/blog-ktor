package fi.lipp.blog

import fi.lipp.blog.data.*
import fi.lipp.blog.domain.ReactionEntity
import fi.lipp.blog.model.exceptions.*
import fi.lipp.blog.repository.Reactions
import fi.lipp.blog.service.NotificationService
import fi.lipp.blog.service.PostService
import fi.lipp.blog.service.ReactionService
import fi.lipp.blog.service.Viewer
import fi.lipp.blog.service.implementations.PostServiceImpl
import fi.lipp.blog.service.implementations.ReactionServiceImpl
import io.ktor.server.config.MapApplicationConfig
import org.jetbrains.exposed.sql.transactions.transaction
import org.mockito.kotlin.mock
import java.util.UUID
import kotlin.test.*

class ReactionServiceTests : UnitTestBase() {
    private lateinit var testUserRegistration: UserDto.Registration
    private lateinit var testUser: UserDto.FullProfileInfo
    private lateinit var testFile: BlogFile
    private lateinit var postService: PostService
    private lateinit var reactionService: ReactionService
    private val notificationService = mock<NotificationService>()

    // Helper method to find a reaction by name and get its id
    private fun getReactionIdByName(name: String): UUID {
        return transaction {
            val reactionEntity = ReactionEntity.find { Reactions.name eq name }.firstOrNull() ?: throw ReactionNotFoundException()
            reactionEntity.id.value
        }
    }

    @BeforeTest
    fun setUp() {
        transaction {
            testUserRegistration = UnitTestBase.testUser
            userService.signUp(testUserRegistration, "")
            testUser = findUserByLogin(testUserRegistration.login)!!
            testFile = storageService.storeReaction(testUser.id, FileUploadData(
                fullName = "reaction.png",
                inputStream = avatarFile1.inputStream()
            ))
            val config = MapApplicationConfig().apply {
                put("reactions.basic.size", "6")
                put("reactions.basic.0", "like")
                put("reactions.basic.1", "love")
                put("reactions.basic.2", "haha")
                put("reactions.basic.3", "wow")
                put("reactions.basic.4", "sad")
                put("reactions.basic.5", "angry")
            }
            reactionService = ReactionServiceImpl(storageService, groupService, notificationService, userService)
            postService = PostServiceImpl(groupService, storageService, reactionService, notificationService)
        }
    }

    private fun createTestPost(
        userId: UUID,
        uri: String = "test-post-${UUID.randomUUID()}",
        groupId: UUID = groupService.everyoneGroupUUID
    ): PostDto.View {
        return transaction {
            postService.addPost(userId, PostDto.Create(
                title = "Test Post",
                uri = uri,
                text = "Test content",
                tags = setOf(),
                readGroupId = groupId,
                commentGroupId = groupId,
                reactionGroupId = groupId,
                commentReactionGroupId = groupId,
                isPreface = false,
                isEncrypted = false,
                classes = "",
                avatar = ""
            ))
            postService.getPost(Viewer.Registered(userId), testUser.login, uri)
        }
    }

    @Test
    fun `test create reaction`() {
        val name = "like"
        val icon = FileUploadData(
            fullName = "reaction.png",
            inputStream = avatarFile1.inputStream()
        )

        val created = reactionService.createReaction(testUser.id, name, icon)
        assertNotNull(created)
        assertEquals(name, created.name)
    }

    @Test
    fun `test delete reaction`() {
        // Generate invite code and register second test user
        val inviteCode = userService.generateInviteCode(testUser.id)
        userService.signUp(testUser2, inviteCode)
        val user2 = findUserByLogin(testUser2.login)!!

        val name = "like"
        reactionService.createReaction(
            testUser.id,
            name,
            FileUploadData(
                fullName = "reaction.png",
                inputStream = avatarFile1.inputStream()
            )
        )

        // Another user should not be able to delete the reaction
        assertFailsWith<WrongUserException>("Should not allow deletion by non-creator") {
            reactionService.deleteReaction(user2.id, name)
        }

        // Original creator should be able to delete the reaction
        reactionService.deleteReaction(testUser.id, name)

        // Verify the reaction was deleted by trying to get all reactions
        val reactions = reactionService.getReactions()
        assertFalse(reactions.any { it.name == name })
    }

    @Test
    fun `test add reaction to post`() {
        val reactionName = "like"
        val reaction = reactionService.createReaction(
            testUser.id,
            reactionName,
            FileUploadData(
                fullName = "reaction.png",
                inputStream = avatarFile1.inputStream()
            )
        )

        val post = createTestPost(testUser.id)
        val viewer = Viewer.Registered(testUser.id)

        val reactionId = getReactionIdByName(reactionName)
        reactionService.addReaction(viewer, testUser.login, post.uri, reactionId)
        // Adding the same reaction again should not throw
        reactionService.addReaction(viewer, testUser.login, post.uri, reactionId)
    }

    @Test
    fun `test remove reaction from post`() {
        val reactionName = "like"
        val reaction = reactionService.createReaction(
            testUser.id,
            reactionName,
            FileUploadData(
                fullName = "reaction.png",
                inputStream = avatarFile1.inputStream()
            )
        )

        val post = createTestPost(testUser.id)
        val viewer = Viewer.Registered(testUser.id)

        val reactionId = getReactionIdByName(reactionName)
        reactionService.addReaction(viewer, testUser.login, post.uri, reactionId)
        reactionService.removeReaction(viewer, testUser.login, post.uri, reactionId)
        // Removing non-existent reaction should not throw
        reactionService.removeReaction(viewer, testUser.login, post.uri, reactionId)
    }

    @Test
    fun `test anonymous reactions`() {
        val reaction = reactionService.createReaction(
            testUser.id,
            "like",
            FileUploadData(
                fullName = "reaction.png",
                inputStream = avatarFile1.inputStream()
            )
        )

        val post = createTestPost(testUser.id)
        val viewer = Viewer.Anonymous("127.0.0.1", "test-fingerprint")

        reactionService.addReaction(viewer, testUser.login, post.uri, reaction.id)
        reactionService.removeReaction(viewer, testUser.login, post.uri, reaction.id)
    }

    @Test
    fun `test reaction name validation`() {
        // Test invalid names
        val invalidNames = listOf(
            "123like", // starts with number
            "like!", // special character
            "like space", // contains space
            "лайк", // non-English characters
            "" // empty string
        )

        for (invalidName in invalidNames) {
            assertFailsWith<IllegalArgumentException>("Name '$invalidName' should be invalid") {
                reactionService.createReaction(
                    testUser.id,
                    invalidName,
                    FileUploadData(
                        fullName = "reaction.png",
                        inputStream = avatarFile1.inputStream()
                    )
                )
            }
        }

        // Test valid names
        val validNames = listOf(
            "like",
            "like123",
            "like-123",
            "super-like"
        )

        for (validName in validNames) {
            // Valid name should not throw
            reactionService.createReaction(
                testUser.id,
                validName,
                FileUploadData(
                    fullName = "reaction.png",
                    inputStream = avatarFile1.inputStream()
                )
            )
        }
    }

    @Test
    fun `test reaction name uniqueness`() {
        val name = "like"
        val icon = FileUploadData(
            fullName = "reaction.png",
            inputStream = avatarFile1.inputStream()
        )

        reactionService.createReaction(testUser.id, name, icon)

        assertFailsWith<Exception>("Should not allow duplicate reaction names") {
            reactionService.createReaction(testUser.id, name, FileUploadData(
                fullName = "reaction2.png",
                inputStream = avatarFile2.inputStream()
            ))
        }
    }

    @Test
    fun `test reaction group permissions`() {
        // Create test users
        val inviteCode = userService.generateInviteCode(testUser.id)
        userService.signUp(testUser2, inviteCode)
        val user2 = findUserByLogin(testUser2.login)!!

        // Create a reaction
        val reaction = reactionService.createReaction(
            testUser.id,
            "like",
            FileUploadData(
                fullName = "reaction.png",
                inputStream = avatarFile1.inputStream()
            )
        )

        // Create posts with different reaction group permissions
        val everyonePost = createTestPost(testUser.id, "post-everyone", groupService.everyoneGroupUUID)
        val registeredPost = createTestPost(testUser.id, "post-registered", groupService.registeredGroupUUID)
        val privatePost = createTestPost(testUser.id, "post-private", groupService.privateGroupUUID)

        // Test everyone post
        reactionService.addReaction(Viewer.Anonymous("127.0.0.1", "test"), testUser.login, everyonePost.uri, reaction.id)
        reactionService.addReaction(Viewer.Registered(user2.id), testUser.login, everyonePost.uri, reaction.id)

        // Test registered users post
        assertFailsWith<WrongUserException>("Anonymous user should not be able to react to registered-only post") {
            reactionService.addReaction(Viewer.Anonymous("127.0.0.1", "test"), testUser.login, registeredPost.uri, reaction.id)
        }
        reactionService.addReaction(Viewer.Registered(user2.id), testUser.login, registeredPost.uri, reaction.id)

        // Test private post
        assertFailsWith<WrongUserException>("Anonymous user should not be able to react to private post") {
            reactionService.addReaction(Viewer.Anonymous("127.0.0.1", "test"), testUser.login, privatePost.uri, reaction.id)
        }
        assertFailsWith<WrongUserException>("Other user should not be able to react to private post") {
            reactionService.addReaction(Viewer.Registered(user2.id), testUser.login, privatePost.uri, reaction.id)
        }
        // Author should be able to react to their own private post
        reactionService.addReaction(Viewer.Registered(testUser.id), testUser.login, privatePost.uri, reaction.id)
    }

    @Test
    fun `test post reaction information`() {
        // Create test users
        val inviteCode = userService.generateInviteCode(testUser.id)
        userService.signUp(testUser2, inviteCode)
        val user2 = findUserByLogin(testUser2.login)!!

        // Create a reaction
        val reaction = reactionService.createReaction(
            testUser.id,
            "like",
            FileUploadData(
                fullName = "reaction.png",
                inputStream = avatarFile1.inputStream()
            )
        )

        // Create a post
        val post = createTestPost(testUser.id)

        // Add reactions from different users
        reactionService.addReaction(Viewer.Registered(testUser.id), testUser.login, post.uri, reaction.id)
        reactionService.addReaction(Viewer.Registered(user2.id), testUser.login, post.uri, reaction.id)
        reactionService.addReaction(Viewer.Anonymous("127.0.0.1", "test1"), testUser.login, post.uri, reaction.id)
        reactionService.addReaction(Viewer.Anonymous("127.0.0.2", "test2"), testUser.login, post.uri, reaction.id)

        // Get post and verify reaction information
        val updatedPost = postService.getPost(Viewer.Registered(testUser.id), testUser.login, post.uri)
        assertEquals(1, updatedPost.reactions.size)

        val reactionInfo = updatedPost.reactions[0]
        assertEquals(reaction.id, reactionInfo.id)
        assertEquals(reaction.name, reactionInfo.name)
        assertEquals(reaction.iconUri, reactionInfo.iconUri)
        assertEquals(4, reactionInfo.count)
        assertEquals(2, reactionInfo.anonymousCount)
        assertEquals(2, reactionInfo.userLogins.size)
        assertTrue(reactionInfo.userLogins.contains(testUser.login))
        assertTrue(reactionInfo.userLogins.contains(testUser2.login))
    }

    @Test
    fun `test search reactions by name`() {
        // Create test reactions
        val testReactions = listOf(
            "like",
            "superlike",
            "dislike",
            "heart"
        )

        testReactions.forEach { name ->
            reactionService.createReaction(
                testUser.id,
                name,
                FileUploadData(
                    fullName = "reaction.png",
                    inputStream = avatarFile1.inputStream()
                )
            )
        }

        // Test partial match with complete word
        val completeMatch = reactionService.searchReactionsByName("like")
        assertEquals(3, completeMatch.size)
        assertTrue(completeMatch.map { it.name }.containsAll(listOf("like", "superlike", "dislike")))

        // Test partial match at start
        val startMatch = reactionService.searchReactionsByName("super")
        assertEquals(1, startMatch.size)
        assertEquals("superlike", startMatch.first().name)

        // Test partial match in middle
        val middleMatch = reactionService.searchReactionsByName("lik")
        assertEquals(3, middleMatch.size)
        assertTrue(middleMatch.map { it.name }.containsAll(listOf("like", "superlike", "dislike")))

        // Test no match
        val noMatch = reactionService.searchReactionsByName("xyz")
        assertTrue(noMatch.isEmpty())
    }

    @Test
    fun `test get user recent reactions`() {
        // Create test reactions
        val testReactions = listOf("happy", "sad", "love")

        val createdReactions = testReactions.map { name ->
            reactionService.createReaction(
                testUser.id,
                name,
                FileUploadData(
                    fullName = "reaction.png",
                    inputStream = avatarFile1.inputStream()
                )
            )
        }

        // Create test posts
        val posts = (1..3).map { createTestPost(testUser.id) }

        // Add reactions in specific order to test timestamp-based ordering
        reactionService.addReaction(Viewer.Registered(testUser.id), posts[0].authorLogin, posts[0].uri, createdReactions[0].id)
        reactionService.addReaction(Viewer.Registered(testUser.id), posts[1].authorLogin, posts[1].uri, createdReactions[1].id)
        reactionService.addReaction(Viewer.Registered(testUser.id), posts[2].authorLogin, posts[2].uri, createdReactions[2].id)
        reactionService.addReaction(Viewer.Registered(testUser.id), posts[0].authorLogin, posts[0].uri, createdReactions[1].id)

        // Test getting recent reactions with default limit
        val recentReactions = reactionService.getUserRecentReactions(testUser.id)
        assertEquals(3, recentReactions.size)
        assertEquals(createdReactions[1].id, recentReactions[0].id) // Most recent (sad)
        assertEquals(createdReactions[2].id, recentReactions[1].id) // Second recent (love)
        assertEquals(createdReactions[0].id, recentReactions[2].id) // Least recent (happy)

        // Test with custom limit
        val limitedReactions = reactionService.getUserRecentReactions(testUser.id, 2)
        assertEquals(2, limitedReactions.size)
        assertEquals(createdReactions[1].id, limitedReactions[0].id)
        assertEquals(createdReactions[2].id, limitedReactions[1].id)
    }

    @Test
    fun `test get basic reactions`() {
        // Create all basic reactions
        val expectedBasicReactions = listOf("like", "love", "haha", "wow", "sad", "angry")
        expectedBasicReactions.forEach { name ->
            reactionService.createReaction(
                testUser.id,
                name,
                FileUploadData(
                    fullName = "reaction.png",
                    inputStream = avatarFile1.inputStream()
                )
            )
        }

        // Create a non-basic reaction
        reactionService.createReaction(
            testUser.id,
            "custom",
            FileUploadData(
                fullName = "reaction.png",
                inputStream = avatarFile1.inputStream()
            )
        )

        // Get basic reactions and verify
        val basicReactionsList = reactionService.getBasicReactions()
        assertEquals(expectedBasicReactions.size, basicReactionsList.size)
        basicReactionsList.forEach { reaction ->
            assertTrue(expectedBasicReactions.contains(reaction.name))
        }

        // Verify caching by getting reactions again
        val cachedReactions = reactionService.getBasicReactions()
        assertEquals(basicReactionsList, cachedReactions)

        transaction {
            rollback()
        }
    }
}
