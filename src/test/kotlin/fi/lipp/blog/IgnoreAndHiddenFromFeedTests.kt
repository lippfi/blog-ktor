package fi.lipp.blog

import fi.lipp.blog.data.*
import fi.lipp.blog.domain.DiaryEntity
import fi.lipp.blog.model.Page
import fi.lipp.blog.model.Pageable
import fi.lipp.blog.model.TagPolicy
import fi.lipp.blog.model.exceptions.PostNotFoundException
import fi.lipp.blog.repository.*
import fi.lipp.blog.service.Viewer
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.Test
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class IgnoreAndHiddenFromFeedTests : UnitTestBase() {

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
        from: kotlinx.datetime.LocalDate? = null,
        to: kotlinx.datetime.LocalDate? = null,
        isHidden: Boolean? = null,
        pageable: Pageable = Pageable(1, 10, SortOrder.DESC),
        isFeed: Boolean = false,
    ): Page<PostDto.View> {
        return postService.getPosts(viewer, author, diary, pattern, tags, from, to, isHidden, pageable, isFeed)
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
            it[Posts.text] = "text of $title"
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

    // --- Ignore list and hidden-from-feed behavior for posts ---

    @Test
    fun `ignore user stores optional reason`() {
        transaction {
            val (user1, user2) = signUsersUp()
            val reason = "spam reactions"

            userService.ignoreUser(user1, testUser2.login, reason)

            val ignoreRecord = IgnoreList
                .select { (IgnoreList.user eq user1) and (IgnoreList.ignoredUser eq user2) }
                .singleOrNull()

            assertNotNull(ignoreRecord)
            assertEquals(reason, ignoreRecord[IgnoreList.reason])
        }
    }

    @Test
    fun `ignore user stores null reason when reason is blank`() {
        transaction {
            val (user1, user2) = signUsersUp()

            userService.ignoreUser(user1, testUser2.login, "   ")

            val ignoreRecord = IgnoreList
                .select { (IgnoreList.user eq user1) and (IgnoreList.ignoredUser eq user2) }
                .singleOrNull()

            assertNotNull(ignoreRecord)
            assertNull(ignoreRecord[IgnoreList.reason])
        }
    }

    @Test
    fun `a registered viewer does not see posts authored by a local user they ignored`() {
        transaction {
            val (user1, user2) = signUsersUp()
            postService.addPost(user2, createPostPostData(title = "Ignored Post"))

            userService.ignoreUser(user1, testUser2.login)
            commit()

            val posts = getPosts(Viewer.Registered(user1))
            assertEquals(0, posts.content.size)

            rollback()
        }
    }

    @Test
    fun `a registered viewer does not see posts authored by an external linked user they ignored`() {
        transaction {
            val (user1, user2) = signUsersUp()
            val diaryId = DiaryEntity.find { Diaries.owner eq user1 }.first().id.value
            val extUserId = insertExternalUser(nickname = "ext_linked", linkedUserId = user2)
            insertExternalAuthorPost(diaryId, extUserId, "Ext Ignored Post", "ext-ignored-post")

            userService.ignoreUser(user1, testUser2.login)
            commit()

            val posts = getPosts(Viewer.Registered(user1))
            assertEquals(0, posts.content.size)

            rollback()
        }
    }

    @Test
    fun `a registered viewer does not see posts authored by a user who ignored them`() {
        transaction {
            val (user1, user2) = signUsersUp()
            postService.addPost(user1, createPostPostData(title = "Author Ignored Viewer"))

            userService.ignoreUser(user1, testUser2.login)
            commit()

            val posts = getPosts(Viewer.Registered(user2))
            assertEquals(0, posts.content.size)

            rollback()
        }
    }

    @Test
    fun `a registered viewer does not see posts authored by an external linked user who ignored them`() {
        transaction {
            val (user1, user2) = signUsersUp()
            val diaryId = DiaryEntity.find { Diaries.owner eq user2 }.first().id.value
            val extUserId = insertExternalUser(nickname = "ext_ignorer", linkedUserId = user1)
            insertExternalAuthorPost(diaryId, extUserId, "Ext Ignorer Post", "ext-ignorer-post")

            // user1 (linked external author) ignores user2
            userService.ignoreUser(user1, testUser2.login)
            commit()

            val posts = getPosts(Viewer.Registered(user2))
            assertEquals(0, posts.content.size)

            rollback()
        }
    }

    @Test
    fun `ignore filtering does not hide posts from anonymous viewers`() {
        transaction {
            val (user1, user2) = signUsersUp()
            postService.addPost(user1, createPostPostData(title = "Public Post"))

            // user2 ignores user1 - should not affect anonymous viewers
            userService.ignoreUser(user2, testUser.login)
            commit()

            val anonymousViewer = Viewer.Anonymous("127.0.0.1", "fp1")
            val posts = getPosts(anonymousViewer)
            assertEquals(1, posts.content.size)

            rollback()
        }
    }

    @Test
    fun `ignore filtering does not hide posts from unrelated viewers when no ignore relationship exists`() {
        transaction {
            val users = signUsersUp(3)
            val (user1Id, user1Login) = users[0]
            val (user2Id, user2Login) = users[1]
            val (user3Id, _) = users[2]

            postService.addPost(user1Id, createPostPostData(title = "User1 Post"))

            // user2 ignores user1 but user3 does not
            userService.ignoreUser(user2Id, user1Login)
            commit()

            val posts = getPosts(Viewer.Registered(user3Id))
            assertEquals(1, posts.content.size)

            rollback()
        }
    }

    @Test
    fun `hidden-from-feed filtering hides posts in feed endpoints for a registered viewer`() {
        transaction {
            val (user1, user2) = signUsersUp()
            postService.addPost(user2, createPostPostData(title = "Hidden From Feed"))

            userService.doNotShowInFeed(user1, testUser2.login)
            commit()

            val feedPosts = postService.getLatestPosts(Viewer.Registered(user1), Pageable(1, 10, SortOrder.DESC))
            assertEquals(0, feedPosts.content.size)

            rollback()
        }
    }

    @Test
    fun `hidden-from-feed filtering hides posts from linked external authors in feed endpoints`() {
        transaction {
            val (user1, user2) = signUsersUp()
            val diaryId = DiaryEntity.find { Diaries.owner eq user1 }.first().id.value
            val extUserId = insertExternalUser(nickname = "ext_hidden", linkedUserId = user2)
            insertExternalAuthorPost(diaryId, extUserId, "Ext Hidden Feed", "ext-hidden-feed")

            userService.doNotShowInFeed(user1, testUser2.login)
            commit()

            val feedPosts = postService.getLatestPosts(Viewer.Registered(user1), Pageable(1, 10, SortOrder.DESC))
            assertEquals(0, feedPosts.content.size)

            rollback()
        }
    }

    @Test
    fun `hidden-from-feed filtering does not affect non-feed endpoints`() {
        transaction {
            val (user1, user2) = signUsersUp()
            postService.addPost(user2, createPostPostData(title = "Hidden From Feed"))

            userService.doNotShowInFeed(user1, testUser2.login)
            commit()

            val nonFeedPosts = getPosts(Viewer.Registered(user1), isFeed = false)
            assertEquals(1, nonFeedPosts.content.size)

            rollback()
        }
    }

    @Test
    fun `hidden-from-feed filtering does not affect direct single-post loading by URI when there is no ignore relation`() {
        transaction {
            val (user1, user2) = signUsersUp()
            postService.addPost(user2, createPostPostData(title = "Hidden From Feed"))

            userService.doNotShowInFeed(user1, testUser2.login)
            commit()

            val postPage = postService.getPost(Viewer.Registered(user1), testUser2.login, "hidden-from-feed")
            assertEquals("Hidden From Feed", postPage.post.title)

            rollback()
        }
    }

    @Test
    fun `ignore filtering still applies in single-post loading by URI`() {
        transaction {
            val (user1, user2) = signUsersUp()
            postService.addPost(user2, createPostPostData(title = "Ignored Post"))

            userService.ignoreUser(user1, testUser2.login)
            commit()

            assertFailsWith<PostNotFoundException> {
                postService.getPost(Viewer.Registered(user1), testUser2.login, "ignored-post")
            }

            rollback()
        }
    }

    @Test
    fun `ignore filtering still applies in getPreface`() {
        transaction {
            val (user1, user2) = signUsersUp()
            postService.addPost(user2, createPostPostData(title = "Ignored Preface", isPreface = true))

            userService.ignoreUser(user1, testUser2.login)
            commit()

            val preface = postService.getPreface(Viewer.Registered(user1), testUser2.login)
            assertNull(preface)

            rollback()
        }
    }

    @Test
    fun `ignore filtering still applies in toPostViewById`() {
        transaction {
            val (user1, user2) = signUsersUp()
            postService.addPost(user2, createPostPostData(title = "Ignored By Id"))

            userService.ignoreUser(user1, testUser2.login)
            commit()

            assertFailsWith<PostNotFoundException> {
                postService.getPost(Viewer.Registered(user1), testUser2.login, "ignored-by-id")
            }

            rollback()
        }
    }

    @Test
    fun `a post by a user in both ignore list and hidden-from-feed is still hidden`() {
        transaction {
            val (user1, user2) = signUsersUp()
            postService.addPost(user2, createPostPostData(title = "Double Hidden"))

            userService.ignoreUser(user1, testUser2.login)
            userService.doNotShowInFeed(user1, testUser2.login)
            commit()

            val feedPosts = postService.getLatestPosts(Viewer.Registered(user1), Pageable(1, 10, SortOrder.DESC))
            assertEquals(0, feedPosts.content.size)

            val nonFeedPosts = getPosts(Viewer.Registered(user1), isFeed = false)
            assertEquals(0, nonFeedPosts.content.size)

            rollback()
        }
    }

    @Test
    fun `a post by a user only in hidden-from-feed is visible outside feed endpoints`() {
        transaction {
            val (user1, user2) = signUsersUp()
            postService.addPost(user2, createPostPostData(title = "Feed Hidden Only"))

            userService.doNotShowInFeed(user1, testUser2.login)
            commit()

            val feedPosts = postService.getLatestPosts(Viewer.Registered(user1), Pageable(1, 10, SortOrder.DESC))
            assertEquals(0, feedPosts.content.size)

            val nonFeedPosts = getPosts(Viewer.Registered(user1), isFeed = false)
            assertEquals(1, nonFeedPosts.content.size)

            rollback()
        }
    }

    @Test
    fun `a post by a user only in ignore list is hidden both in feed and non-feed endpoints`() {
        transaction {
            val (user1, user2) = signUsersUp()
            postService.addPost(user2, createPostPostData(title = "Ignored Post"))

            userService.ignoreUser(user1, testUser2.login)
            commit()

            val feedPosts = postService.getLatestPosts(Viewer.Registered(user1), Pageable(1, 10, SortOrder.DESC))
            assertEquals(0, feedPosts.content.size)

            val nonFeedPosts = getPosts(Viewer.Registered(user1), isFeed = false)
            assertEquals(0, nonFeedPosts.content.size)

            rollback()
        }
    }
}
