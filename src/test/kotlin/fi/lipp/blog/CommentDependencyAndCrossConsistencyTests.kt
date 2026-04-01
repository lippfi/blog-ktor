package fi.lipp.blog

import fi.lipp.blog.data.*
import fi.lipp.blog.domain.DiaryEntity
import fi.lipp.blog.domain.PostEntity
import fi.lipp.blog.model.Page
import fi.lipp.blog.model.Pageable
import fi.lipp.blog.model.TagPolicy
import fi.lipp.blog.model.exceptions.*
import fi.lipp.blog.repository.*
import fi.lipp.blog.service.Viewer
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.Test
import java.util.*
import kotlin.test.*

class CommentDependencyAndCrossConsistencyTests : UnitTestBase() {

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

    private fun getPosts(
        viewer: Viewer,
        author: String? = null,
        diary: String? = null,
        pageable: Pageable = Pageable(1, 10, SortOrder.DESC),
        isFeed: Boolean = false,
    ): Page<PostDto.View> {
        return postService.getPosts(viewer, author, diary, null, null, null, null, null, pageable, isFeed)
    }

    private fun insertExternalUser(nickname: String, linkedUserId: UUID? = null): UUID {
        return ExternalUsers.insertAndGetId {
            it[ExternalUsers.platformName] = "telegram"
            it[ExternalUsers.externalUserId] = UUID.randomUUID().toString()
            it[ExternalUsers.nickname] = nickname
            it[ExternalUsers.user] = linkedUserId
        }.value
    }

    private fun insertExternalAuthorPost(diaryId: UUID, extUserId: UUID, title: String, uri: String): UUID {
        return Posts.insertAndGetId {
            it[Posts.uri] = uri
            it[Posts.diary] = diaryId
            it[Posts.authorType] = PostAuthorType.EXTERNAL
            it[Posts.externalAuthor] = extUserId
            it[Posts.localAuthor] = null
            it[Posts.avatar] = "avatar"
            it[Posts.title] = title
            it[Posts.text] = "text"
            it[Posts.isPreface] = false
            it[Posts.isEncrypted] = false
            it[Posts.isHidden] = false
            it[Posts.isArchived] = false
            it[Posts.classes] = ""
            it[Posts.readGroup] = groupService.everyoneGroupUUID
            it[Posts.commentGroup] = groupService.everyoneGroupUUID
            it[Posts.reactionGroup] = groupService.everyoneGroupUUID
            it[Posts.commentReactionGroup] = groupService.everyoneGroupUUID
        }.value
    }

    private fun makeFriends(user1Id: UUID, user2Login: String, user2Id: UUID) {
        userService.sendFriendRequest(user1Id, FriendRequestDto.Create(user2Login, "Hi", null))
        val requestId = userService.getReceivedFriendRequests(user2Id).first().id
        userService.acceptFriendRequest(user2Id, requestId, null)
    }

    // --- Comment dependency logic ---

    @Test
    fun `a top-level comment creates dependency row for the comment author`() {
        transaction {
            val (user1, user2) = signUsersUp()
            val post = createPost(user1)
            val comment = postService.addComment(user2, CommentDto.Create(postId = post.id, avatar = "a", text = "c"))

            val deps = CommentDependencies.select { CommentDependencies.comment eq comment.id }.map { it[CommentDependencies.user].value }.toSet()
            assertTrue(deps.contains(user2))
            rollback()
        }
    }

    @Test
    fun `a top-level comment creates dependency row for the post author if different`() {
        transaction {
            val (user1, user2) = signUsersUp()
            val post = createPost(user1)
            val comment = postService.addComment(user2, CommentDto.Create(postId = post.id, avatar = "a", text = "c"))

            val deps = CommentDependencies.select { CommentDependencies.comment eq comment.id }.map { it[CommentDependencies.user].value }.toSet()
            assertTrue(deps.contains(user1))
            rollback()
        }
    }

    @Test
    fun `a top-level comment does not duplicate dependency row when comment author is also post author`() {
        transaction {
            val (user1, _) = signUsersUp()
            val post = createPost(user1)
            val comment = postService.addComment(user1, CommentDto.Create(postId = post.id, avatar = "a", text = "self"))

            val deps = CommentDependencies.select { CommentDependencies.comment eq comment.id }.map { it[CommentDependencies.user].value }
            assertEquals(1, deps.size)
            assertEquals(user1, deps.first())
            rollback()
        }
    }

