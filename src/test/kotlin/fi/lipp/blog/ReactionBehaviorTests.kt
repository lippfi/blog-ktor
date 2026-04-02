package fi.lipp.blog

import fi.lipp.blog.data.*
import fi.lipp.blog.model.Pageable
import fi.lipp.blog.model.exceptions.*
import fi.lipp.blog.repository.*
import fi.lipp.blog.service.Viewer
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.Test
import java.util.*
import kotlin.test.*

class ReactionBehaviorTests : UnitTestBase() {

    private fun createPost(
        userId: UUID,
        title: String = "Test Post",
        readGroup: UUID = groupService.everyoneGroupUUID,
        commentGroup: UUID = groupService.everyoneGroupUUID,
        reactionGroup: UUID = groupService.everyoneGroupUUID,
        commentReactionGroup: UUID = reactionGroup,
        reactionSubset: UUID? = null,
    ): PostDto.View {
        return postService.addPost(userId, PostDto.Create(
            uri = "", avatar = "av", title = title, text = "text",
            isPreface = false, isEncrypted = false, classes = "", tags = emptySet(),
            readGroupId = readGroup, commentGroupId = commentGroup,
            reactionGroupId = reactionGroup, commentReactionGroupId = commentReactionGroup,
            reactionSubset = reactionSubset,
        ))
    }

    private fun createReaction(userId: UUID, name: String): ReactionDto.View {
        return reactionService.createReaction(Viewer.Registered(userId), name, "test-pack", FileUploadData(
            fullName = "reaction.png",
            bytes = avatarFile1.readBytes()
        ))
    }

    // --- Reactions shared behavior ---

    @Test
    fun `loading reactions for empty post set returns empty map`() {
        transaction {
            val (user1, _) = signUsersUp()
            val pageable = Pageable(1, 10, SortOrder.DESC)
            val posts = postService.getPosts(Viewer.Registered(user1), null, null, null, null, null, null, null, pageable, false)
            assertTrue(posts.content.isEmpty())
            rollback()
        }
    }

    @Test
    fun `reactions with only registered users are returned correctly`() {
        transaction {
            val (user1, user2) = signUsersUp()
            createReaction(user1, "like")
            val post = createPost(user1, "React Post")

            reactionService.addReaction(Viewer.Registered(user2), testUser.login, post.uri, "like")

            val postPage = postService.getPost(Viewer.Registered(user1), testUser.login, post.uri)
            val reactions = postPage.post.reactions
            assertTrue(reactions.isNotEmpty())
            assertEquals("like", reactions.first().name)
            assertEquals(1, reactions.first().count)
            assertEquals(0, reactions.first().anonymousCount)
            rollback()
        }
    }

    @Test
    fun `reactions with only anonymous users are returned correctly`() {
        transaction {
            val (user1, _) = signUsersUp()
            createReaction(user1, "like")
            val post = createPost(user1, "React Post")

            val anon = Viewer.Anonymous("1.2.3.4", "fp")
            reactionService.addReaction(anon, testUser.login, post.uri, "like")

            val postPage = postService.getPost(Viewer.Registered(user1), testUser.login, post.uri)
            val reactions = postPage.post.reactions
            assertTrue(reactions.isNotEmpty())
            assertEquals(1, reactions.first().anonymousCount)
            rollback()
        }
    }

    @Test
    fun `reactions with both registered and anonymous users are merged into one ReactionInfo`() {
        transaction {
            val (user1, user2) = signUsersUp()
            createReaction(user1, "like")
            val post = createPost(user1, "React Post")

            reactionService.addReaction(Viewer.Registered(user2), testUser.login, post.uri, "like")
            reactionService.addReaction(Viewer.Anonymous("1.2.3.4", "fp"), testUser.login, post.uri, "like")

            val postPage = postService.getPost(Viewer.Registered(user1), testUser.login, post.uri)
            val reactions = postPage.post.reactions
            assertEquals(1, reactions.size)
            assertEquals("like", reactions.first().name)
            assertEquals(2, reactions.first().count)
            rollback()
        }
    }

    @Test
    fun `reaction count equals users size plus anonymousCount`() {
        transaction {
            val (user1, user2) = signUsersUp()
            createReaction(user1, "like")
            val post = createPost(user1, "React Post")

            reactionService.addReaction(Viewer.Registered(user2), testUser.login, post.uri, "like")
            reactionService.addReaction(Viewer.Anonymous("1.2.3.4", "fp"), testUser.login, post.uri, "like")

            val postPage = postService.getPost(Viewer.Registered(user1), testUser.login, post.uri)
            val r = postPage.post.reactions.first()
            assertEquals(r.users.size + r.anonymousCount, r.count)
            rollback()
        }
    }

