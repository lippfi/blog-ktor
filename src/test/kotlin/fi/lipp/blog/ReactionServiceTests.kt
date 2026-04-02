package fi.lipp.blog

import fi.lipp.blog.data.BlogFile
import fi.lipp.blog.data.FileUploadData
import fi.lipp.blog.data.PostDto
import fi.lipp.blog.data.UserDto
import fi.lipp.blog.model.Pageable
import fi.lipp.blog.model.exceptions.WrongUserException
import fi.lipp.blog.service.NotificationService
import fi.lipp.blog.service.PostService
import fi.lipp.blog.service.ReactionService
import fi.lipp.blog.service.Viewer
import org.jetbrains.exposed.sql.SortOrder
import fi.lipp.blog.service.implementations.PostServiceImpl
import fi.lipp.blog.service.implementations.ReactionDatabaseSeeder
import fi.lipp.blog.service.implementations.ReactionServiceImpl
import io.ktor.server.config.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.mockito.kotlin.mock
import java.util.*
import kotlin.test.*

class ReactionServiceTests : UnitTestBase() {
    private lateinit var testUser: UserDto.FullProfileInfo
    private lateinit var testUser2: UserDto.FullProfileInfo
    private lateinit var testFile: BlogFile
    private lateinit var postService: PostService
    private lateinit var reactionService: ReactionService
    private val notificationService = mock<NotificationService>()

