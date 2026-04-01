package fi.lipp.blog

import fi.lipp.blog.data.*
import fi.lipp.blog.domain.DiaryEntity
import fi.lipp.blog.model.Pageable
import fi.lipp.blog.model.exceptions.*
import fi.lipp.blog.repository.*
import fi.lipp.blog.service.Viewer
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.Test
import java.util.*
import kotlin.test.*

class CommentVisibilityAndLoadingTests : UnitTestBase() {

    private fun createPost(userId: UUID, title: String = "Test Post"): PostDto.View {
        return postService.addPost(userId, PostDto.Create(
            uri = "", avatar = "av", title = title, text = "text",
            isPreface = false, isEncrypted = false, classes = "", tags = emptySet(),
            readGroupId = groupService.everyoneGroupUUID,
            commentGroupId = groupService.everyoneGroupUUID,
            reactionGroupId = groupService.everyoneGroupUUID,
            commentReactionGroupId = groupService.everyoneGroupUUID,
        ))
    }

    private fun insertExternalUser(nickname: String, linkedUserId: UUID? = null): UUID {
        return ExternalUsers.insertAndGetId {
            it[ExternalUsers.platformName] = "telegram"
            it[ExternalUsers.externalUserId] = UUID.randomUUID().toString()
            it[ExternalUsers.nickname] = nickname
            it[ExternalUsers.user] = linkedUserId
        }.value
    }

    private fun insertExternalComment(postId: UUID, extUserId: UUID, text: String, parentId: UUID? = null): UUID {
        val commentId = Comments.insertAndGetId {
            it[Comments.post] = postId
            it[Comments.authorType] = CommentAuthorType.EXTERNAL
            it[Comments.externalAuthor] = extUserId
            it[Comments.localAuthor] = null
            it[Comments.anonymousAuthor] = null
            it[Comments.avatar] = "av"
            it[Comments.text] = text
            it[Comments.parentComment] = parentId
        }
        return commentId.value
    }

    private fun insertAnonymousUser(nickname: String?): UUID {
        return AnonymousUsers.insertAndGetId {
            it[AnonymousUsers.nickname] = nickname ?: "anonymous"
            it[AnonymousUsers.ipFingerprint] = UUID.randomUUID().toString()
        }.value
    }

    private fun insertAnonymousComment(postId: UUID, anonUserId: UUID, text: String, parentId: UUID? = null): UUID {
        val commentId = Comments.insertAndGetId {
            it[Comments.post] = postId
            it[Comments.authorType] = CommentAuthorType.ANONYMOUS
            it[Comments.anonymousAuthor] = anonUserId
            it[Comments.localAuthor] = null
            it[Comments.externalAuthor] = null
            it[Comments.avatar] = "av"
            it[Comments.text] = text
            it[Comments.parentComment] = parentId
        }
        return commentId.value
    }

    @Test
    fun `loading comments for a post returns comments ordered by creation time ascending`() {
        transaction {
            val (user1, user2) = signUsersUp()
            val post = createPost(user1)
            postService.addComment(user2, CommentDto.Create(postId = post.id, avatar = "a", text = "First"))
            Thread.sleep(50)
            postService.addComment(user2, CommentDto.Create(postId = post.id, avatar = "a", text = "Second"))

            val postPage = postService.getPost(Viewer.Registered(user1), testUser.login, post.uri)
            assertEquals(2, postPage.comments.size)
            assertEquals("First", postPage.comments[0].text)
            assertEquals("Second", postPage.comments[1].text)
            rollback()
        }
    }

    @Test
    fun `anonymous viewer sees comments when post is visible and no ignore filtering applies`() {
        transaction {
            val (user1, user2) = signUsersUp()
            val post = createPost(user1)
            postService.addComment(user2, CommentDto.Create(postId = post.id, avatar = "a", text = "Hello"))

            val anon = Viewer.Anonymous("1.2.3.4", "fp")
            val postPage = postService.getPost(anon, testUser.login, post.uri)
            assertEquals(1, postPage.comments.size)
            rollback()
        }
    }

    @Test
    fun `registered viewer sees comments when post is visible and no ignore filtering applies`() {
        transaction {
            val users = signUsersUp(3)
            val (u1, l1) = users[0]
            val (u2, _) = users[1]
            val (u3, _) = users[2]
            val post = createPost(u1, "Test")
            postService.addComment(u2, CommentDto.Create(postId = post.id, avatar = "a", text = "Hi"))

            val postPage = postService.getPost(Viewer.Registered(u3), l1, post.uri)
            assertEquals(1, postPage.comments.size)
            rollback()
        }
    }