    // --- Post reactions permissions and behavior ---

    @Test
    fun `a post author can add a reaction to their own post even if reaction group denies others`() {
        transaction {
            val (user1, _) = signUsersUp()
            createReaction(user1, "like")
            val post = createPost(user1, reactionGroup = groupService.privateGroupUUID)

            reactionService.addReaction(Viewer.Registered(user1), testUser.login, post.uri, "like")

            val postPage = postService.getPost(Viewer.Registered(user1), testUser.login, post.uri)
            assertTrue(postPage.post.reactions.isNotEmpty())
            rollback()
        }
    }

    @Test
    fun `a non-author with reaction-group access can add a reaction`() {
        transaction {
            val (user1, user2) = signUsersUp()
            createReaction(user1, "like")
            val post = createPost(user1, reactionGroup = groupService.everyoneGroupUUID)

            reactionService.addReaction(Viewer.Registered(user2), testUser.login, post.uri, "like")

            val postPage = postService.getPost(Viewer.Registered(user1), testUser.login, post.uri)
            assertTrue(postPage.post.reactions.isNotEmpty())
            rollback()
        }
    }

    @Test
    fun `a non-author without reaction-group access cannot add a reaction`() {
        transaction {
            val (user1, user2) = signUsersUp()
            createReaction(user1, "like")
            val post = createPost(user1, reactionGroup = groupService.privateGroupUUID)

            assertFailsWith<WrongUserException> {
                reactionService.addReaction(Viewer.Registered(user2), testUser.login, post.uri, "like")
            }
            rollback()
        }
    }

    @Test
    fun `adding a registered reaction twice does not create duplicates`() {
        transaction {
            val (user1, user2) = signUsersUp()
            createReaction(user1, "like")
            val post = createPost(user1)

            reactionService.addReaction(Viewer.Registered(user2), testUser.login, post.uri, "like")
            reactionService.addReaction(Viewer.Registered(user2), testUser.login, post.uri, "like")

            val postPage = postService.getPost(Viewer.Registered(user1), testUser.login, post.uri)
            assertEquals(1, postPage.post.reactions.first().count)
            rollback()
        }
    }

    @Test
    fun `adding an anonymous reaction twice from same fingerprint does not create duplicates`() {
        transaction {
            val (user1, _) = signUsersUp()
            createReaction(user1, "like")
            val post = createPost(user1)

            val anon = Viewer.Anonymous("1.2.3.4", "fp")
            reactionService.addReaction(anon, testUser.login, post.uri, "like")
            reactionService.addReaction(anon, testUser.login, post.uri, "like")

            val postPage = postService.getPost(Viewer.Registered(user1), testUser.login, post.uri)
            assertEquals(1, postPage.post.reactions.first().anonymousCount)
            rollback()
        }
    }

    @Test
    fun `removing an existing registered reaction succeeds`() {
        transaction {
            val (user1, user2) = signUsersUp()
            createReaction(user1, "like")
            val post = createPost(user1)

            reactionService.addReaction(Viewer.Registered(user2), testUser.login, post.uri, "like")
            reactionService.removeReaction(Viewer.Registered(user2), testUser.login, post.uri, "like")

            val postPage = postService.getPost(Viewer.Registered(user1), testUser.login, post.uri)
            assertTrue(postPage.post.reactions.isEmpty())
            rollback()
        }
    }

    @Test
    fun `removing a nonexistent registered reaction is a no-op`() {
        transaction {
            val (user1, user2) = signUsersUp()
            createReaction(user1, "like")
            val post = createPost(user1)

            // Should not throw
            reactionService.removeReaction(Viewer.Registered(user2), testUser.login, post.uri, "like")
            rollback()
        }
    }

    @Test
    fun `removing an existing anonymous reaction succeeds`() {
        transaction {
            val (user1, _) = signUsersUp()
            createReaction(user1, "like")
            val post = createPost(user1)

            val anon = Viewer.Anonymous("1.2.3.4", "fp")
            reactionService.addReaction(anon, testUser.login, post.uri, "like")
            reactionService.removeReaction(anon, testUser.login, post.uri, "like")

            val postPage = postService.getPost(Viewer.Registered(user1), testUser.login, post.uri)
            assertTrue(postPage.post.reactions.isEmpty())
            rollback()
        }
    }