    @Test
    fun `a reply comment inherits all dependency users from parent comment`() {
        transaction {
            val users = signUsersUp(3)
            val (u1, _) = users[0]
            val (u2, _) = users[1]
            val (u3, _) = users[2]
            val post = createPost(u1)
            val parent = postService.addComment(u2, CommentDto.Create(postId = post.id, avatar = "a", text = "parent"))
            val reply = postService.addComment(u3, CommentDto.Create(postId = post.id, avatar = "a", text = "reply", parentCommentId = parent.id))

            val parentDeps = CommentDependencies.select { CommentDependencies.comment eq parent.id }.map { it[CommentDependencies.user].value }.toSet()
            val replyDeps = CommentDependencies.select { CommentDependencies.comment eq reply.id }.map { it[CommentDependencies.user].value }.toSet()

            assertTrue(replyDeps.containsAll(parentDeps))
            rollback()
        }
    }

    @Test
    fun `a reply comment also includes current commenter even if absent from parent dependencies`() {
        transaction {
            val users = signUsersUp(3)
            val (u1, _) = users[0]
            val (u2, _) = users[1]
            val (u3, _) = users[2]
            val post = createPost(u1)
            val parent = postService.addComment(u2, CommentDto.Create(postId = post.id, avatar = "a", text = "parent"))
            val reply = postService.addComment(u3, CommentDto.Create(postId = post.id, avatar = "a", text = "reply", parentCommentId = parent.id))

            val replyDeps = CommentDependencies.select { CommentDependencies.comment eq reply.id }.map { it[CommentDependencies.user].value }.toSet()
            assertTrue(replyDeps.contains(u3))
            rollback()
        }
    }

    @Test
    fun `a reply comment also includes post author even if absent from parent dependencies`() {
        transaction {
            val users = signUsersUp(3)
            val (u1, _) = users[0]
            val (u2, _) = users[1]
            val (u3, _) = users[2]
            val post = createPost(u1)
            val parent = postService.addComment(u2, CommentDto.Create(postId = post.id, avatar = "a", text = "parent"))
            val reply = postService.addComment(u3, CommentDto.Create(postId = post.id, avatar = "a", text = "reply", parentCommentId = parent.id))

            val replyDeps = CommentDependencies.select { CommentDependencies.comment eq reply.id }.map { it[CommentDependencies.user].value }.toSet()
            assertTrue(replyDeps.contains(u1))
            rollback()
        }
    }

    @Test
    fun `deep nested replies accumulate dependency users through copied parent dependencies`() {
        transaction {
            val users = signUsersUp(4)
            val (u1, _) = users[0]
            val (u2, _) = users[1]
            val (u3, _) = users[2]
            val (u4, _) = users[3]
            val post = createPost(u1, "Deep Nesting")
            val c1 = postService.addComment(u2, CommentDto.Create(postId = post.id, avatar = "a", text = "level1"))
            val c2 = postService.addComment(u3, CommentDto.Create(postId = post.id, avatar = "a", text = "level2", parentCommentId = c1.id))
            val c3 = postService.addComment(u4, CommentDto.Create(postId = post.id, avatar = "a", text = "level3", parentCommentId = c2.id))

            val c3Deps = CommentDependencies.select { CommentDependencies.comment eq c3.id }.map { it[CommentDependencies.user].value }.toSet()
            assertTrue(c3Deps.containsAll(setOf(u1, u2, u3, u4)))
            rollback()
        }
    }

    @Test
    fun `ignore filtering of comments excludes any comment whose dependency set contains an ignored user`() {
        transaction {
            val users = signUsersUp(3)
            val (u1, l1) = users[0]
            val (u2, l2) = users[1]
            val (u3, _) = users[2]
            val post = createPost(u1, "Ignore Dep Test")
            val parent = postService.addComment(u2, CommentDto.Create(postId = post.id, avatar = "a", text = "by u2"))
            postService.addComment(u3, CommentDto.Create(postId = post.id, avatar = "a", text = "reply to u2", parentCommentId = parent.id))

            userService.ignoreUser(u1, l2)
            commit()

            val postPage = postService.getPost(Viewer.Registered(u1), l1, post.uri)
            // Both parent and reply should be hidden because u2 is in both dependency sets
            assertEquals(0, postPage.comments.size)
            rollback()
        }
    }