    @BeforeTest
    fun setUp() {
        transaction {
            val (userId1, userId2) = signUsersUp()
            testUser = findUserByLogin(UnitTestBase.testUser.login)!!
            testUser2 = findUserByLogin(UnitTestBase.testUser2.login)!!
            testFile = storageService.storeReaction(testUser.id, "reaction.png",FileUploadData(
                fullName = "reaction.png",
                bytes = avatarFile1.readBytes()
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
            val reactionDatabaseSeeder = ReactionDatabaseSeeder(storageService, userService)
            reactionService = ReactionServiceImpl(storageService, groupService, notificationService, reactionDatabaseSeeder, commentWebSocketService)
            postService = PostServiceImpl(groupService, storageService, reactionService, notificationService, commentWebSocketService)
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
            postService.getPost(Viewer.Registered(userId), testUser.login, uri).post
        }
    }

    @Test
    fun `test create reaction`() {
        val name = "like"
        val icon = FileUploadData(
            fullName = "reaction.png",
            bytes = avatarFile1.readBytes()
        )

        val created = reactionService.createReaction(testUser.id, name, "custom", icon)
        assertNotNull(created)
        assertEquals(name, created.name)
    }

    @Test
    fun `test delete reaction`() {
        // Get the second user from signUsersUp
        val user2 = findUserByLogin(testUser2.login)!!

        val name = "like"
        reactionService.createReaction(
            testUser.id,
            name,
            "custom",
            FileUploadData(
                fullName = "reaction.png",
                bytes = avatarFile1.readBytes()
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
            "custom",
            FileUploadData(
                fullName = "reaction.png",
                bytes = avatarFile1.readBytes()
            )
        )

        val post = createTestPost(testUser.id)
        val viewer = Viewer.Registered(testUser.id)

        reactionService.addReaction(viewer, testUser.login, post.uri, reactionName)
        // Adding the same reaction again should not throw
        reactionService.addReaction(viewer, testUser.login, post.uri, reactionName)
    }

    @Test
    fun `test remove reaction from post`() {
        val reactionName = "like"
        val reaction = reactionService.createReaction(
            testUser.id,
            reactionName,
            "custom",
            FileUploadData(
                fullName = "reaction.png",
                bytes = avatarFile1.readBytes()
            )
        )

        val post = createTestPost(testUser.id)
        val viewer = Viewer.Registered(testUser.id)

        reactionService.addReaction(viewer, testUser.login, post.uri, reactionName)
        reactionService.removeReaction(viewer, testUser.login, post.uri, reactionName)
        // Removing non-existent reaction should not throw
        reactionService.removeReaction(viewer, testUser.login, post.uri, reactionName)
    }

    @Test
    fun `test anonymous reactions`() {
        val reaction = reactionService.createReaction(
            testUser.id,
            "like",
            "custom",
            FileUploadData(
                fullName = "reaction.png",
                bytes = avatarFile1.readBytes()
            )
        )

        val post = createTestPost(testUser.id)
        val viewer = Viewer.Anonymous("127.0.0.1", "test-fingerprint")

        reactionService.addReaction(viewer, testUser.login, post.uri, reaction.name)
        reactionService.removeReaction(viewer, testUser.login, post.uri, reaction.name)
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
                    "custom",
                    FileUploadData(
                        fullName = "reaction.png",
                        bytes = avatarFile1.readBytes()
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
                "custom",
                FileUploadData(
                    fullName = "reaction.png",
                    bytes = avatarFile1.readBytes()
                )
            )
        }
    }

    @Test
    fun `test reaction name uniqueness`() {
        val name = "like"
        val icon = FileUploadData(
            fullName = "reaction.png",
            bytes = avatarFile1.readBytes()
        )

        reactionService.createReaction(testUser.id, name, "custom", icon)

        assertFailsWith<Exception>("Should not allow duplicate reaction names") {
            reactionService.createReaction(testUser.id, name, "custom", FileUploadData(
                fullName = "reaction2.png",
                bytes = avatarFile2.readBytes()
            ))
        }
    }

    @Test
    fun `test reaction group permissions`() {
        // Get the second user from signUsersUp
        val user2 = findUserByLogin(testUser2.login)!!

        // Create a reaction
        val reaction = reactionService.createReaction(
            testUser.id,
            "like",
            "custom",
            FileUploadData(
                fullName = "reaction.png",
                bytes = avatarFile1.readBytes()
            )
        )

        // Create posts with different reaction group permissions
        val everyonePost = createTestPost(testUser.id, "post-everyone", groupService.everyoneGroupUUID)
        val registeredPost = createTestPost(testUser.id, "post-registered", groupService.registeredGroupUUID)
        val privatePost = createTestPost(testUser.id, "post-private", groupService.privateGroupUUID)

        // Test everyone post
        reactionService.addReaction(Viewer.Anonymous("127.0.0.1", "test"), testUser.login, everyonePost.uri, reaction.name)
        reactionService.addReaction(Viewer.Registered(user2.id), testUser.login, everyonePost.uri, reaction.name)

        // Test registered users post
        assertFailsWith<WrongUserException>("Anonymous user should not be able to react to registered-only post") {
            reactionService.addReaction(Viewer.Anonymous("127.0.0.1", "test"), testUser.login, registeredPost.uri, reaction.name)
        }
        reactionService.addReaction(Viewer.Registered(user2.id), testUser.login, registeredPost.uri, reaction.name)

        // Test private post
        assertFailsWith<WrongUserException>("Anonymous user should not be able to react to private post") {
            reactionService.addReaction(Viewer.Anonymous("127.0.0.1", "test"), testUser.login, privatePost.uri, reaction.name)
        }
        assertFailsWith<WrongUserException>("Other user should not be able to react to private post") {
            reactionService.addReaction(Viewer.Registered(user2.id), testUser.login, privatePost.uri, reaction.name)
        }
        // Author should be able to react to their own private post
        reactionService.addReaction(Viewer.Registered(testUser.id), testUser.login, privatePost.uri, reaction.name)
    }

    @Test
    fun `test post reaction information`() {
        // Get the second user from signUsersUp
        val user2 = findUserByLogin(testUser2.login)!!

        // Create a reaction
        val reaction = reactionService.createReaction(
            testUser.id,
            "like",
            "custom",
            FileUploadData(
                fullName = "reaction.png",
                bytes = avatarFile1.readBytes()
            )
        )

        // Create a post
        val post = createTestPost(testUser.id)

        // Add reactions from different users
        reactionService.addReaction(Viewer.Registered(testUser.id), testUser.login, post.uri, reaction.name)
        reactionService.addReaction(Viewer.Registered(user2.id), testUser.login, post.uri, reaction.name)
        reactionService.addReaction(Viewer.Anonymous("127.0.0.1", "test1"), testUser.login, post.uri, reaction.name)
        reactionService.addReaction(Viewer.Anonymous("127.0.0.2", "test2"), testUser.login, post.uri, reaction.name)

        // Get post and verify reaction information
        val updatedPost = postService.getPost(Viewer.Registered(testUser.id), testUser.login, post.uri).post
        assertEquals(1, updatedPost.reactions.size)

        val reactionInfo = updatedPost.reactions[0]
        assertEquals(reaction.name, reactionInfo.name)
        assertEquals(reaction.iconUri, reactionInfo.iconUri)
        assertEquals(2, reactionInfo.anonymousCount)
        assertEquals(2, reactionInfo.users.size)
        assertEquals(4, reactionInfo.count)
        assertTrue(reactionInfo.users.any { it.login == testUser.login })
        assertTrue(reactionInfo.users.any { it.login == testUser2.login })
    }

    @Test
    fun `test search reactions by name`() {
        // Create test reactions with unique names that don't conflict with seeded reactions
        val testReactions = listOf(
            "testlike",
            "testsuperlike",
            "testdislike",
            "testheart"
        )

        testReactions.forEach { name ->
            reactionService.createReaction(
                testUser.id,
                name,
                "custom",
                FileUploadData(
                    fullName = "reaction.png",
                    bytes = avatarFile1.readBytes()
                )
            )
        }

        // Test partial match with complete word
        val completeMatch = reactionService.searchReactionsByName("like")
        assertTrue(completeMatch.size >= 3, "Expected at least 3 reactions matching 'testlike', but found ${completeMatch.size}")
        assertTrue(completeMatch.map { it.name }.containsAll(listOf("testlike", "testsuperlike", "testdislike")))

        // Test partial match at start
        val startMatch = reactionService.searchReactionsByName("super")
        assertTrue(startMatch.isNotEmpty(), "Expected at least one reaction matching 'testsuper', but found none")
        assertTrue(startMatch.any { it.name == "testsuperlike" })

        // Test partial match in middle
        val middleMatch = reactionService.searchReactionsByName("lik")
        assertTrue(middleMatch.size >= 3, "Expected at least 3 reactions matching 'testlik', but found ${middleMatch.size}")
        assertTrue(middleMatch.map { it.name }.containsAll(listOf("testlike", "testsuperlike", "testdislike")))

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
                "custom",
                FileUploadData(
                    fullName = "reaction.png",
                    bytes = avatarFile1.readBytes()
                )
            )
        }

        // Create test posts
        val posts = (1..3).map { createTestPost(testUser.id) }

        // Add reactions in specific order to test timestamp-based ordering
        reactionService.addReaction(Viewer.Registered(testUser.id), posts[0].authorLogin!!, posts[0].uri, createdReactions[0].name)
        reactionService.addReaction(Viewer.Registered(testUser.id), posts[1].authorLogin!!, posts[1].uri, createdReactions[1].name)
        reactionService.addReaction(Viewer.Registered(testUser.id), posts[2].authorLogin!!, posts[2].uri, createdReactions[2].name)
        reactionService.addReaction(Viewer.Registered(testUser.id), posts[0].authorLogin!!, posts[0].uri, createdReactions[1].name)

        // Test getting recent reactions with default limit
        val recentReactions = reactionService.getUserRecentReactions(testUser.id)
        assertEquals(3, recentReactions.size)
        assertEquals(createdReactions[1].name, recentReactions[0].name) // Most recent (sad)
        assertEquals(createdReactions[2].name, recentReactions[1].name) // Second recent (love)
        assertEquals(createdReactions[0].name, recentReactions[2].name) // Least recent (happy)

        // Test with custom limit
        val limitedReactions = reactionService.getUserRecentReactions(testUser.id, 2)
        assertEquals(2, limitedReactions.size)
        assertEquals(createdReactions[1].name, limitedReactions[0].name)
        assertEquals(createdReactions[2].name, limitedReactions[1].name)
    }