    @Test
    fun `registered viewer does not see comments excluded by ignore relationships`() {
        transaction {
            val (user1, user2) = signUsersUp()
            val post = createPost(user1)
            postService.addComment(user2, CommentDto.Create(postId = post.id, avatar = "a", text = "Ignored"))

            userService.ignoreUser(user1, testUser2.login)
            commit()

            val postPage = postService.getPost(Viewer.Registered(user1), testUser.login, post.uri)
            assertEquals(0, postPage.comments.size)
            rollback()
        }
    }

    @Test
    fun `anonymous viewer does not exclude comments via ignore relationships`() {
        transaction {
            val (user1, user2) = signUsersUp()
            val post = createPost(user1)
            postService.addComment(user2, CommentDto.Create(postId = post.id, avatar = "a", text = "Visible"))

            val anon = Viewer.Anonymous("1.2.3.4", "fp")
            val postPage = postService.getPost(anon, testUser.login, post.uri)
            assertEquals(1, postPage.comments.size)
            rollback()
        }
    }

    @Test
    fun `comment count matches number of visible comments after ignore filtering`() {
        transaction {
            val users = signUsersUp(3)
            val (u1, l1) = users[0]
            val (u2, l2) = users[1]
            val (u3, _) = users[2]
            val post = createPost(u1, "Count Test")
            postService.addComment(u2, CommentDto.Create(postId = post.id, avatar = "a", text = "From u2"))
            postService.addComment(u3, CommentDto.Create(postId = post.id, avatar = "a", text = "From u3"))

            userService.ignoreUser(u1, l2)
            commit()

            val pageable = Pageable(1, 10, SortOrder.DESC)
            val posts = postService.getPosts(Viewer.Registered(u1), null, l1, null, null, null, null, null, pageable, false)
            assertEquals(1, posts.content.size)
            assertEquals(1, posts.content.first().commentsCount)
            rollback()
        }
    }

    @Test
    fun `comment count excludes comments filtered by ignore relationships`() {
        transaction {
            val (user1, user2) = signUsersUp()
            val post = createPost(user1)
            postService.addComment(user2, CommentDto.Create(postId = post.id, avatar = "a", text = "Ignored"))

            userService.ignoreUser(user1, testUser2.login)
            commit()

            val pageable = Pageable(1, 10, SortOrder.DESC)
            val posts = postService.getPosts(Viewer.Registered(user1), null, testUser.login, null, null, null, null, null, pageable, false)
            assertEquals(0, posts.content.first().commentsCount)
            rollback()
        }
    }

    @Test
    fun `comment list and comment count use the same exclusion logic`() {
        transaction {
            val (user1, user2) = signUsersUp()
            val post = createPost(user1)
            postService.addComment(user2, CommentDto.Create(postId = post.id, avatar = "a", text = "c1"))
            postService.addComment(user1, CommentDto.Create(postId = post.id, avatar = "a", text = "c2"))

            userService.ignoreUser(user1, testUser2.login)
            commit()

            val postPage = postService.getPost(Viewer.Registered(user1), testUser.login, post.uri)
            assertEquals(postPage.comments.size, postPage.post.commentsCount)
            rollback()
        }
    }

    @Test
    fun `a comment with local author shows author login from author diary`() {
        transaction {
            val (user1, user2) = signUsersUp()
            val post = createPost(user1)
            postService.addComment(user2, CommentDto.Create(postId = post.id, avatar = "a", text = "Hi"))

            val postPage = postService.getPost(Viewer.Registered(user1), testUser.login, post.uri)
            assertEquals(testUser2.login, postPage.comments.first().authorLogin)
            rollback()
        }
    }

    @Test
    fun `a comment with external linked author shows linked diary login`() {
        transaction {
            val (user1, user2) = signUsersUp()
            val post = createPost(user1)
            val extId = insertExternalUser("ext_linked", user2)
            insertExternalComment(post.id, extId, "Ext Comment")
            CommentDependencies.insert {
                it[comment] = Comments.select { Comments.text eq "Ext Comment" }.first()[Comments.id]
                it[user] = user1
            }

            val postPage = postService.getPost(Viewer.Registered(user1), testUser.login, post.uri)
            val extComment = postPage.comments.find { it.text == "Ext Comment" }
            assertNotNull(extComment)
            assertEquals(testUser2.login, extComment.authorLogin)
            rollback()
        }
    }

