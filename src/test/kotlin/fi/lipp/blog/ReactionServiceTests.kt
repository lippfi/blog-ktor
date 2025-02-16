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

    private fun createTestPost(userId: UUID): PostDto.View {
        return transaction {
            postService.addPost(userId, PostDto.Create(
                title = "Test Post",
                uri = "test-post",
                text = "Test content",
                tags = setOf(),
                readGroupId = groupService.everyoneGroupUUID,
                commentGroupId = groupService.everyoneGroupUUID,
                isPreface = false,
                isEncrypted = false,
                classes = "",
                avatar = ""
            ))
            postService.getPost(Viewer.Registered(userId), testUser.login, "test-post")
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
}