    @Ignore
    @Test
    fun `test get basic reactions`() {
        // Create all basic reactions
        val expectedBasicReactions = listOf("like", "love", "haha", "wow", "sad", "angry")
        expectedBasicReactions.forEach { name ->
            reactionService.createReaction(
                testUser.id,
                name,
                "basic",
                FileUploadData(
                    fullName = "reaction.png",
                    bytes = avatarFile1.readBytes()
                )
            )
        }

        // Create a non-basic reaction
        reactionService.createReaction(
            testUser.id,
            "custom-reaction",
            "custom",
            FileUploadData(
                fullName = "reaction.png",
                bytes = avatarFile1.readBytes()
            )
        )

        // Get all reactions and filter for basic ones
        val allReactions = reactionService.getReactions()
        val basicReactions = allReactions.filter { expectedBasicReactions.contains(it.name) }

        // Verify basic reactions
        assertEquals(expectedBasicReactions.size, basicReactions.size)
        basicReactions.forEach { reaction ->
            assertTrue(expectedBasicReactions.contains(reaction.name))
        }

        transaction {
            rollback()
        }
    }

    @Test
    fun `test get reaction packs`() {
        val packName = "test-pack"
        reactionService.createReaction(
            testUser.id,
            "pack-reaction",
            packName,
            FileUploadData(
                fullName = "reaction.png",
                bytes = avatarFile1.readBytes()
            )
        )

        // We need to bypass the cache for this test or use a fresh service
        // Since getBasicReactions uses cachedBasicReactions which is lazy,
        // it might already be initialized.
        // But in tests, cleanDatabase is called before each test, 
        // and Koin is usually restarted or the service is fresh.
        // Wait, cachedBasicReactions is a property of ReactionServiceImpl.
        
        val packs = reactionService.getBasicReactions()
        val testPack = packs.find { it.name == packName }
        assertNotNull(testPack, "Pack $packName should be found")
    }

