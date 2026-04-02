package fi.lipp.blog

import fi.lipp.blog.data.FileUploadData
import fi.lipp.blog.domain.ReactionEntity
import fi.lipp.blog.service.Viewer
import fi.lipp.blog.domain.ReactionSubsetEntity
import fi.lipp.blog.domain.ReactionSubsetReactionEntity
import fi.lipp.blog.model.exceptions.ReactionNotFoundException
import fi.lipp.blog.model.exceptions.WrongUserException
import fi.lipp.blog.repository.ReactionSubsetReactions
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.UUID
import org.junit.Before
import org.junit.Test
import kotlin.test.*

class ReactionSubsetTests : UnitTestBase() {
    private lateinit var user1Id: UUID
    private lateinit var user2Id: UUID

    @Before
    fun setUp() {
        val ids = signUsersUp()
        user1Id = ids.first
        user2Id = ids.second

        // Create some reactions to use
        transaction {
            reactionService.createReaction(Viewer.Registered(user1Id), "like", "custom", FileUploadData("like.png", avatarFile1.readBytes()))
            reactionService.createReaction(Viewer.Registered(user1Id), "love", "custom", FileUploadData("love.png", avatarFile1.readBytes()))
            reactionService.createReaction(Viewer.Registered(user1Id), "haha", "custom", FileUploadData("haha.png", avatarFile1.readBytes()))
        }
    }

    @Test
    fun `test create reaction subset`() {
        val name = "My Favorites"
        val reactionNames = listOf("fire", "heart")

        val subsetId = reactionService.createReactionSubset(Viewer.Registered(user1Id), testUser.login, name, reactionNames)

        transaction {
            val subset = ReactionSubsetEntity.findById(subsetId)
            assertNotNull(subset)
            assertEquals(name, subset.name)

            val subsetReactions = ReactionSubsetReactionEntity.find { ReactionSubsetReactions.subset eq subsetId }.toList()
            assertEquals(2, subsetReactions.size)
            val names = subsetReactions.map { ReactionEntity.findById(it.reaction)!!.name }
            assertTrue(names.containsAll(reactionNames))
        }
    }

    @Test
    fun `test create empty reaction subset`() {
        val subsetId = reactionService.createReactionSubset(Viewer.Registered(user1Id), testUser.login, "Empty", emptyList())
        transaction {
            val subset = ReactionSubsetEntity.findById(subsetId)
            assertNotNull(subset)
            val subsetReactions = ReactionSubsetReactionEntity.find { ReactionSubsetReactions.subset eq subsetId }.toList()
            assertTrue(subsetReactions.isEmpty())
        }
    }

    @Test
    fun `test create reaction subset with non-existent reaction`() {
        assertFailsWith<ReactionNotFoundException> {
            reactionService.createReactionSubset(Viewer.Registered(user1Id), testUser.login, "Invalid", listOf("like", "missing"))
        }
    }

    @Test
    fun `test create reaction subset with duplicate names`() {
        val subsetId = reactionService.createReactionSubset(Viewer.Registered(user1Id), testUser.login, "Duplicates", listOf("like", "like"))
        transaction {
            val subsetReactions = ReactionSubsetReactionEntity.find { ReactionSubsetReactions.subset eq subsetId }.toList()
            assertEquals(1, subsetReactions.size)
        }
    }

    @Test
    fun `test create reaction subset wrong user`() {
        assertFailsWith<WrongUserException> {
            reactionService.createReactionSubset(Viewer.Registered(user2Id), testUser.login, "Hacker Subset", listOf("like"))
        }
    }

    @Test
    fun `test update reaction subset name`() {
        val subsetId = reactionService.createReactionSubset(Viewer.Registered(user1Id), testUser.login, "Old Name", listOf("like"))

        reactionService.updateReactionSubset(Viewer.Registered(user1Id), subsetId, "New Name", null)

        transaction {
            val subset = ReactionSubsetEntity.findById(subsetId)
            assertNotNull(subset)
            assertEquals("New Name", subset.name)
            assertEquals(1, ReactionSubsetReactionEntity.find { ReactionSubsetReactions.subset eq subsetId }.count())
        }
    }

    @Test
    fun `test update reaction subset reactions`() {
        val subsetId = reactionService.createReactionSubset(Viewer.Registered(user1Id), testUser.login, "Subset", listOf("wolf"))

        reactionService.updateReactionSubset(Viewer.Registered(user1Id), subsetId, null, listOf("fire", "heart"))

        transaction {
            val subsetReactions = ReactionSubsetReactionEntity.find { ReactionSubsetReactions.subset eq subsetId }.toList()
            assertEquals(2, subsetReactions.size)
            val names = subsetReactions.map { ReactionEntity.findById(it.reaction)!!.name }
            assertTrue(names.containsAll(listOf("fire", "heart")))
            assertFalse(names.contains("wolf"))
        }
    }

    @Test
    fun `test update reaction subset to empty`() {
        val subsetId = reactionService.createReactionSubset(Viewer.Registered(user1Id), testUser.login, "Subset", listOf("like"))

        reactionService.updateReactionSubset(Viewer.Registered(user1Id), subsetId, null, emptyList())

        transaction {
            val subsetReactions = ReactionSubsetReactionEntity.find { ReactionSubsetReactions.subset eq subsetId }.toList()
            assertTrue(subsetReactions.isEmpty())
        }
    }

    @Test
    fun `test update reaction subset wrong user`() {
        val subsetId = reactionService.createReactionSubset(Viewer.Registered(user1Id), testUser.login, "User1 Subset", listOf("like"))

        assertFailsWith<WrongUserException> {
            reactionService.updateReactionSubset(Viewer.Registered(user2Id), subsetId, "Hacked", null)
        }
    }

    @Test
    fun `test update reaction subset with non-existent reaction`() {
        val subsetId = reactionService.createReactionSubset(Viewer.Registered(user1Id), testUser.login, "Subset", listOf("like"))
        assertFailsWith<ReactionNotFoundException> {
            reactionService.updateReactionSubset(Viewer.Registered(user1Id), subsetId, null, listOf("missing"))
        }
    }

    @Test
    fun `test update non-existent reaction subset`() {
        reactionService.updateReactionSubset(Viewer.Registered(user1Id), UUID.randomUUID(), "Whatever", null)
    }

    @Test
    fun `test delete reaction subset`() {
        val subsetId = reactionService.createReactionSubset(Viewer.Registered(user1Id), testUser.login, "Delete Me", listOf("like"))

        reactionService.deleteReactionSubset(Viewer.Registered(user1Id), subsetId)

        transaction {
            assertNull(ReactionSubsetEntity.findById(subsetId))
            val subsetReactions = ReactionSubsetReactionEntity.find { ReactionSubsetReactions.subset eq subsetId }.toList()
            assertTrue(subsetReactions.isEmpty())
        }
    }

    @Test
    fun `test delete reaction subset wrong user`() {
        val subsetId = reactionService.createReactionSubset(Viewer.Registered(user1Id), testUser.login, "User1 Subset", listOf("like"))

        assertFailsWith<WrongUserException> {
            reactionService.deleteReactionSubset(Viewer.Registered(user2Id), subsetId)
        }

        transaction {
            assertNotNull(ReactionSubsetEntity.findById(subsetId))
        }
    }

    @Test
    fun `test delete non-existent reaction subset`() {
        reactionService.deleteReactionSubset(Viewer.Registered(user1Id), UUID.randomUUID())
    }
}