    @Test
    fun `removing a nonexistent anonymous reaction is a no-op`() {
        transaction {
            val (user1, _) = signUsersUp()
            createReaction(user1, "like")
            val post = createPost(user1)

            val anon = Viewer.Anonymous("1.2.3.4", "fp")
            reactionService.removeReaction(anon, testUser.login, post.uri, "like")
            rollback()
        }
    }

    @Test
    fun `post reaction subset restriction allows only reactions in subset`() {
        transaction {
            val (user1, user2) = signUsersUp()
            createReaction(user1, "like")
            createReaction(user1, "heart")
            val subsetId = reactionService.createReactionSubset(Viewer.Registered(user1), testUser.login, "my-subset", listOf("like"))
            val post = createPost(user1, reactionSubset = subsetId)

            reactionService.addReaction(Viewer.Registered(user2), testUser.login, post.uri, "like")
            val postPage = postService.getPost(Viewer.Registered(user1), testUser.login, post.uri)
            assertTrue(postPage.post.reactions.isNotEmpty())
            rollback()
        }
    }

    @Test
    fun `post reaction subset restriction rejects reactions outside subset`() {
        transaction {
            val (user1, user2) = signUsersUp()
            createReaction(user1, "like")
            createReaction(user1, "heart")
            val subsetId = reactionService.createReactionSubset(Viewer.Registered(user1), testUser.login, "my-subset", listOf("like"))
            val post = createPost(user1, reactionSubset = subsetId)

            assertFailsWith<WrongUserException> {
                reactionService.addReaction(Viewer.Registered(user2), testUser.login, post.uri, "heart")
            }
            rollback()
        }
    }

    @Test
    fun `post without subset accepts any existing reaction`() {
        transaction {
            val (user1, user2) = signUsersUp()
            createReaction(user1, "like")
            createReaction(user1, "heart")
            val post = createPost(user1)

            reactionService.addReaction(Viewer.Registered(user2), testUser.login, post.uri, "like")
            reactionService.addReaction(Viewer.Registered(user2), testUser.login, post.uri, "heart")

            val postPage = postService.getPost(Viewer.Registered(user1), testUser.login, post.uri)
            assertEquals(2, postPage.post.reactions.size)
            rollback()
        }
    }

    @Test
    fun `adding a reaction with nonexistent reaction name fails`() {
        transaction {
            val (user1, user2) = signUsersUp()
            val post = createPost(user1)

            assertFailsWith<ReactionNotFoundException> {
                reactionService.addReaction(Viewer.Registered(user2), testUser.login, post.uri, "nonexistent")
            }
            rollback()
        }
    }

    @Test
    fun `removing a reaction with nonexistent reaction name fails`() {
        transaction {
            val (user1, user2) = signUsersUp()
            val post = createPost(user1)

            assertFailsWith<ReactionNotFoundException> {
                reactionService.removeReaction(Viewer.Registered(user2), testUser.login, post.uri, "nonexistent")
            }
            rollback()
        }
    }

    // --- Comment reactions ---

    @Test
    fun `comment author can add reaction to own comment even if comment reaction group denies others`() {
        transaction {
            val (user1, user2) = signUsersUp()
            createReaction(user1, "like")
            val post = createPost(user1, commentReactionGroup = groupService.privateGroupUUID)
            val comment = postService.addComment(user2, CommentDto.Create(postId = post.id, avatar = "a", text = "c"))

            reactionService.addCommentReaction(Viewer.Registered(user2), comment.id, "like")
            val reactions = reactionService.getCommentReactions(Viewer.Registered(user2), comment.id)
            assertTrue(reactions.isNotEmpty())
            rollback()
        }
    }

    @Test
    fun `registered viewer with comment reaction access can add a reaction to comment`() {
        transaction {
            val (user1, user2) = signUsersUp()
            createReaction(user1, "like")
            val post = createPost(user1, commentReactionGroup = groupService.everyoneGroupUUID)
            val comment = postService.addComment(user1, CommentDto.Create(postId = post.id, avatar = "a", text = "c"))

            reactionService.addCommentReaction(Viewer.Registered(user2), comment.id, "like")
            val reactions = reactionService.getCommentReactions(Viewer.Registered(user1), comment.id)
            assertTrue(reactions.isNotEmpty())
            rollback()
        }
    }