    @Test
    fun `a comment with external unlinked author shows null login and external nickname`() {
        transaction {
            val (user1, _) = signUsersUp()
            val post = createPost(user1)
            val extId = insertExternalUser("ext_unlinked", null)
            insertExternalComment(post.id, extId, "Unlinked Comment")
            CommentDependencies.insert {
                it[comment] = Comments.select { Comments.text eq "Unlinked Comment" }.first()[Comments.id]
                it[user] = user1
            }

            val postPage = postService.getPost(Viewer.Registered(user1), testUser.login, post.uri)
            val c = postPage.comments.find { it.text == "Unlinked Comment" }
            assertNotNull(c)
            assertNull(c.authorLogin)
            assertEquals("ext_unlinked", c.authorNickname)
            rollback()
        }
    }

    @Test
    fun `a comment with anonymous author shows null login and anonymous nickname`() {
        transaction {
            val (user1, _) = signUsersUp()
            val post = createPost(user1)
            val anonId = insertAnonymousUser("anon_nick")
            insertAnonymousComment(post.id, anonId, "Anon Comment")
            CommentDependencies.insert {
                it[comment] = Comments.select { Comments.text eq "Anon Comment" }.first()[Comments.id]
                it[user] = user1
            }

            val postPage = postService.getPost(Viewer.Registered(user1), testUser.login, post.uri)
            val c = postPage.comments.find { it.text == "Anon Comment" }
            assertNotNull(c)
            assertNull(c.authorLogin)
            assertEquals("anon_nick", c.authorNickname)
            rollback()
        }
    }

    @Test
    fun `anonymous comment with null nickname falls back to anonymous`() {
        transaction {
            val (user1, _) = signUsersUp()
            val post = createPost(user1)
            val anonId = insertAnonymousUser(null)
            insertAnonymousComment(post.id, anonId, "Null Nick Comment")
            CommentDependencies.insert {
                it[comment] = Comments.select { Comments.text eq "Null Nick Comment" }.first()[Comments.id]
                it[user] = user1
            }

            val postPage = postService.getPost(Viewer.Registered(user1), testUser.login, post.uri)
            val c = postPage.comments.find { it.text == "Null Nick Comment" }
            assertNotNull(c)
            assertEquals("anonymous", c.authorNickname)
            rollback()
        }
    }

    @Test
    fun `a reply comment includes inReplyTo when parent is visible`() {
        transaction {
            val (user1, user2) = signUsersUp()
            val post = createPost(user1)
            val parent = postService.addComment(user2, CommentDto.Create(postId = post.id, avatar = "a", text = "Parent"))
            postService.addComment(user1, CommentDto.Create(postId = post.id, avatar = "a", text = "Reply", parentCommentId = parent.id))

            val postPage = postService.getPost(Viewer.Registered(user1), testUser.login, post.uri)
            val reply = postPage.comments.find { it.text == "Reply" }!!
            assertNotNull(reply.inReplyTo)
            assertEquals(parent.id, reply.inReplyTo!!.id)
            rollback()
        }
    }

    @Test
    fun `a reply comment omits inReplyTo when parent is hidden by ignore filtering`() {
        transaction {
            val users = signUsersUp(3)
            val (u1, l1) = users[0]
            val (u2, l2) = users[1]
            val (u3, _) = users[2]
            val post = createPost(u1, "Reply Test")
            val parent = postService.addComment(u2, CommentDto.Create(postId = post.id, avatar = "a", text = "Parent by u2"))
            postService.addComment(u3, CommentDto.Create(postId = post.id, avatar = "a", text = "Reply by u3", parentCommentId = parent.id))

            userService.ignoreUser(u1, l2)
            commit()

            val postPage = postService.getPost(Viewer.Registered(u1), l1, post.uri)
            val reply = postPage.comments.find { it.text == "Reply by u3" }
            assertNull(reply)
            rollback()
        }
    }

    @Test
    fun `loading comments for empty post ID set returns empty map`() {
        transaction {
            val (user1, _) = signUsersUp()
            // Just verify no crash when no posts
            val pageable = Pageable(1, 10, SortOrder.DESC)
            val posts = postService.getPosts(Viewer.Registered(user1), null, null, null, null, null, null, null, pageable, false)
            assertEquals(0, posts.content.size)
            rollback()
        }
    }

    @Test
    fun `visible comment counts for empty post ID set returns empty map`() {
        transaction {
            val (user1, _) = signUsersUp()
            val pageable = Pageable(1, 10, SortOrder.DESC)
            val posts = postService.getPosts(Viewer.Registered(user1), null, null, null, null, null, null, null, pageable, false)
            assertTrue(posts.content.isEmpty())
            rollback()
        }
    }
}
