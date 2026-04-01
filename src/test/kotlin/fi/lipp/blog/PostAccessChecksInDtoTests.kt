package fi.lipp.blog

import fi.lipp.blog.data.*
import fi.lipp.blog.domain.DiaryEntity
import fi.lipp.blog.model.Page
import fi.lipp.blog.model.Pageable
import fi.lipp.blog.model.TagPolicy
import fi.lipp.blog.repository.*
import fi.lipp.blog.service.Viewer
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.Test
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PostAccessChecksInDtoTests : UnitTestBase() {

    private fun createPostPostData(
        uri: String = "",
        avatar: String = "avatar url",
        title: String = "sample title",
        text: String = "sample text",
        isPreface: Boolean = false,
        isEncrypted: Boolean = false,
        isHidden: Boolean = false,
        classes: String = "bold",
        tags: Set<String> = emptySet(),
        readGroup: UUID = groupService.everyoneGroupUUID,
        commentGroup: UUID = groupService.everyoneGroupUUID,
        reactionGroup: UUID = groupService.everyoneGroupUUID,
        commentReactionGroup: UUID = reactionGroup,
    ): PostDto.Create {
        return PostDto.Create(
            uri = uri,
            avatar = avatar,
            title = title,
            text = text,
            isPreface = isPreface,
            isEncrypted = isEncrypted,
            isHidden = isHidden,
            classes = classes,
            tags = tags,
            readGroupId = readGroup,
            commentGroupId = commentGroup,
            reactionGroupId = reactionGroup,
            commentReactionGroupId = commentReactionGroup,
        )
    }

    private fun getPosts(
        viewer: Viewer,
        pageable: Pageable = Pageable(1, 10, SortOrder.DESC),
    ): Page<PostDto.View> {
        return postService.getPosts(viewer, null, null, null, null, null, null, null, pageable, isFeed = false)
    }

    private fun insertExternalUser(
        platformName: String = "telegram",
        externalUserId: String = UUID.randomUUID().toString(),
        nickname: String = "ext_nick",
        linkedUserId: UUID? = null,
    ): UUID {
        return ExternalUsers.insertAndGetId {
            it[ExternalUsers.platformName] = platformName
            it[ExternalUsers.externalUserId] = externalUserId
            it[ExternalUsers.nickname] = nickname
            it[ExternalUsers.user] = linkedUserId
        }.value
    }

    private fun insertExternalAuthorPost(
        diaryId: UUID,
        externalUserId: UUID,
        title: String,
        uri: String,
        readGroupId: UUID = groupService.everyoneGroupUUID,
        commentGroupId: UUID = groupService.everyoneGroupUUID,
        reactionGroupId: UUID = groupService.everyoneGroupUUID,
        commentReactionGroupId: UUID = groupService.everyoneGroupUUID,
    ): UUID {
        return Posts.insertAndGetId {
            it[Posts.uri] = uri
            it[Posts.diary] = diaryId
            it[Posts.authorType] = PostAuthorType.EXTERNAL
            it[Posts.externalAuthor] = externalUserId
            it[Posts.localAuthor] = null
            it[Posts.avatar] = "avatar"
            it[Posts.title] = title
            it[Posts.text] = "text"
            it[Posts.isPreface] = false
            it[Posts.isEncrypted] = false
            it[Posts.isHidden] = false
            it[Posts.isArchived] = false
            it[Posts.classes] = ""
            it[Posts.readGroup] = readGroupId
            it[Posts.commentGroup] = commentGroupId
            it[Posts.reactionGroup] = reactionGroupId
            it[Posts.commentReactionGroup] = commentReactionGroupId
        }.value
    }

    // --- Post access checks in DTO ---

    @Test
    fun `a post author has isCommentable true even if comment group denies others`() {
        transaction {
            val (user1, _) = signUsersUp()
            postService.addPost(user1, createPostPostData(
                title = "Author Comment Test",
                commentGroup = groupService.privateGroupUUID
            ))

            val posts = getPosts(Viewer.Registered(user1))
            assertEquals(1, posts.content.size)
            assertTrue(posts.content.first().isCommentable)

            rollback()
        }
    }

    @Test
    fun `a post author has isReactable true even if reaction group denies others`() {
        transaction {
            val (user1, _) = signUsersUp()
            postService.addPost(user1, createPostPostData(
                title = "Author React Test",
                reactionGroup = groupService.privateGroupUUID
            ))

            val posts = getPosts(Viewer.Registered(user1))
            assertEquals(1, posts.content.size)
            assertTrue(posts.content.first().isReactable)

            rollback()
        }
    }

    @Test
    fun `a non-author with comment-group membership has isCommentable true`() {
        transaction {
            val (user1, user2) = signUsersUp()
            postService.addPost(user1, createPostPostData(
                title = "Comment Check",
                commentGroup = groupService.everyoneGroupUUID
            ))

            val posts = getPosts(Viewer.Registered(user2))
            assertEquals(1, posts.content.size)
            assertTrue(posts.content.first().isCommentable)

            rollback()
        }
    }

    @Test
    fun `a non-author without comment-group membership has isCommentable false`() {
        transaction {
            val (user1, user2) = signUsersUp()
            postService.addPost(user1, createPostPostData(
                title = "No Comment Check",
                commentGroup = groupService.privateGroupUUID
            ))

            val posts = getPosts(Viewer.Registered(user2))
            assertEquals(1, posts.content.size)
            assertFalse(posts.content.first().isCommentable)

            rollback()
        }
    }

    @Test
    fun `a non-author with reaction-group membership has isReactable true`() {
        transaction {
            val (user1, user2) = signUsersUp()
            postService.addPost(user1, createPostPostData(
                title = "React Check",
                reactionGroup = groupService.everyoneGroupUUID
            ))

            val posts = getPosts(Viewer.Registered(user2))
            assertEquals(1, posts.content.size)
            assertTrue(posts.content.first().isReactable)

            rollback()
        }
    }

    @Test
    fun `a non-author without reaction-group membership has isReactable false`() {
        transaction {
            val (user1, user2) = signUsersUp()
            postService.addPost(user1, createPostPostData(
                title = "No React Check",
                reactionGroup = groupService.privateGroupUUID
            ))

            val posts = getPosts(Viewer.Registered(user2))
            assertEquals(1, posts.content.size)
            assertFalse(posts.content.first().isReactable)

            rollback()
        }
    }

    @Test
    fun `access checks are correct for a local author row`() {
        transaction {
            val (user1, user2) = signUsersUp()
            postService.addPost(user1, createPostPostData(
                title = "Local Author Check",
                commentGroup = groupService.privateGroupUUID,
                reactionGroup = groupService.privateGroupUUID
            ))

            // Author sees true/true
            val ownerPosts = getPosts(Viewer.Registered(user1))
            assertTrue(ownerPosts.content.first().isCommentable)
            assertTrue(ownerPosts.content.first().isReactable)

            // Non-author sees false/false
            val otherPosts = getPosts(Viewer.Registered(user2))
            assertFalse(otherPosts.content.first().isCommentable)
            assertFalse(otherPosts.content.first().isReactable)

            rollback()
        }
    }

    @Test
    fun `access checks are correct for an external linked author row`() {
        transaction {
            val (user1, user2) = signUsersUp()
            val diaryId = DiaryEntity.find { Diaries.owner eq user2 }.first().id.value
            val extUserId = insertExternalUser(nickname = "ext_linked", linkedUserId = user2)
            insertExternalAuthorPost(
                diaryId, extUserId, "Ext Author Check", "ext-author-check",
                commentGroupId = groupService.privateGroupUUID,
                reactionGroupId = groupService.privateGroupUUID
            )

            // Linked author (user2) sees true/true
            val authorPosts = getPosts(Viewer.Registered(user2))
            assertTrue(authorPosts.content.first().isCommentable)
            assertTrue(authorPosts.content.first().isReactable)

            // Non-author (user1) sees false/false
            val otherPosts = getPosts(Viewer.Registered(user1))
            assertFalse(otherPosts.content.first().isCommentable)
            assertFalse(otherPosts.content.first().isReactable)

            rollback()
        }
    }

    @Test
    fun `bulk access checks for multiple posts return correct values per post`() {
        transaction {
            val (user1, user2) = signUsersUp()
            postService.addPost(user1, createPostPostData(
                title = "Post Commentable",
                commentGroup = groupService.everyoneGroupUUID,
                reactionGroup = groupService.privateGroupUUID
            ))
            postService.addPost(user1, createPostPostData(
                title = "Post Reactable",
                commentGroup = groupService.privateGroupUUID,
                reactionGroup = groupService.everyoneGroupUUID
            ))

            val posts = getPosts(Viewer.Registered(user2))
            assertEquals(2, posts.content.size)

            val commentable = posts.content.find { it.title == "Post Commentable" }!!
            assertTrue(commentable.isCommentable)
            assertFalse(commentable.isReactable)

            val reactable = posts.content.find { it.title == "Post Reactable" }!!
            assertFalse(reactable.isCommentable)
            assertTrue(reactable.isReactable)

            rollback()
        }
    }

    @Test
    fun `bulk access checks do not require access-group lookups for self-authored posts`() {
        transaction {
            val (user1, _) = signUsersUp()
            // Create multiple posts with private groups - author should still see true for all
            postService.addPost(user1, createPostPostData(
                title = "Self Post 1",
                commentGroup = groupService.privateGroupUUID,
                reactionGroup = groupService.privateGroupUUID
            ))
            postService.addPost(user1, createPostPostData(
                title = "Self Post 2",
                commentGroup = groupService.privateGroupUUID,
                reactionGroup = groupService.privateGroupUUID
            ))

            val posts = getPosts(Viewer.Registered(user1))
            assertEquals(2, posts.content.size)
            posts.content.forEach { post ->
                assertTrue(post.isCommentable)
                assertTrue(post.isReactable)
            }

            rollback()
        }
    }
}