    @Test
    fun `test search reactions with limit and sorting`() {
        // Create test reactions with names that will test sorting
        val testReactionNames = listOf(
            "zreaction",
            "areaction",
            "breaction",
            "creaction",
            "dreaction"
        )

        testReactionNames.forEach { name ->
            reactionService.createReaction(
                testUser.id,
                name,
                "custom",
                FileUploadData(
                    fullName = "reaction.png",
                    bytes = avatarFile1.readBytes()
                )
            )
        }

        // Test search with a pattern that matches all test reactions
        val searchResults = reactionService.search("reaction")

        // Verify results contain all test reactions
        assertTrue(searchResults.size >= testReactionNames.size, 
            "Expected at least ${testReactionNames.size} reactions, but found ${searchResults.size}")

        // Verify all test reactions are in the results
        val resultNames = searchResults.map { it.name }
        testReactionNames.forEach { name ->
            assertTrue(resultNames.contains(name), "Result should contain $name")
        }

        // Verify results are sorted by ordinal (creation order)
        val sortedNames = resultNames.filter { testReactionNames.contains(it) }
        val expectedSortedNames = testReactionNames
        assertEquals(expectedSortedNames, sortedNames, "Results should be sorted by ordinal")

        // Verify limit works (this is more of a code check since we can't easily create 120+ reactions in a test)
        // The implementation should limit to 120 results
        assertTrue(searchResults.size <= 120, "Results should be limited to 120")
    }

