package fi.lipp.blog

import fi.lipp.blog.data.*
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
import kotlin.test.assertTrue

class PostPaginationAndOrderingTests : UnitTestBase() {

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
        author: String? = null,
        diary: String? = null,
        pattern: String? = null,
        tags: Pair<TagPolicy, Set<String>>? = null,
        isHidden: Boolean? = null,
        pageable: Pageable = Pageable(1, 10, SortOrder.DESC),
        isFeed: Boolean = false,
    ): Page<PostDto.View> {
        return postService.getPosts(viewer, author, diary, pattern, tags, null, null, isHidden, pageable, isFeed)
    }

    // --- Post pagination and ordering ---

    @Test
    fun `pagination returns the correct number of posts for the first page`() {
        transaction {
            val (user1, _) = signUsersUp()
            repeat(5) { i ->
                postService.addPost(user1, createPostPostData(title = "Post $i"))
            }

            val page = getPosts(Viewer.Registered(user1), pageable = Pageable(1, 3, SortOrder.DESC))
            assertEquals(3, page.content.size)
            assertEquals(1, page.currentPage)

            rollback()
        }
    }

    @Test
    fun `pagination returns the correct number of posts for a middle page`() {
        transaction {
            val (user1, _) = signUsersUp()
            repeat(7) { i ->
                postService.addPost(user1, createPostPostData(title = "Post $i"))
            }

            val page = getPosts(Viewer.Registered(user1), pageable = Pageable(2, 3, SortOrder.DESC))
            assertEquals(3, page.content.size)
            assertEquals(2, page.currentPage)

            rollback()
        }
    }

    @Test
    fun `pagination returns fewer posts on the last page when remainder exists`() {
        transaction {
            val (user1, _) = signUsersUp()
            repeat(5) { i ->
                postService.addPost(user1, createPostPostData(title = "Post $i"))
            }

            val page = getPosts(Viewer.Registered(user1), pageable = Pageable(2, 3, SortOrder.DESC))
            assertEquals(2, page.content.size)

            rollback()
        }
    }

    @Test
    fun `pagination returns empty content when page number is beyond total pages`() {
        transaction {
            val (user1, _) = signUsersUp()
            repeat(3) { i ->
                postService.addPost(user1, createPostPostData(title = "Post $i"))
            }

            val page = getPosts(Viewer.Registered(user1), pageable = Pageable(5, 3, SortOrder.DESC))
            assertEquals(0, page.content.size)

            rollback()
        }
    }

    @Test
    fun `total pages is zero when query returns no rows`() {
        transaction {
            val (user1, _) = signUsersUp()

            val page = getPosts(Viewer.Registered(user1), pageable = Pageable(1, 10, SortOrder.DESC))
            assertEquals(0, page.totalPages)

            rollback()
        }
    }

    @Test
    fun `total pages is one when results fit in one page`() {
        transaction {
            val (user1, _) = signUsersUp()
            repeat(3) { i ->
                postService.addPost(user1, createPostPostData(title = "Post $i"))
            }

            val page = getPosts(Viewer.Registered(user1), pageable = Pageable(1, 10, SortOrder.DESC))
            assertEquals(1, page.totalPages)

            rollback()
        }
    }

    @Test
    fun `ordering by creation time descending returns newest posts first`() {
        transaction {
            val (user1, _) = signUsersUp()
            postService.addPost(user1, createPostPostData(title = "First"))
            Thread.sleep(50)
            postService.addPost(user1, createPostPostData(title = "Second"))
            Thread.sleep(50)
            postService.addPost(user1, createPostPostData(title = "Third"))

            val page = getPosts(Viewer.Registered(user1), pageable = Pageable(1, 10, SortOrder.DESC))
            assertEquals("Third", page.content[0].title)
            assertEquals("Second", page.content[1].title)
            assertEquals("First", page.content[2].title)

            rollback()
        }
    }

    @Test
    fun `ordering by creation time ascending returns oldest posts first`() {
        transaction {
            val (user1, _) = signUsersUp()
            postService.addPost(user1, createPostPostData(title = "First"))
            Thread.sleep(50)
            postService.addPost(user1, createPostPostData(title = "Second"))
            Thread.sleep(50)
            postService.addPost(user1, createPostPostData(title = "Third"))

            val page = getPosts(Viewer.Registered(user1), pageable = Pageable(1, 10, SortOrder.ASC))
            assertEquals("First", page.content[0].title)
            assertEquals("Second", page.content[1].title)
            assertEquals("Third", page.content[2].title)

            rollback()
        }
    }

    @Test
    fun `ordering by discussed posts uses lastCommentTime descending and creation time as secondary sort`() {
        transaction {
            val (user1, user2) = signUsersUp()
            val post1 = postService.addPost(user1, createPostPostData(title = "Post A"))
            Thread.sleep(50)
            val post2 = postService.addPost(user1, createPostPostData(title = "Post B"))
            Thread.sleep(50)
            val post3 = postService.addPost(user1, createPostPostData(title = "Post C"))

            // Comment on post1 last to make it most recently discussed
            postService.addComment(user2, CommentDto.Create(postId = post1.id, avatar = "av", text = "comment"))

            val page = postService.getDiscussedPosts(Viewer.Registered(user1), Pageable(1, 10, SortOrder.DESC))
            // post1 should be first because it has the latest comment
            assertEquals("Post A", page.content[0].title)

            rollback()
        }
    }

    @Test
    fun `ordering by preface-first and creation-time works correctly in diary posts`() {
        transaction {
            val (user1, user2) = signUsersUp()
            postService.addPost(user1, createPostPostData(title = "Regular Post 1"))
            Thread.sleep(50)
            postService.addPost(user1, createPostPostData(title = "Regular Post 2"))
            Thread.sleep(50)
            postService.addPost(user1, createPostPostData(title = "Preface Post", isPreface = true))

            val diaryPage = postService.getDiaryPosts(
                Viewer.Registered(user2),
                testUser.login,
                null, null, null, null,
                Pageable(1, 10, SortOrder.DESC)
            )
            // Preface should come first regardless of creation time
            assertEquals("Preface Post", diaryPage.posts.content[0].title)

            rollback()
        }
    }

    @Test
    fun `pagination with filters still reports correct total pages`() {
        transaction {
            val (user1, _) = signUsersUp()
            repeat(5) { i ->
                postService.addPost(user1, createPostPostData(title = "Tagged $i", tags = setOf("special")))
            }
            repeat(3) { i ->
                postService.addPost(user1, createPostPostData(title = "Untagged $i"))
            }

            val page = getPosts(
                Viewer.Registered(user1),
                tags = TagPolicy.UNION to setOf("special"),
                pageable = Pageable(1, 2, SortOrder.DESC)
            )
            assertEquals(2, page.content.size)
            assertEquals(3, page.totalPages) // 5 tagged posts, 2 per page = 3 pages

            rollback()
        }
    }
}