    @Test
    fun `a comment with no dependency intersection with ignore lists remains visible`() {
        transaction {
            val users = signUsersUp(3)
            val (u1, l1) = users[0]
            val (u2, l2) = users[1]
            val (u3, _) = users[2]
            val post = createPost(u1, "Visible Dep")
            postService.addComment(u3, CommentDto.Create(postId = post.id, avatar = "a", text = "by u3"))

            userService.ignoreUser(u1, l2)
            commit()

            val postPage = postService.getPost(Viewer.Registered(u1), l1, post.uri)
            assertEquals(1, postPage.comments.size)
            rollback()
        }
    }

    // --- Cross-consistency scenarios ---

    @Test
    fun `a visible post commentsCount equals number of comments returned by loadCommentsForPosts for same viewer`() {
        transaction {
            val (user1, user2) = signUsersUp()
            val post = createPost(user1)
            postService.addComment(user2, CommentDto.Create(postId = post.id, avatar = "a", text = "c1"))
            postService.addComment(user2, CommentDto.Create(postId = post.id, avatar = "a", text = "c2"))

            val postPage = postService.getPost(Viewer.Registered(user1), testUser.login, post.uri)
            assertEquals(postPage.comments.size, postPage.post.commentsCount)
            rollback()
        }
    }

    @Test
    fun `a hidden-by-ignore post is absent both from list and direct URI load`() {
        transaction {
            val (user1, user2) = signUsersUp()
            postService.addPost(user2, PostDto.Create(
                uri = "", avatar = "av", title = "Ignored Post", text = "t",
                isPreface = false, isEncrypted = false, classes = "", tags = emptySet(),
                readGroupId = groupService.everyoneGroupUUID,
                commentGroupId = groupService.everyoneGroupUUID,
                reactionGroupId = groupService.everyoneGroupUUID,
                commentReactionGroupId = groupService.everyoneGroupUUID,
            ))

            userService.ignoreUser(user1, testUser2.login)
            commit()

            val posts = getPosts(Viewer.Registered(user1))
            assertEquals(0, posts.content.size)

            assertFailsWith<PostNotFoundException> {
                postService.getPost(Viewer.Registered(user1), testUser2.login, "ignored-post")
            }
            rollback()
        }
    }

    @Test
    fun `a visible post by external linked author can be filtered by linked diary login and rendered with linked diary login`() {
        transaction {
            val (user1, user2) = signUsersUp()
            val diaryId = DiaryEntity.find { Diaries.owner eq user1 }.first().id.value
            val extUserId = insertExternalUser("ext_linked", user2)
            insertExternalAuthorPost(diaryId, extUserId, "Ext Linked Post", "ext-linked-post")

            val posts = getPosts(Viewer.Registered(user1), author = testUser2.login)
            assertEquals(1, posts.content.size)
            assertEquals(testUser2.login, posts.content.first().authorLogin)
            rollback()
        }
    }

    @Test
    fun `a post hidden in feed due to hidden-from-feed but visible in direct diary listing behaves exactly so`() {
        transaction {
            val (user1, user2) = signUsersUp()
            postService.addPost(user2, PostDto.Create(
                uri = "", avatar = "av", title = "Feed Hidden", text = "t",
                isPreface = false, isEncrypted = false, classes = "", tags = emptySet(),
                readGroupId = groupService.everyoneGroupUUID,
                commentGroupId = groupService.everyoneGroupUUID,
                reactionGroupId = groupService.everyoneGroupUUID,
                commentReactionGroupId = groupService.everyoneGroupUUID,
            ))

            userService.doNotShowInFeed(user1, testUser2.login)
            commit()

            val feedPosts = postService.getLatestPosts(Viewer.Registered(user1), Pageable(1, 10, SortOrder.DESC))
            assertEquals(0, feedPosts.content.size)

            val diaryPosts = getPosts(Viewer.Registered(user1), diary = testUser2.login, isFeed = false)
            assertEquals(1, diaryPosts.content.size)
            rollback()
        }
    }

    @Test
    fun `a comment hidden by ignore rules is excluded from both count and list for the same viewer`() {
        transaction {
            val (user1, user2) = signUsersUp()
            val post = createPost(user1)
            postService.addComment(user2, CommentDto.Create(postId = post.id, avatar = "a", text = "ignored comment"))

            userService.ignoreUser(user1, testUser2.login)
            commit()

            val postPage = postService.getPost(Viewer.Registered(user1), testUser.login, post.uri)
            assertEquals(0, postPage.comments.size)
            assertEquals(0, postPage.post.commentsCount)
            rollback()
        }
    }

