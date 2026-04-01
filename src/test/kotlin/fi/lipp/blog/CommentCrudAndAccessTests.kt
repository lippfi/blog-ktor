package fi.lipp.blog

import fi.lipp.blog.data.*
import fi.lipp.blog.domain.CommentEntity
import fi.lipp.blog.domain.PostEntity
import fi.lipp.blog.model.Pageable
import fi.lipp.blog.model.exceptions.*
import fi.lipp.blog.repository.*
import fi.lipp.blog.service.Viewer
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.Test
import java.util.*
import kotlin.test.*

class CommentCrudAndAccessTests : UnitTestBase() {

    private fun createPost(
        userId: UUID,
        title: String = "Test Post",
        readGroup: UUID = groupService.everyoneGroupUUID,
        commentGroup: UUID = groupService.everyoneGroupUUID,
        reactionGroup: UUID = groupService.everyoneGroupUUID,
        commentReactionGroup: UUID = reactionGroup,
    ): PostDto.View {
        return postService.addPost(userId, PostDto.Create(
            uri = "", avatar = "av", title = title, text = "text",
            isPreface = false, isEncrypted = false, classes = "", tags = emptySet(),
            readGroupId = readGroup, commentGroupId = commentGroup,
            reactionGroupId = reactionGroup, commentReactionGroupId = commentReactionGroup,
        ))
    }

    // --- Single comment access ---

    @Test
    fun `a post author can fetch a comment on their post even if they are not the comment author`() {
        transaction {
            val (user1, user2) = signUsersUp()
            val post = createPost(user1)
            val comment = postService.addComment(user2, CommentDto.Create(postId = post.id, avatar = "a", text = "Hi"))

            val fetched = postService.getComment(Viewer.Registered(user1), comment.id)
            assertEquals("Hi", fetched.text)
            rollback()
        }
    }

    @Test
    fun `a comment author can fetch their own comment even if read group would otherwise deny access`() {
        transaction {
            val (user1, user2) = signUsersUp()
            val post = createPost(user1, commentGroup = groupService.everyoneGroupUUID)
            val comment = postService.addComment(user2, CommentDto.Create(postId = post.id, avatar = "a", text = "My comment"))

            // Even if we restrict read access, the commenter can still fetch their own comment
            val fetched = postService.getComment(Viewer.Registered(user2), comment.id)
            assertEquals("My comment", fetched.text)
            rollback()
        }
    }

    @Test
    fun `an unrelated viewer with read access to the post can fetch the comment`() {
        transaction {
            val users = signUsersUp(3)
            val (u1, _) = users[0]
            val (u2, _) = users[1]
            val (u3, _) = users[2]
            val post = createPost(u1)
            val comment = postService.addComment(u2, CommentDto.Create(postId = post.id, avatar = "a", text = "Visible"))

            val fetched = postService.getComment(Viewer.Registered(u3), comment.id)
            assertEquals("Visible", fetched.text)
            rollback()
        }
    }

    @Test
    fun `an unrelated viewer without read access cannot fetch the comment`() {
        transaction {
            val users = signUsersUp(3)
            val (u1, _) = users[0]
            val (u2, _) = users[1]
            val (u3, _) = users[2]
            val post = createPost(u1, readGroup = groupService.privateGroupUUID, commentGroup = groupService.privateGroupUUID)
            // u1 is the owner, so they can comment on their own post
            val comment = postService.addComment(u1, CommentDto.Create(postId = post.id, avatar = "a", text = "Secret"))

            assertFailsWith<CommentNotFoundException> {
                postService.getComment(Viewer.Registered(u3), comment.id)
            }
            rollback()
        }
    }

    @Test
    fun `fetching a nonexistent comment returns not found`() {
        transaction {
            val (user1, _) = signUsersUp()
            assertFailsWith<CommentNotFoundException> {
                postService.getComment(Viewer.Registered(user1), UUID.randomUUID())
            }
            rollback()
        }
    }

    // --- Comment creation ---

    @Test
    fun `a user with post read and comment permissions can create a comment`() {
        transaction {
            val (user1, user2) = signUsersUp()
            val post = createPost(user1)
            val comment = postService.addComment(user2, CommentDto.Create(postId = post.id, avatar = "av", text = "Hello"))
            assertEquals("Hello", comment.text)
            rollback()
        }
    }

    @Test
    fun `post owner can create a comment even if comment group would otherwise deny them`() {
        transaction {
            val (user1, _) = signUsersUp()
            val post = createPost(user1, commentGroup = groupService.privateGroupUUID)
            val comment = postService.addComment(user1, CommentDto.Create(postId = post.id, avatar = "av", text = "Owner comment"))
            assertEquals("Owner comment", comment.text)
            rollback()
        }
    }

