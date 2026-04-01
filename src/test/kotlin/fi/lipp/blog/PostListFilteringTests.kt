package fi.lipp.blog

import fi.lipp.blog.data.*
import fi.lipp.blog.model.Page
import fi.lipp.blog.model.Pageable
import fi.lipp.blog.model.TagPolicy
import fi.lipp.blog.repository.*
import fi.lipp.blog.service.Viewer
import kotlinx.datetime.LocalDate
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.Test
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PostListFilteringTests : UnitTestBase() {

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
        from: LocalDate? = null,
        to: LocalDate? = null,
        isHidden: Boolean? = null,
        pageable: Pageable = Pageable(1, 10, SortOrder.DESC),
        isFeed: Boolean = false,
    ): Page<PostDto.View> {
        return postService.getPosts(viewer, author, diary, pattern, tags, from, to, isHidden, pageable, isFeed)
    }

    // --- Post list filtering ---

    @Test
    fun `filtering by diaryLogin returns only posts from that diary`() {
        transaction {
            val (user1, user2) = signUsersUp()
            postService.addPost(user1, createPostPostData(title = "User1 Post"))
            postService.addPost(user2, createPostPostData(title = "User2 Post"))

            val posts = getPosts(Viewer.Registered(user1), diary = testUser.login)
            assertEquals(1, posts.content.size)
            assertEquals("User1 Post", posts.content.first().title)

            rollback()
        }
    }

    @Test
    fun `filtering by diaryLogin excludes posts from other diaries`() {
        transaction {
            val (user1, user2) = signUsersUp()
            postService.addPost(user1, createPostPostData(title = "User1 Post"))
            postService.addPost(user2, createPostPostData(title = "User2 Post"))

            val posts = getPosts(Viewer.Registered(user1), diary = testUser2.login)
            assertEquals(1, posts.content.size)
            assertEquals("User2 Post", posts.content.first().title)

            rollback()
        }
    }

    @Test
    fun `filtering by text matches post title`() {
        transaction {
            val (user1, _) = signUsersUp()
            postService.addPost(user1, createPostPostData(title = "Unique Title Alpha"))
            postService.addPost(user1, createPostPostData(title = "Other Post"))

            val posts = getPosts(Viewer.Registered(user1), pattern = "Alpha")
            assertEquals(1, posts.content.size)
            assertEquals("Unique Title Alpha", posts.content.first().title)

            rollback()
        }
    }

    @Test
    fun `filtering by text matches post body`() {
        transaction {
            val (user1, _) = signUsersUp()
            postService.addPost(user1, createPostPostData(title = "Post1", text = "Contains special keyword xyz"))
            postService.addPost(user1, createPostPostData(title = "Post2", text = "Normal text"))

            val posts = getPosts(Viewer.Registered(user1), pattern = "xyz")
            assertEquals(1, posts.content.size)
            assertEquals("Post1", posts.content.first().title)

            rollback()
        }
    }

    @Test
    fun `filtering by text excludes posts that do not match title or body`() {
        transaction {
            val (user1, _) = signUsersUp()
            postService.addPost(user1, createPostPostData(title = "Normal Post", text = "Normal text"))

            val posts = getPosts(Viewer.Registered(user1), pattern = "nonexistent")
            assertEquals(0, posts.content.size)

            rollback()
        }
    }

    @Test
    fun `filtering by from includes posts exactly at start-of-day boundary`() {
        transaction {
            val (user1, _) = signUsersUp()
            postService.addPost(user1, createPostPostData(title = "Today Post"))

            val today = java.time.LocalDate.now().let { LocalDate(it.year, it.monthValue, it.dayOfMonth) }
            val posts = getPosts(Viewer.Registered(user1), from = today)
            assertEquals(1, posts.content.size)

            rollback()
        }
    }

    @Test
    fun `filtering by from excludes posts before start-of-day boundary`() {
        transaction {
            val (user1, _) = signUsersUp()
            postService.addPost(user1, createPostPostData(title = "Today Post"))

            val tomorrow = java.time.LocalDate.now().plusDays(1).let { LocalDate(it.year, it.monthValue, it.dayOfMonth) }
            val posts = getPosts(Viewer.Registered(user1), from = tomorrow)
            assertEquals(0, posts.content.size)

            rollback()
        }
    }

    @Test
    fun `filtering by to includes posts exactly at end-of-day boundary`() {
        transaction {
            val (user1, _) = signUsersUp()
            postService.addPost(user1, createPostPostData(title = "Today Post"))

            val today = java.time.LocalDate.now().let { LocalDate(it.year, it.monthValue, it.dayOfMonth) }
            val posts = getPosts(Viewer.Registered(user1), to = today)
            assertEquals(1, posts.content.size)

            rollback()
        }
    }

    @Test
    fun `filtering by to excludes posts after end-of-day boundary`() {
        transaction {
            val (user1, _) = signUsersUp()
            postService.addPost(user1, createPostPostData(title = "Today Post"))

            val yesterday = java.time.LocalDate.now().minusDays(1).let { LocalDate(it.year, it.monthValue, it.dayOfMonth) }
            val posts = getPosts(Viewer.Registered(user1), to = yesterday)
            assertEquals(0, posts.content.size)

            rollback()
        }
    }

    @Test
    fun `filtering by isHidden true returns only hidden posts`() {
        transaction {
            val (user1, _) = signUsersUp()
            postService.addPost(user1, createPostPostData(title = "Public Post", isHidden = false))
            postService.addPost(user1, createPostPostData(title = "Hidden Post", isHidden = true))

            val posts = getPosts(Viewer.Registered(user1), isHidden = true)
            assertEquals(1, posts.content.size)
            assertEquals("Hidden Post", posts.content.first().title)

            rollback()
        }
    }

    @Test
    fun `filtering by isHidden false returns only visible posts`() {
        transaction {
            val (user1, _) = signUsersUp()
            postService.addPost(user1, createPostPostData(title = "Public Post", isHidden = false))
            postService.addPost(user1, createPostPostData(title = "Hidden Post", isHidden = true))

            val posts = getPosts(Viewer.Registered(user1), isHidden = false)
            assertEquals(1, posts.content.size)
            assertEquals("Public Post", posts.content.first().title)

            rollback()
        }
    }

    @Test
    fun `filtering by tags with UNION returns posts containing at least one tag`() {
        transaction {
            val (user1, _) = signUsersUp()
            postService.addPost(user1, createPostPostData(title = "Tagged A", tags = setOf("tagA")))
            postService.addPost(user1, createPostPostData(title = "Tagged B", tags = setOf("tagB")))
            postService.addPost(user1, createPostPostData(title = "No Tags"))

            val posts = getPosts(Viewer.Registered(user1), tags = TagPolicy.UNION to setOf("tagA", "tagB"))
            assertEquals(2, posts.content.size)
            val titles = posts.content.map { it.title }.toSet()
            assertTrue(titles.containsAll(setOf("Tagged A", "Tagged B")))

            rollback()
        }
    }

    @Test
    fun `filtering by tags with INTERSECTION returns only posts containing all tags`() {
        transaction {
            val (user1, _) = signUsersUp()
            postService.addPost(user1, createPostPostData(title = "Both Tags", tags = setOf("tagA", "tagB")))
            postService.addPost(user1, createPostPostData(title = "Only A", tags = setOf("tagA")))
            postService.addPost(user1, createPostPostData(title = "Only B", tags = setOf("tagB")))

            val posts = getPosts(Viewer.Registered(user1), tags = TagPolicy.INTERSECTION to setOf("tagA", "tagB"))
            assertEquals(1, posts.content.size)
            assertEquals("Both Tags", posts.content.first().title)

            rollback()
        }
    }

    @Test
    fun `filtering by empty tag set behaves like no tag filter`() {
        transaction {
            val (user1, _) = signUsersUp()
            postService.addPost(user1, createPostPostData(title = "Post A"))
            postService.addPost(user1, createPostPostData(title = "Post B"))

            val posts = getPosts(Viewer.Registered(user1), tags = TagPolicy.UNION to emptySet())
            assertEquals(2, posts.content.size)

            rollback()
        }
    }

    @Test
    fun `combining diary filter and tag filter returns only posts matching both`() {
        transaction {
            val (user1, user2) = signUsersUp()
            postService.addPost(user1, createPostPostData(title = "User1 Tagged", tags = setOf("special")))
            postService.addPost(user2, createPostPostData(title = "User2 Tagged", tags = setOf("special")))
            postService.addPost(user1, createPostPostData(title = "User1 No Tag"))

            val posts = getPosts(Viewer.Registered(user1), diary = testUser.login, tags = TagPolicy.UNION to setOf("special"))
            assertEquals(1, posts.content.size)
            assertEquals("User1 Tagged", posts.content.first().title)

            rollback()
        }
    }

    @Test
    fun `combining author filter and text filter returns only posts matching both`() {
        transaction {
            val (user1, user2) = signUsersUp()
            postService.addPost(user1, createPostPostData(title = "User1 Keyword", text = "abc"))
            postService.addPost(user1, createPostPostData(title = "User1 Other", text = "def"))
            postService.addPost(user2, createPostPostData(title = "User2 Keyword", text = "abc"))

            val posts = getPosts(Viewer.Registered(user1), author = testUser.login, pattern = "abc")
            assertEquals(1, posts.content.size)
            assertEquals("User1 Keyword", posts.content.first().title)

            rollback()
        }
    }

    @Test
    fun `combining date range and hidden flag returns only posts matching both`() {
        transaction {
            val (user1, _) = signUsersUp()
            postService.addPost(user1, createPostPostData(title = "Hidden Today", isHidden = true))
            postService.addPost(user1, createPostPostData(title = "Visible Today", isHidden = false))

            val today = java.time.LocalDate.now().let { LocalDate(it.year, it.monthValue, it.dayOfMonth) }
            val posts = getPosts(Viewer.Registered(user1), from = today, to = today, isHidden = true)
            assertEquals(1, posts.content.size)
            assertEquals("Hidden Today", posts.content.first().title)

            rollback()
        }
    }

    @Test
    fun `combining feed filtering with ignore filtering hides ignored authors`() {
        transaction {
            val (user1, user2) = signUsersUp()
            postService.addPost(user2, createPostPostData(title = "Ignored Feed Post"))

            userService.ignoreUser(user1, testUser2.login)
            commit()

            val feedPosts = postService.getLatestPosts(Viewer.Registered(user1), Pageable(1, 10, SortOrder.DESC))
            assertEquals(0, feedPosts.content.size)

            rollback()
        }
    }

    @Test
    fun `combining feed filtering with hidden-from-feed hides hidden authors`() {
        transaction {
            val (user1, user2) = signUsersUp()
            postService.addPost(user2, createPostPostData(title = "Hidden Feed Post"))

            userService.doNotShowInFeed(user1, testUser2.login)
            commit()

            val feedPosts = postService.getLatestPosts(Viewer.Registered(user1), Pageable(1, 10, SortOrder.DESC))
            assertEquals(0, feedPosts.content.size)

            rollback()
        }
    }
}