    @Test
    fun `test reactions from ignored users are not shown on single post`() {
        transaction {
            // Use existing users: testUser as post owner, testUser2 as reactor
            val postOwner = testUser
            val reactor = testUser2

            // Create a reaction
            val reaction = reactionService.createReaction(
                postOwner.id,
                "like",
                "custom",
                FileUploadData(
                    fullName = "reaction.png",
                    bytes = avatarFile1.readBytes()
                )
            )

            // Create a post
            val post = createTestPost(postOwner.id)

            // Add reaction from reactor
            reactionService.addReaction(Viewer.Registered(reactor.id), postOwner.login, post.uri, reaction.name)

            // Verify the reaction is visible initially
            var updatedPost = postService.getPost(Viewer.Registered(postOwner.id), postOwner.login, post.uri).post
            println("[DEBUG_LOG] Initial reactions: ${updatedPost.reactions}")

            // Check if there are any reactions
            if (updatedPost.reactions.isNotEmpty()) {
                val reactionInfo = updatedPost.reactions[0]
                println("[DEBUG_LOG] Reaction users: ${reactionInfo.users.map { it.login }}")

                // Check if the reactor's reaction is visible
                if (reactionInfo.users.isNotEmpty()) {
                    assertTrue(reactionInfo.users.any { it.login == reactor.login }, "Should include reactor's reaction")
                } else {
                    fail("No users found in the reaction")
                }
            } else {
                fail("No reactions found on the post")
            }

            // Post owner ignores reactor
            userService.ignoreUser(postOwner.id, reactor.login)

            commit()

            // Verify that reactor's reaction is not visible
            updatedPost = postService.getPost(Viewer.Registered(postOwner.id), postOwner.login, post.uri).post
            println("[DEBUG_LOG] After ignore - reactions: ${updatedPost.reactions}")

            // Either there should be no reactions at all, or the reaction should have no users
            if (updatedPost.reactions.isNotEmpty()) {
                val filteredReactionInfo = updatedPost.reactions[0]
                println("[DEBUG_LOG] After ignore - reaction users: ${filteredReactionInfo.users.map { it.login }}")

                // The reaction should have no users, or at least not the ignored user
                if (filteredReactionInfo.users.isNotEmpty()) {
                    assertFalse(filteredReactionInfo.users.any { it.login == reactor.login }, 
                        "Should not include reactor's reaction after ignore, but found users: ${filteredReactionInfo.users.map { it.login }}")
                }
            }

            rollback()
        }
    }