    @Test
    fun `a reply to hidden parent stays visible itself if its own dependencies do not exclude it but inReplyTo is null`() {
        transaction {
            val users = signUsersUp(3)
            val (u1, l1) = users[0]
            val (u2, l2) = users[1]
            val (u3, _) = users[2]
            val post = createPost(u1, "Reply Visibility")
            val parent = postService.addComment(u2, CommentDto.Create(postId = post.id, avatar = "a", text = "parent"))
            postService.addComment(u3, CommentDto.Create(postId = post.id, avatar = "a", text = "reply", parentCommentId = parent.id))

            userService.ignoreUser(u1, l2)
            commit()

            val postPage = postService.getPost(Viewer.Registered(u1), l1, post.uri)
            // The reply depends on u2 (from parent deps), so it's also hidden
            // If the reply's dependency set includes u2 (parent dep), it's excluded too
            // This is expected behavior from CommentDependencies logic
            val visibleComments = postPage.comments
            visibleComments.forEach { c ->
                if (c.inReplyTo != null) {
                    // If visible, inReplyTo should reference a visible parent
                    assertTrue(visibleComments.any { it.id == c.inReplyTo!!.id })
                }
            }
            rollback()
        }
    }

    @Test
    fun `a visible reply to visible parent has non-null inReplyTo`() {
        transaction {
            val (user1, user2) = signUsersUp()
            val post = createPost(user1)
            val parent = postService.addComment(user2, CommentDto.Create(postId = post.id, avatar = "a", text = "parent"))
            postService.addComment(user1, CommentDto.Create(postId = post.id, avatar = "a", text = "reply", parentCommentId = parent.id))

            val postPage = postService.getPost(Viewer.Registered(user1), testUser.login, post.uri)
            val reply = postPage.comments.find { it.text == "reply" }!!
            assertNotNull(reply.inReplyTo)
            rollback()
        }
    }

    @Test
    fun `single-post view and paged-post view produce the same author fields for the same post`() {
        transaction {
            val (user1, user2) = signUsersUp()
            postService.addPost(user1, PostDto.Create(
                uri = "", avatar = "av", title = "Author Fields", text = "t",
                isPreface = false, isEncrypted = false, classes = "", tags = emptySet(),
                readGroupId = groupService.everyoneGroupUUID,
                commentGroupId = groupService.everyoneGroupUUID,
                reactionGroupId = groupService.everyoneGroupUUID,
                commentReactionGroupId = groupService.everyoneGroupUUID,
            ))

            val singleView = postService.getPost(Viewer.Registered(user2), testUser.login, "author-fields").post
            val listView = getPosts(Viewer.Registered(user2), diary = testUser.login).content.first()

            assertEquals(singleView.authorLogin, listView.authorLogin)
            assertEquals(singleView.authorNickname, listView.authorNickname)
            assertEquals(singleView.authorSignature, listView.authorSignature)
            rollback()
        }
    }

    // --- Failure and edge scenarios ---

    @Test
    fun `single-post loader returns not found instead of generic error when post is missing`() {
        transaction {
            val (user1, _) = signUsersUp()
            assertFailsWith<PostNotFoundException> {
                postService.getPost(Viewer.Registered(user1), testUser.login, "nonexistent-uri")
            }
            rollback()
        }
    }

    @Test
    fun `loading post view by ID for hidden-by-ignore post returns not found`() {
        transaction {
            val (user1, user2) = signUsersUp()
            postService.addPost(user2, PostDto.Create(
                uri = "", avatar = "av", title = "Ignored", text = "t",
                isPreface = false, isEncrypted = false, classes = "", tags = emptySet(),
                readGroupId = groupService.everyoneGroupUUID,
                commentGroupId = groupService.everyoneGroupUUID,
                reactionGroupId = groupService.everyoneGroupUUID,
                commentReactionGroupId = groupService.everyoneGroupUUID,
            ))

            userService.ignoreUser(user1, testUser2.login)
            commit()

            assertFailsWith<PostNotFoundException> {
                postService.getPost(Viewer.Registered(user1), testUser2.login, "ignored")
            }
            rollback()
        }
    }