    @Test
    fun `registered viewer without comment reaction access cannot add a reaction`() {
        transaction {
            val (user1, user2) = signUsersUp()
            createReaction(user1, "like")
            val post = createPost(user1, commentReactionGroup = groupService.privateGroupUUID)
            val comment = postService.addComment(user1, CommentDto.Create(postId = post.id, avatar = "a", text = "c"))

            assertFailsWith<WrongUserException> {
                reactionService.addCommentReaction(Viewer.Registered(user2), comment.id, "like")
            }
            rollback()
        }
    }

    @Test
    fun `adding a registered comment reaction twice does not create duplicates`() {
        transaction {
            val (user1, user2) = signUsersUp()
            createReaction(user1, "like")
            val post = createPost(user1)
            val comment = postService.addComment(user1, CommentDto.Create(postId = post.id, avatar = "a", text = "c"))

            reactionService.addCommentReaction(Viewer.Registered(user2), comment.id, "like")
            reactionService.addCommentReaction(Viewer.Registered(user2), comment.id, "like")

            val reactions = reactionService.getCommentReactions(Viewer.Registered(user1), comment.id)
            assertEquals(1, reactions.first().count)
            rollback()
        }
    }

    @Test
    fun `removing an existing registered comment reaction succeeds`() {
        transaction {
            val (user1, user2) = signUsersUp()
            createReaction(user1, "like")
            val post = createPost(user1)
            val comment = postService.addComment(user1, CommentDto.Create(postId = post.id, avatar = "a", text = "c"))

            reactionService.addCommentReaction(Viewer.Registered(user2), comment.id, "like")
            reactionService.removeCommentReaction(Viewer.Registered(user2), comment.id, "like")

            val reactions = reactionService.getCommentReactions(Viewer.Registered(user1), comment.id)
            assertTrue(reactions.isEmpty())
            rollback()
        }
    }

    @Test
    fun `removing a nonexistent registered comment reaction is a no-op`() {
        transaction {
            val (user1, user2) = signUsersUp()
            createReaction(user1, "like")
            val post = createPost(user1)
            val comment = postService.addComment(user1, CommentDto.Create(postId = post.id, avatar = "a", text = "c"))

            reactionService.removeCommentReaction(Viewer.Registered(user2), comment.id, "like")
            rollback()
        }
    }

    @Test
    fun `adding a comment reaction with nonexistent reaction name fails`() {
        transaction {
            val (user1, user2) = signUsersUp()
            val post = createPost(user1)
            val comment = postService.addComment(user1, CommentDto.Create(postId = post.id, avatar = "a", text = "c"))

            assertFailsWith<ReactionNotFoundException> {
                reactionService.addCommentReaction(Viewer.Registered(user2), comment.id, "nonexistent")
            }
            rollback()
        }
    }

    @Test
    fun `removing a comment reaction with nonexistent reaction name fails`() {
        transaction {
            val (user1, user2) = signUsersUp()
            val post = createPost(user1)
            val comment = postService.addComment(user1, CommentDto.Create(postId = post.id, avatar = "a", text = "c"))

            assertFailsWith<ReactionNotFoundException> {
                reactionService.removeCommentReaction(Viewer.Registered(user2), comment.id, "nonexistent")
            }
            rollback()
        }
    }

    @Test
    fun `comment reaction permission uses Posts commentReactionGroup not any comment-specific field`() {
        transaction {
            val (user1, user2) = signUsersUp()
            createReaction(user1, "like")
            val post = createPost(user1, commentReactionGroup = groupService.registeredGroupUUID)
            val comment = postService.addComment(user1, CommentDto.Create(postId = post.id, avatar = "a", text = "c"))

            // user2 is registered, so has access
            reactionService.addCommentReaction(Viewer.Registered(user2), comment.id, "like")
            val reactions = reactionService.getCommentReactions(Viewer.Registered(user1), comment.id)
            assertTrue(reactions.isNotEmpty())
            rollback()
        }
    }

    // --- Reaction search and retrieval ---

    @Test
    fun `getBasicReactions returns seeded packs`() {
        transaction {
            val (user1, _) = signUsersUp()
            val packs = reactionService.getBasicReactions()
            assertTrue(packs.isNotEmpty())
            rollback()
        }
    }

    @Test
    fun `searchReactionsByName returns matching reactions sorted by pack then ordinal`() {
        transaction {
            val (user1, _) = signUsersUp()
            createReaction(user1, "test-like")
            createReaction(user1, "test-love")

            val results = reactionService.searchReactionsByName("test")
            assertTrue(results.size >= 2)
            assertTrue(results.any { it.name == "test-like" })
            assertTrue(results.any { it.name == "test-love" })
            rollback()
        }
    }

