package fi.lipp.blog

import fi.lipp.blog.data.*
import fi.lipp.blog.model.exceptions.*
import fi.lipp.blog.service.PostService
import fi.lipp.blog.service.ReactionService
import fi.lipp.blog.service.Viewer
import fi.lipp.blog.service.implementations.PostServiceImpl
import fi.lipp.blog.service.implementations.ReactionServiceImpl
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.UUID
import kotlin.test.*

class ReactionServiceTests : UnitTestBase() {
    private lateinit var testUserRegistration: UserDto.Registration
    private lateinit var testUser: UserDto.FullProfileInfo
    private lateinit var testFile: BlogFile
    private lateinit var postService: PostService
    private lateinit var reactionService: ReactionService

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
            reactionService = ReactionServiceImpl(storageService, groupService)
            postService = PostServiceImpl(groupService, storageService, reactionService)
        }
    }

    private fun createTestPost(userId: UUID, uri: String = "test-post-${UUID.randomUUID()}"): PostDto.View {
        return transaction {
            postService.addPost(userId, PostDto.Create(
                title = "Test Post",
                uri = uri,
                text = "Test content",
                tags = setOf(),
                readGroupId = groupService.everyoneGroupUUID,
                commentGroupId = groupService.everyoneGroupUUID,
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
        val reaction = reactionService.createReaction(
            testUser.id,
            "like",
            FileUploadData(
                fullName = "reaction.png",
                inputStream = avatarFile1.inputStream()
            )
        )

        val post = createTestPost(testUser.id)
        val viewer = Viewer.Registered(testUser.id)

        reactionService.addReaction(viewer, testUser.login, post.uri, reaction.id)
        // Adding the same reaction again should not throw
        reactionService.addReaction(viewer, testUser.login, post.uri, reaction.id)
    }

    @Test
    fun `test remove reaction from post`() {
        val reaction = reactionService.createReaction(
            testUser.id,
            "like",
            FileUploadData(
                fullName = "reaction.png",
                inputStream = avatarFile1.inputStream()
            )
        )

        val post = createTestPost(testUser.id)
        val viewer = Viewer.Registered(testUser.id)

        reactionService.addReaction(viewer, testUser.login, post.uri, reaction.id)
        reactionService.removeReaction(viewer, testUser.login, post.uri, reaction.id)
        // Removing non-existent reaction should not throw
        reactionService.removeReaction(viewer, testUser.login, post.uri, reaction.id)
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
}