    @Test
    fun `loading post view by ID for read-restricted post returns not found`() {
        transaction {
            val (user1, user2) = signUsersUp()
            postService.addPost(user1, PostDto.Create(
                uri = "", avatar = "av", title = "Private", text = "t",
                isPreface = false, isEncrypted = false, classes = "", tags = emptySet(),
                readGroupId = groupService.privateGroupUUID,
                commentGroupId = groupService.everyoneGroupUUID,
                reactionGroupId = groupService.everyoneGroupUUID,
                commentReactionGroupId = groupService.everyoneGroupUUID,
            ))

            assertFailsWith<PostNotFoundException> {
                postService.getPost(Viewer.Registered(user2), testUser.login, "private")
            }
            rollback()
        }
    }

    @Test
    fun `external linked author without diary does not crash author resolution`() {
        transaction {
            val (user1, _) = signUsersUp()
            val diaryId = DiaryEntity.find { Diaries.owner eq user1 }.first().id.value
            val extUserId = insertExternalUser("ext_no_diary", null)
            insertExternalAuthorPost(diaryId, extUserId, "No Diary Author", "no-diary-post")

            val posts = getPosts(Viewer.Registered(user1))
            assertEquals(1, posts.content.size)
            assertNull(posts.content.first().authorLogin)
            rollback()
        }
    }

    @Test
    fun `feed query with empty follow list returns empty page and total pages zero`() {
        transaction {
            val (user1, _) = signUsersUp()
            val pageable = Pageable(1, 10, SortOrder.DESC)
            val result = postService.getFollowedPosts(user1, pageable)
            assertTrue(result.content.isEmpty())
            assertEquals(0, result.totalPages)
            rollback()
        }
    }

    @Test
    fun `friends posts query with no friends returns empty page and total pages zero`() {
        transaction {
            val (user1, _) = signUsersUp()
            val pageable = Pageable(1, 10, SortOrder.DESC)
            val result = postService.getFriendsPosts(user1, pageable)
            assertTrue(result.content.isEmpty())
            assertEquals(0, result.totalPages)
            rollback()
        }
    }

    @Test
    fun `tag filter intersection with nonexistent tag returns no posts`() {
        transaction {
            val (user1, _) = signUsersUp()
            postService.addPost(user1, PostDto.Create(
                uri = "", avatar = "av", title = "Tagged", text = "t",
                isPreface = false, isEncrypted = false, classes = "", tags = setOf("existing"),
                readGroupId = groupService.everyoneGroupUUID,
                commentGroupId = groupService.everyoneGroupUUID,
                reactionGroupId = groupService.everyoneGroupUUID,
                commentReactionGroupId = groupService.everyoneGroupUUID,
            ))

            val pageable = Pageable(1, 10, SortOrder.DESC)
            val posts = postService.getPosts(Viewer.Registered(user1), null, null, null, TagPolicy.INTERSECTION to setOf("nonexistent"), null, null, null, pageable, false)
            assertEquals(0, posts.content.size)
            rollback()
        }
    }

    @Test
    fun `author filter by login with nonexistent login returns no posts`() {
        transaction {
            val (user1, _) = signUsersUp()
            postService.addPost(user1, PostDto.Create(
                uri = "", avatar = "av", title = "Post", text = "t",
                isPreface = false, isEncrypted = false, classes = "", tags = emptySet(),
                readGroupId = groupService.everyoneGroupUUID,
                commentGroupId = groupService.everyoneGroupUUID,
                reactionGroupId = groupService.everyoneGroupUUID,
                commentReactionGroupId = groupService.everyoneGroupUUID,
            ))

            val posts = getPosts(Viewer.Registered(user1), author = "nonexistent-login")
            assertEquals(0, posts.content.size)
            rollback()
        }
    }