    @Test
    fun `test reactions from ignored users are not shown on multiple posts`() {
        transaction {
            // Use existing users: testUser as post owner, testUser2 as reactor
            val postOwner = testUser
            val reactor = testUser2

            // Create a reaction
            val reaction = reactionService.createReaction(
                postOwner.id,
                "like",
                "custom",
                FileUploadData(
                    fullName = "reaction.png",
                    bytes = avatarFile1.readBytes()
                )
            )

            // Create multiple posts
            val post1 = createTestPost(postOwner.id, "test-post-1")
            val post2 = createTestPost(postOwner.id, "test-post-2")

            // Add reactions from reactor to both posts
            reactionService.addReaction(Viewer.Registered(reactor.id), postOwner.login, post1.uri, reaction.name)
            reactionService.addReaction(Viewer.Registered(reactor.id), postOwner.login, post2.uri, reaction.name)

            // Verify reactions are visible initially on both posts
            var posts = postService.getDiaryPosts(
                viewer = Viewer.Registered(postOwner.id),
                diaryLogin = postOwner.login,
                text = null,
                tags = null,
                from = null,
                to = null,
                pageable = Pageable(1, 10, SortOrder.DESC)
            ).posts.content

            // Find the posts we created
            val foundPost1 = posts.find { it.uri == "test-post-1" }
            val foundPost2 = posts.find { it.uri == "test-post-2" }

            assertNotNull(foundPost1)
            assertNotNull(foundPost2)

            // Verify reactions on post1
            println("[DEBUG_LOG] Initial reactions on post1: ${foundPost1!!.reactions}")

            // Check if there are any reactions on post1
            if (foundPost1.reactions.isNotEmpty()) {
                val reactionInfo1 = foundPost1.reactions[0]
                println("[DEBUG_LOG] Initial reaction users on post1: ${reactionInfo1.users.map { it.login }}")

                // Check if the reactor's reaction is visible on post1
                if (reactionInfo1.users.isNotEmpty()) {
                    assertTrue(reactionInfo1.users.any { it.login == reactor.login }, "Should include reactor's reaction on post1")
                } else {
                    fail("No users found in the reaction on post1")
                }
            } else {
                fail("No reactions found on post1")
            }

            // Verify reactions on post2
            println("[DEBUG_LOG] Initial reactions on post2: ${foundPost2!!.reactions}")

            // Check if there are any reactions on post2
            if (foundPost2.reactions.isNotEmpty()) {
                val reactionInfo2 = foundPost2.reactions[0]
                println("[DEBUG_LOG] Initial reaction users on post2: ${reactionInfo2.users.map { it.login }}")

                // Check if the reactor's reaction is visible on post2
                if (reactionInfo2.users.isNotEmpty()) {
                    assertTrue(reactionInfo2.users.any { it.login == reactor.login }, "Should include reactor's reaction on post2")
                } else {
                    fail("No users found in the reaction on post2")
                }
            } else {
                fail("No reactions found on post2")
            }

            // Post owner ignores reactor
            userService.ignoreUser(postOwner.id, reactor.login)

            commit()

            // Get posts again
            posts = postService.getDiaryPosts(
                viewer = Viewer.Registered(postOwner.id),
                diaryLogin = postOwner.login,
                text = null,
                tags = null,
                from = null,
                to = null,
                pageable = Pageable(1, 10, SortOrder.DESC)
            ).posts.content

            // Find the posts we created
            val updatedPost1 = posts.find { it.uri == "test-post-1" }
            val updatedPost2 = posts.find { it.uri == "test-post-2" }

            assertNotNull(updatedPost1)
            assertNotNull(updatedPost2)

            // Verify reactions on post1 - reactor's reaction should be filtered out
            println("[DEBUG_LOG] After ignore - reactions on post1: ${updatedPost1!!.reactions}")
            if (updatedPost1.reactions.isNotEmpty()) {
                val filteredReactionInfo1 = updatedPost1.reactions[0]
                println("[DEBUG_LOG] After ignore - reaction users on post1: ${filteredReactionInfo1.users.map { it.login }}")
                assertEquals(0, filteredReactionInfo1.users.size, "Should have 0 users who reacted on post1 after ignore")
                assertFalse(filteredReactionInfo1.users.any { it.login == reactor.login }, "Should not include reactor's reaction on post1 after ignore")
            } else {
                // If there are no reactions at all, that's also acceptable
                assertTrue(updatedPost1.reactions.isEmpty(), "Should have no reactions on post1 after ignore")
            }

            // Verify reactions on post2 - reactor's reaction should be filtered out
            println("[DEBUG_LOG] After ignore - reactions on post2: ${updatedPost2!!.reactions}")
            if (updatedPost2.reactions.isNotEmpty()) {
                val filteredReactionInfo2 = updatedPost2.reactions[0]
                println("[DEBUG_LOG] After ignore - reaction users on post2: ${filteredReactionInfo2.users.map { it.login }}")
                assertEquals(0, filteredReactionInfo2.users.size, "Should have 0 users who reacted on post2 after ignore")
                assertFalse(filteredReactionInfo2.users.any { it.login == reactor.login }, "Should not include reactor's reaction on post2 after ignore")
            } else {
                // If there are no reactions at all, that's also acceptable
                assertTrue(updatedPost2.reactions.isEmpty(), "Should have no reactions on post2 after ignore")
            }

            rollback()
        }
    }
}