    @Test
    fun `a user without comment permission cannot create a comment`() {
        transaction {
            val (user1, user2) = signUsersUp()
            val post = createPost(user1, commentGroup = groupService.privateGroupUUID)
            assertFailsWith<WrongUserException> {
                postService.addComment(user2, CommentDto.Create(postId = post.id, avatar = "av", text = "Denied"))
            }
            rollback()
        }
    }

    @Test
    fun `a user without read permission cannot create a comment`() {
        transaction {
            val (user1, user2) = signUsersUp()
            val post = createPost(user1, readGroup = groupService.privateGroupUUID)
            assertFailsWith<WrongUserException> {
                postService.addComment(user2, CommentDto.Create(postId = post.id, avatar = "av", text = "Denied"))
            }
            rollback()
        }
    }

    @Test
    fun `creating a comment with valid parent in same post succeeds`() {
        transaction {
            val (user1, user2) = signUsersUp()
            val post = createPost(user1)
            val parent = postService.addComment(user2, CommentDto.Create(postId = post.id, avatar = "a", text = "Parent"))
            val reply = postService.addComment(user1, CommentDto.Create(postId = post.id, avatar = "a", text = "Reply", parentCommentId = parent.id))
            assertEquals("Reply", reply.text)
            assertNotNull(reply.inReplyTo)
            rollback()
        }
    }

    @Test
    fun `creating a comment with nonexistent parent fails`() {
        transaction {
            val (user1, _) = signUsersUp()
            val post = createPost(user1)
            assertFailsWith<InvalidParentComment> {
                postService.addComment(user1, CommentDto.Create(postId = post.id, avatar = "a", text = "Bad parent", parentCommentId = UUID.randomUUID()))
            }
            rollback()
        }
    }

    @Test
    fun `creating a comment with parent from another post fails`() {
        transaction {
            val (user1, _) = signUsersUp()
            val post1 = createPost(user1, "Post1")
            val post2 = createPost(user1, "Post2")
            val parentComment = postService.addComment(user1, CommentDto.Create(postId = post1.id, avatar = "a", text = "Parent on post1"))
            assertFailsWith<InvalidParentComment> {
                postService.addComment(user1, CommentDto.Create(postId = post2.id, avatar = "a", text = "Cross-post reply", parentCommentId = parentComment.id))
            }
            rollback()
        }
    }

    @Test
    fun `creating a comment stores local author fields correctly`() {
        transaction {
            val (user1, user2) = signUsersUp()
            val post = createPost(user1)
            val comment = postService.addComment(user2, CommentDto.Create(postId = post.id, avatar = "my-avatar", text = "My text"))
            assertEquals("my-avatar", comment.avatar)
            assertEquals("My text", comment.text)
            assertEquals(testUser2.login, comment.authorLogin)
            assertEquals(testUser2.nickname, comment.authorNickname)
            rollback()
        }
    }

    @Test
    fun `creating a comment updates Posts lastCommentTime`() {
        transaction {
            val (user1, user2) = signUsersUp()
            val post = createPost(user1)
            val postBefore = PostEntity.findById(post.id)!!
            assertNull(postBefore.let { Posts.select { Posts.id eq post.id }.first()[Posts.lastCommentTime] })

            postService.addComment(user2, CommentDto.Create(postId = post.id, avatar = "a", text = "c"))
            val lastTime = Posts.select { Posts.id eq post.id }.first()[Posts.lastCommentTime]
            assertNotNull(lastTime)
            rollback()
        }
    }

    @Test
    fun `creating a top-level comment inserts correct comment dependencies`() {
        transaction {
            val (user1, user2) = signUsersUp()
            val post = createPost(user1)
            val comment = postService.addComment(user2, CommentDto.Create(postId = post.id, avatar = "a", text = "Top level"))

            val deps = CommentDependencies.select { CommentDependencies.comment eq comment.id }.map { it[CommentDependencies.user].value }.toSet()
            assertTrue(deps.contains(user2)) // commenter
            assertTrue(deps.contains(user1)) // post author
            rollback()
        }
    }

    @Test
    fun `creating a reply comment copies parent dependencies plus current user plus post author`() {
        transaction {
            val users = signUsersUp(3)
            val (u1, _) = users[0]
            val (u2, _) = users[1]
            val (u3, _) = users[2]
            val post = createPost(u1, "Reply Dep Test")
            val parent = postService.addComment(u2, CommentDto.Create(postId = post.id, avatar = "a", text = "Parent"))
            val reply = postService.addComment(u3, CommentDto.Create(postId = post.id, avatar = "a", text = "Reply", parentCommentId = parent.id))

            val deps = CommentDependencies.select { CommentDependencies.comment eq reply.id }.map { it[CommentDependencies.user].value }.toSet()
            assertTrue(deps.contains(u1)) // post author
            assertTrue(deps.contains(u2)) // parent commenter
            assertTrue(deps.contains(u3)) // reply commenter
            rollback()
        }
    }

