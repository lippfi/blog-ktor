package fi.lipp.blog

import fi.lipp.blog.data.PostDto
import fi.lipp.blog.model.Page
import fi.lipp.blog.model.Pageable
import fi.lipp.blog.repository.*
import fi.lipp.blog.service.Viewer
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.Test
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PostFilteringTests : UnitTestBase() {

    private fun createPostData(
        uri: String = "",
        avatar: String = "avatar url",
        title: String = "sample title",
        text: String = "sample text",
        isPreface: Boolean = false,
        isEncrypted: Boolean = false,
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
            classes = classes,
            tags = tags,
            readGroupId = readGroup,
            commentGroupId = commentGroup,
            reactionGroupId = reactionGroup,
            commentReactionGroupId = commentReactionGroup,
        )
    }

    @Test
    fun `test posts from ignored users are not returned`() {
        transaction {
            // Set up two users: author1 and author2
            val users = signUsersUp(3)
            val (author1Id, author1Login) = users[0]
            val (author2Id, author2Login) = users[1]
            val (viewerId, _) = users[2]

            // Author1 creates a post
            val post1 = createPostData(title = "Post from Author 1")
            postService.addPost(author1Id, post1)

            // Author2 creates a post
            val post2 = createPostData(title = "Post from Author 2")
            postService.addPost(author2Id, post2)

            // Verify both posts are visible initially
            var posts = postService.getLatestPosts(Viewer.Registered(viewerId), Pageable(1, 10, SortOrder.DESC))
            assertEquals(2, posts.content.size)

            // Viewer ignores author1
            userService.ignoreUser(viewerId, author1Login)

            commit()

            // Verify that author1's post is not visible
            posts = postService.getLatestPosts(Viewer.Registered(viewerId), Pageable(1, 10, SortOrder.DESC))
            assertEquals(1, posts.content.size)
            assertEquals("Post from Author 2", posts.content[0].title)

            rollback()
        }
    }

    @Test
    fun `test posts reappear after user is unignored`() {
        transaction {
            // Set up two users: author1 and author2
            val users = signUsersUp(3)
            val (author1Id, author1Login) = users[0]
            val (author2Id, _) = users[1]
            val (viewerId, _) = users[2]

            // Author1 creates a post
            val post1 = createPostData(title = "Post from Author 1")
            postService.addPost(author1Id, post1)

            // Author2 creates a post
            val post2 = createPostData(title = "Post from Author 2")
            postService.addPost(author2Id, post2)

            // Viewer ignores author1
            userService.ignoreUser(viewerId, author1Login)

            commit()

            // Verify that author1's post is not visible
            var posts = postService.getLatestPosts(Viewer.Registered(viewerId), Pageable(1, 10, SortOrder.DESC))
            assertEquals(1, posts.content.size)
            assertEquals("Post from Author 2", posts.content[0].title)

            // Viewer unignores author1
            userService.unignoreUser(viewerId, author1Login)

            commit()

            // Verify both posts are visible again
            posts = postService.getLatestPosts(Viewer.Registered(viewerId), Pageable(1, 10, SortOrder.DESC))
            assertEquals(2, posts.content.size)

            rollback()
        }
    }

    @Test
    fun `test posts are hidden when author ignores viewer`() {
        transaction {
            // Set up two users: author and viewer
            val users = signUsersUp(2)
            val (authorId, _) = users[0]
            val (viewerId, viewerLogin) = users[1]

            // Author creates a post
            val post = createPostData(title = "Test Post")
            postService.addPost(authorId, post)

            // Verify post is visible to viewer initially
            var posts = postService.getLatestPosts(Viewer.Registered(viewerId), Pageable(1, 10, SortOrder.DESC))
            assertEquals(1, posts.content.size)

            // Author ignores viewer
            userService.ignoreUser(authorId, viewerLogin)

            commit()

            // Verify post is not visible to viewer anymore
            posts = postService.getLatestPosts(Viewer.Registered(viewerId), Pageable(1, 10, SortOrder.DESC))
            assertEquals(0, posts.content.size)

            rollback()
        }
    }
}