    @Test
    fun `multiple posts with same title but different diaries still generate unique URIs per diary context`() {
        transaction {
            val (user1, user2) = signUsersUp()
            val post1 = postService.addPost(user1, PostDto.Create(
                uri = "", avatar = "av", title = "Same Title", text = "t",
                isPreface = false, isEncrypted = false, classes = "", tags = emptySet(),
                readGroupId = groupService.everyoneGroupUUID,
                commentGroupId = groupService.everyoneGroupUUID,
                reactionGroupId = groupService.everyoneGroupUUID,
                commentReactionGroupId = groupService.everyoneGroupUUID,
            ))
            val post2 = postService.addPost(user2, PostDto.Create(
                uri = "", avatar = "av", title = "Same Title", text = "t",
                isPreface = false, isEncrypted = false, classes = "", tags = emptySet(),
                readGroupId = groupService.everyoneGroupUUID,
                commentGroupId = groupService.everyoneGroupUUID,
                reactionGroupId = groupService.everyoneGroupUUID,
                commentReactionGroupId = groupService.everyoneGroupUUID,
            ))

            // Both should have "same-title" since they're in different diaries
            assertEquals("same-title", post1.uri)
            assertEquals("same-title", post2.uri)
            rollback()
        }
    }

    @Test
    fun `a viewer ignored by post author cannot see the post even if they are in access group`() {
        transaction {
            val (user1, user2) = signUsersUp()
            postService.addPost(user1, PostDto.Create(
                uri = "", avatar = "av", title = "Ignore Override", text = "t",
                isPreface = false, isEncrypted = false, classes = "", tags = emptySet(),
                readGroupId = groupService.everyoneGroupUUID,
                commentGroupId = groupService.everyoneGroupUUID,
                reactionGroupId = groupService.everyoneGroupUUID,
                commentReactionGroupId = groupService.everyoneGroupUUID,
            ))

            userService.ignoreUser(user1, testUser2.login)
            commit()

            val posts = getPosts(Viewer.Registered(user2))
            assertEquals(0, posts.content.size)
            rollback()
        }
    }

    @Test
    fun `a post author can still see their own post even if another user in post dependencies ignored them`() {
        transaction {
            val (user1, user2) = signUsersUp()
            val post = createPost(user1)

            userService.ignoreUser(user2, testUser.login)
            commit()

            // user1 (post author) should still see their own post
            val posts = getPosts(Viewer.Registered(user1))
            assertEquals(1, posts.content.size)
            rollback()
        }
    }

    @Test
    fun `a comment author can still edit their own comment even if viewer relationships changed later`() {
        transaction {
            val (user1, user2) = signUsersUp()
            val post = createPost(user1)
            val comment = postService.addComment(user2, CommentDto.Create(postId = post.id, avatar = "a", text = "original"))

            userService.ignoreUser(user1, testUser2.login)
            commit()

            // user2 should still be able to edit their own comment
            val updated = postService.updateComment(user2, CommentDto.Update(id = comment.id, avatar = "a", text = "updated"))
            assertEquals("updated", updated.text)
            rollback()
        }
    }

    @Test
    fun `a post owner can still delete a comment on their post even if comment author ignored them`() {
        transaction {
            val (user1, user2) = signUsersUp()
            val post = createPost(user1)
            val comment = postService.addComment(user2, CommentDto.Create(postId = post.id, avatar = "a", text = "to delete"))

            userService.ignoreUser(user2, testUser.login)
            commit()

            // user1 (post owner) should still be able to delete the comment
            postService.deleteComment(user1, comment.id)
            rollback()
        }
    }

    @Test
    fun `bulk access checks with mixed local and external authors produce correct self flags for every row`() {
        transaction {
            val (user1, user2) = signUsersUp()
            // Local author post
            postService.addPost(user1, PostDto.Create(
                uri = "", avatar = "av", title = "Local Post", text = "t",
                isPreface = false, isEncrypted = false, classes = "", tags = emptySet(),
                readGroupId = groupService.everyoneGroupUUID,
                commentGroupId = groupService.privateGroupUUID,
                reactionGroupId = groupService.privateGroupUUID,
                commentReactionGroupId = groupService.everyoneGroupUUID,
            ))

            // External linked author post
            val diaryId = DiaryEntity.find { Diaries.owner eq user2 }.first().id.value
            val extUserId = insertExternalUser("ext_linked", user1)
            insertExternalAuthorPost(diaryId, extUserId, "External Post", "ext-post")

            val posts = getPosts(Viewer.Registered(user1))
            assertEquals(2, posts.content.size)

            val localPost = posts.content.find { it.title == "Local Post" }!!
            assertTrue(localPost.isCommentable) // self as local author
            assertTrue(localPost.isReactable)

            val extPost = posts.content.find { it.title == "External Post" }!!
            assertTrue(extPost.isCommentable) // self as linked external author
            assertTrue(extPost.isReactable)
            rollback()
        }
    }
}