    @Test
    fun `creating a comment does not duplicate dependency rows when input set already deduplicated`() {
        transaction {
            val (user1, _) = signUsersUp()
            val post = createPost(user1)
            // User1 is both post author and commenter
            val comment = postService.addComment(user1, CommentDto.Create(postId = post.id, avatar = "a", text = "Self"))

            val deps = CommentDependencies.select { CommentDependencies.comment eq comment.id }.map { it[CommentDependencies.user].value }
            // Should not have duplicate entries for user1
            assertEquals(deps.toSet().size, deps.size)
            rollback()
        }
    }

    // --- Comment update ---

    @Test
    fun `updating a comment by owner succeeds`() {
        transaction {
            val (user1, user2) = signUsersUp()
            val post = createPost(user1)
            val comment = postService.addComment(user2, CommentDto.Create(postId = post.id, avatar = "a", text = "Original"))

            val updated = postService.updateComment(user2, CommentDto.Update(id = comment.id, avatar = "new-av", text = "Updated"))
            assertEquals("Updated", updated.text)
            assertEquals("new-av", updated.avatar)
            rollback()
        }
    }

    @Test
    fun `updating a comment by non-owner fails`() {
        transaction {
            val (user1, user2) = signUsersUp()
            val post = createPost(user1)
            val comment = postService.addComment(user2, CommentDto.Create(postId = post.id, avatar = "a", text = "Original"))

            assertFailsWith<WrongUserException> {
                postService.updateComment(user1, CommentDto.Update(id = comment.id, avatar = "a", text = "Hacked"))
            }
            rollback()
        }
    }

    @Test
    fun `updating a comment changes avatar`() {
        transaction {
            val (user1, user2) = signUsersUp()
            val post = createPost(user1)
            val comment = postService.addComment(user2, CommentDto.Create(postId = post.id, avatar = "old", text = "t"))

            val updated = postService.updateComment(user2, CommentDto.Update(id = comment.id, avatar = "new", text = "t"))
            assertEquals("new", updated.avatar)
            rollback()
        }
    }

    @Test
    fun `updating a comment changes text`() {
        transaction {
            val (user1, user2) = signUsersUp()
            val post = createPost(user1)
            val comment = postService.addComment(user2, CommentDto.Create(postId = post.id, avatar = "a", text = "old text"))

            val updated = postService.updateComment(user2, CommentDto.Update(id = comment.id, avatar = "a", text = "new text"))
            assertEquals("new text", updated.text)
            rollback()
        }
    }

    // --- Comment deletion ---

    @Test
    fun `deleting a comment by comment owner succeeds`() {
        transaction {
            val (user1, user2) = signUsersUp()
            val post = createPost(user1)
            val comment = postService.addComment(user2, CommentDto.Create(postId = post.id, avatar = "a", text = "To delete"))

            postService.deleteComment(user2, comment.id)

            assertNull(CommentEntity.findById(comment.id))
            rollback()
        }
    }

    @Test
    fun `deleting a comment by post owner succeeds`() {
        transaction {
            val (user1, user2) = signUsersUp()
            val post = createPost(user1)
            val comment = postService.addComment(user2, CommentDto.Create(postId = post.id, avatar = "a", text = "To delete"))

            postService.deleteComment(user1, comment.id)

            assertNull(CommentEntity.findById(comment.id))
            rollback()
        }
    }

    @Test
    fun `deleting a comment by unrelated user fails`() {
        transaction {
            val users = signUsersUp(3)
            val (u1, _) = users[0]
            val (u2, _) = users[1]
            val (u3, _) = users[2]
            val post = createPost(u1)
            val comment = postService.addComment(u2, CommentDto.Create(postId = post.id, avatar = "a", text = "Not yours"))

            assertFailsWith<WrongUserException> {
                postService.deleteComment(u3, comment.id)
            }
            rollback()
        }
    }

    @Test
    fun `deleting a comment updates Posts lastCommentTime to latest remaining comment`() {
        transaction {
            val (user1, user2) = signUsersUp()
            val post = createPost(user1)
            postService.addComment(user2, CommentDto.Create(postId = post.id, avatar = "a", text = "c1"))
            Thread.sleep(50)
            val c2 = postService.addComment(user2, CommentDto.Create(postId = post.id, avatar = "a", text = "c2"))

            postService.deleteComment(user2, c2.id)

            val lastTime = Posts.select { Posts.id eq post.id }.first()[Posts.lastCommentTime]
            assertNotNull(lastTime)
            rollback()
        }
    }

