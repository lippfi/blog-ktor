package fi.lipp.blog

import fi.lipp.blog.data.*
import fi.lipp.blog.model.exceptions.*
import fi.lipp.blog.service.PostService
import fi.lipp.blog.service.Viewer
import fi.lipp.blog.service.implementations.PostServiceImpl
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.UUID
import kotlin.test.*

class ReactionServiceTests : UnitTestBase() {
    private lateinit var testUserRegistration: UserDto.Registration
    private lateinit var testUser: UserDto.FullProfileInfo
    private lateinit var testFile: BlogFile
    private lateinit var postService: PostService

    @BeforeTest
    fun setUp() {
        transaction {
            testUserRegistration = UnitTestBase.testUser
            userService.signUp(testUserRegistration, "")
            testUser = findUserByLogin(testUserRegistration.login)!!
            testFile = storageService.storeReactions(testUser.id, listOf(FileUploadData(
                fullName = "reaction.png",
                inputStream = avatarFile1.inputStream()
            ))).first()
            postService = PostServiceImpl(groupService, storageService)
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
        val reaction = ReactionDto.Create(
            name = "like",
            icon = FileUploadData(
                fullName = "reaction.png",
                inputStream = avatarFile1.inputStream()
            ),
            localizations = mapOf(
                Language.EN to "Like",
                Language.RU to "Нравится"
            )
        )

        val created = postService.createReaction(testUser.id, reaction)
        assertNotNull(created)
        assertEquals(reaction.name, created.name)
        assertEquals(reaction.localizations, created.localizations)
    }

    @Test
    fun `test update reaction`() {
        val create = ReactionDto.Create(
            name = "like",
            icon = FileUploadData(
                fullName = "reaction.png",
                inputStream = avatarFile1.inputStream()
            ),
            localizations = mapOf(Language.EN to "Like")
        )

        val created = postService.createReaction(testUser.id, create)

        val update = ReactionDto.Update(
            id = created.id,
            name = "super-like",
            icon = FileUploadData(
                fullName = "reaction2.png",
                inputStream = avatarFile2.inputStream()
            ),
            localizations = mapOf(
                Language.EN to "Super Like",
                Language.RU to "Супер"
            )
        )

        val updated = postService.updateReaction(testUser.id, update)
        assertEquals(update.name, updated.name)
        assertEquals(update.localizations, updated.localizations)
    }

    @Test
    fun `test delete reaction`() {
        val reaction = ReactionDto.Create(
            name = "like",
            icon = FileUploadData(
                fullName = "reaction.png",
                inputStream = avatarFile1.inputStream()
            ),
            localizations = mapOf(Language.EN to "Like")
        )

        val created = postService.createReaction(testUser.id, reaction)
        postService.deleteReaction(testUser.id, created.id)

        assertFailsWith<ReactionNotFoundException> {
            postService.updateReaction(testUser.id, ReactionDto.Update(
                id = created.id,
                name = "new-name",
                icon = FileUploadData(
                    fullName = "reaction2.png",
                    inputStream = avatarFile2.inputStream()
                ),
                localizations = mapOf()
            ))
        }
    }

    @Test
    fun `test add reaction to post`() {
        val reaction = postService.createReaction(testUser.id, ReactionDto.Create(
            name = "like",
            icon = FileUploadData(
                fullName = "reaction.png",
                inputStream = avatarFile1.inputStream()
            ),
            localizations = mapOf(Language.EN to "Like")
        ))

        val post = createTestPost(testUser.id)
        val viewer = Viewer.Registered(testUser.id)

        postService.addReaction(viewer, testUser.login, post.uri, reaction.id)
        // Adding the same reaction again should not throw
        postService.addReaction(viewer, testUser.login, post.uri, reaction.id)
    }

    @Test
    fun `test remove reaction from post`() {
        val reaction = postService.createReaction(testUser.id, ReactionDto.Create(
            name = "like",
            icon = FileUploadData(
                fullName = "reaction.png",
                inputStream = avatarFile1.inputStream()
            ),
            localizations = mapOf(Language.EN to "Like")
        ))

        val post = createTestPost(testUser.id)
        val viewer = Viewer.Registered(testUser.id)

        postService.addReaction(viewer, testUser.login, post.uri, reaction.id)
        postService.removeReaction(viewer, testUser.login, post.uri, reaction.id)
        // Removing non-existent reaction should not throw
        postService.removeReaction(viewer, testUser.login, post.uri, reaction.id)
    }

    @Test
    fun `test anonymous reactions`() {
        val reaction = postService.createReaction(testUser.id, ReactionDto.Create(
            name = "like",
            icon = FileUploadData(
                fullName = "reaction.png",
                inputStream = avatarFile1.inputStream()
            ),
            localizations = mapOf(Language.EN to "Like")
        ))

        val post = createTestPost(testUser.id)
        val viewer = Viewer.Anonymous("127.0.0.1", "test-fingerprint")

        postService.addReaction(viewer, testUser.login, post.uri, reaction.id)
        postService.removeReaction(viewer, testUser.login, post.uri, reaction.id)
    }
}