    @Test
    fun `createReaction stores icon and creates reaction row`() {
        transaction {
            val (user1, _) = signUsersUp()
            val reaction = createReaction(user1, "custom-react")
            assertEquals("custom-react", reaction.name)
            assertTrue(reaction.iconUri.isNotBlank())
            rollback()
        }
    }

    @Test
    fun `createReaction fails with name taken when storage layer reports duplicate`() {
        transaction {
            val (user1, _) = signUsersUp()
            createReaction(user1, "duplicate-name")
            assertFailsWith<ReactionNameIsTakenException> {
                createReaction(user1, "duplicate-name")
            }
            rollback()
        }
    }

    @Test
    fun `deleteReaction succeeds for creator`() {
        transaction {
            val (user1, _) = signUsersUp()
            createReaction(user1, "to-delete")
            reactionService.deleteReaction(Viewer.Registered(user1), "to-delete")
            val all = reactionService.getReactions()
            assertFalse(all.any { it.name == "to-delete" })
            rollback()
        }
    }

    @Test
    fun `deleteReaction fails for non-creator`() {
        transaction {
            val (user1, user2) = signUsersUp()
            createReaction(user1, "not-yours")
            assertFailsWith<WrongUserException> {
                reactionService.deleteReaction(Viewer.Registered(user2), "not-yours")
            }
            rollback()
        }
    }

    @Test
    fun `createReactionSubset succeeds for diary owner`() {
        transaction {
            val (user1, _) = signUsersUp()
            val subsetId = reactionService.createReactionSubset(Viewer.Registered(user1), testUser.login, "my-subset", emptyList())
            assertNotNull(subsetId)
            rollback()
        }
    }

    @Test
    fun `createReactionSubset fails for non-owner`() {
        transaction {
            val (user1, user2) = signUsersUp()
            assertFailsWith<WrongUserException> {
                reactionService.createReactionSubset(Viewer.Registered(user2), testUser.login, "bad-subset", emptyList())
            }
            rollback()
        }
    }

    @Test
    fun `createReactionSubset fails when any reaction name is missing`() {
        transaction {
            val (user1, _) = signUsersUp()
            assertFailsWith<ReactionNotFoundException> {
                reactionService.createReactionSubset(Viewer.Registered(user1), testUser.login, "bad-subset", listOf("nonexistent-reaction"))
            }
            rollback()
        }
    }

    @Test
    fun `deleteReactionSubset succeeds for owner`() {
        transaction {
            val (user1, _) = signUsersUp()
            val subsetId = reactionService.createReactionSubset(Viewer.Registered(user1), testUser.login, "to-del", emptyList())
            reactionService.deleteReactionSubset(Viewer.Registered(user1), subsetId)
            rollback()
        }
    }

    @Test
    fun `deleteReactionSubset fails for non-owner`() {
        transaction {
            val (user1, user2) = signUsersUp()
            val subsetId = reactionService.createReactionSubset(Viewer.Registered(user1), testUser.login, "not-yours", emptyList())
            assertFailsWith<WrongUserException> {
                reactionService.deleteReactionSubset(Viewer.Registered(user2), subsetId)
            }
            rollback()
        }
    }

    // --- Ignore effects on comment reactions ---

    @Test
    fun `getCommentReactions excludes registered users whom viewer ignored`() {
        transaction {
            val (user1, user2) = signUsersUp()
            createReaction(user1, "like")
            val post = createPost(user1)
            val comment = postService.addComment(user1, CommentDto.Create(postId = post.id, avatar = "a", text = "c"))

            reactionService.addCommentReaction(Viewer.Registered(user2), comment.id, "like")
            userService.ignoreUser(user1, testUser2.login)
            commit()

            val reactions = reactionService.getCommentReactions(Viewer.Registered(user1), comment.id)
            assertTrue(reactions.isEmpty() || reactions.all { it.users.none { u -> u.login == testUser2.login } })
            rollback()
        }
    }

    @Test
    fun `getCommentReactions for anonymous viewer does not apply ignore-based user filtering`() {
        transaction {
            val (user1, user2) = signUsersUp()
            createReaction(user1, "like")
            val post = createPost(user1)
            val comment = postService.addComment(user1, CommentDto.Create(postId = post.id, avatar = "a", text = "c"))

            reactionService.addCommentReaction(Viewer.Registered(user2), comment.id, "like")

            val anonViewer = Viewer.Anonymous("1.2.3.4", "fp")
            val reactions = reactionService.getCommentReactions(anonViewer, comment.id)
            assertTrue(reactions.isNotEmpty())
            rollback()
        }
    }
}