    @Test
    fun `deleting the last comment sets Posts lastCommentTime to null`() {
        transaction {
            val (user1, user2) = signUsersUp()
            val post = createPost(user1)
            val c1 = postService.addComment(user2, CommentDto.Create(postId = post.id, avatar = "a", text = "Only comment"))

            postService.deleteComment(user2, c1.id)

            val lastTime = Posts.select { Posts.id eq post.id }.first()[Posts.lastCommentTime]
            assertNull(lastTime)
            rollback()
        }
    }

    @Test
    fun `deleting a nonexistent comment returns not found`() {
        transaction {
            val (user1, _) = signUsersUp()
            assertFailsWith<CommentNotFoundException> {
                postService.deleteComment(user1, UUID.randomUUID())
            }
            rollback()
        }
    }

    // --- Comment access checks and DTO flags ---

    @Test
    fun `a comment local author has isReactable true even if comment reaction group denies others`() {
        transaction {
            val (user1, user2) = signUsersUp()
            val post = createPost(user1, commentReactionGroup = groupService.privateGroupUUID)
            val comment = postService.addComment(user2, CommentDto.Create(postId = post.id, avatar = "a", text = "My comment"))

            // user2 is the commenter, should be reactable
            assertTrue(comment.isReactable)
            rollback()
        }
    }

    @Test
    fun `a non-author with access to comment reaction group has isReactable true`() {
        transaction {
            val (user1, user2) = signUsersUp()
            val post = createPost(user1, commentReactionGroup = groupService.everyoneGroupUUID)
            postService.addComment(user2, CommentDto.Create(postId = post.id, avatar = "a", text = "Comment"))

            val postPage = postService.getPost(Viewer.Registered(user1), testUser.login, post.uri)
            assertTrue(postPage.comments.first().isReactable)
            rollback()
        }
    }

    @Test
    fun `a non-author without access to comment reaction group has isReactable false`() {
        transaction {
            val (user1, user2) = signUsersUp()
            val post = createPost(user1, commentReactionGroup = groupService.privateGroupUUID)
            postService.addComment(user1, CommentDto.Create(postId = post.id, avatar = "a", text = "Comment"))

            // user2 is not the comment author and not in private group
            val postPage = postService.getPost(Viewer.Registered(user2), testUser.login, post.uri)
            assertFalse(postPage.comments.first().isReactable)
            rollback()
        }
    }

    @Test
    fun `reaction group for comments comes from Posts commentReactionGroup not comment row`() {
        transaction {
            val (user1, user2) = signUsersUp()
            val post = createPost(user1, commentReactionGroup = groupService.registeredGroupUUID)
            postService.addComment(user1, CommentDto.Create(postId = post.id, avatar = "a", text = "c"))

            val postPage = postService.getPost(Viewer.Registered(user2), testUser.login, post.uri)
            assertEquals(groupService.registeredGroupUUID, postPage.comments.first().reactionGroupId)
            rollback()
        }
    }

    @Test
    fun `comment DTO includes correct reactionGroupId from post`() {
        transaction {
            val (user1, user2) = signUsersUp()
            val post = createPost(user1, commentReactionGroup = groupService.friendsGroupUUID)
            postService.addComment(user1, CommentDto.Create(postId = post.id, avatar = "a", text = "c"))

            val postPage = postService.getPost(Viewer.Registered(user1), testUser.login, post.uri)
            assertEquals(groupService.friendsGroupUUID, postPage.comments.first().reactionGroupId)
            rollback()
        }
    }

    @Test
    fun `comment DTO includes correct diaryLogin`() {
        transaction {
            val (user1, user2) = signUsersUp()
            val post = createPost(user1)
            postService.addComment(user2, CommentDto.Create(postId = post.id, avatar = "a", text = "c"))

            val postPage = postService.getPost(Viewer.Registered(user1), testUser.login, post.uri)
            assertEquals(testUser.login, postPage.comments.first().diaryLogin)
            rollback()
        }
    }

    @Test
    fun `comment DTO includes correct postUri`() {
        transaction {
            val (user1, user2) = signUsersUp()
            val post = createPost(user1)
            postService.addComment(user2, CommentDto.Create(postId = post.id, avatar = "a", text = "c"))

            val postPage = postService.getPost(Viewer.Registered(user1), testUser.login, post.uri)
            assertEquals(post.uri, postPage.comments.first().postUri)
            rollback()
        }
    }
}
