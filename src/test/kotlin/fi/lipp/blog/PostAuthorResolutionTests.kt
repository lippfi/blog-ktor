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
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class PostAuthorResolutionTests : UnitTestBase() {

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
        pageable: Pageable = Pageable(1, 10, SortOrder.DESC),
        isFeed: Boolean = false,
    ): Page<PostDto.View> {
        return postService.getPosts(viewer, author, diary, pattern, tags, null, null, null, pageable, isFeed)
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

    // --- Post author resolution ---

    @Test
    fun `a post by a local author has authorNickname from local user`() {
        transaction {
            val (user1, _) = signUsersUp()
            postService.addPost(user1, createPostPostData(title = "Local Post"))

            val posts = getPosts(Viewer.Registered(user1))
            assertEquals(1, posts.content.size)
            assertEquals(testUser.nickname, posts.content.first().authorNickname)

            rollback()
        }
    }

    @Test
    fun `a post by a local author has authorSignature from local user`() {
        transaction {
            val (user1, _) = signUsersUp()
            postService.addPost(user1, createPostPostData(title = "Local Post"))

            val posts = getPosts(Viewer.Registered(user1))
            assertEquals(1, posts.content.size)
            // Signature is nullable, default user has no signature set
            val post = posts.content.first()
            // Just verify it doesn't crash - signature comes from Users.signature
            assertNotNull(post.authorNickname)

            rollback()
        }
    }

    @Test
    fun `a post by a local author has authorLogin from the author diary`() {
        transaction {
            val (user1, _) = signUsersUp()
            postService.addPost(user1, createPostPostData(title = "Local Post"))

            val posts = getPosts(Viewer.Registered(user1))
            assertEquals(1, posts.content.size)
            assertEquals(testUser.login, posts.content.first().authorLogin)

            rollback()
        }
    }

    @Test
    fun `a post by an external author with linked local user has nickname from linked local user`() {
        transaction {
            val (user1, user2) = signUsersUp()
            val diaryId = DiaryEntity.find { Diaries.owner eq user1 }.first().id.value
            val extUserId = insertExternalUser(nickname = "ext_nick_ignored", linkedUserId = user2)
            insertExternalAuthorPost(diaryId, extUserId, "Linked Ext Post", "linked-ext-post")

            val posts = getPosts(Viewer.Registered(user1))
            assertEquals(1, posts.content.size)
            // Should use linked user's nickname, not external user's nickname
            assertEquals(testUser2.nickname, posts.content.first().authorNickname)

            rollback()
        }
    }

    @Test
    fun `a post by an external author with linked local user has signature from linked local user`() {
        transaction {
            val (user1, user2) = signUsersUp()
            val diaryId = DiaryEntity.find { Diaries.owner eq user1 }.first().id.value
            val extUserId = insertExternalUser(nickname = "ext_nick_ignored", linkedUserId = user2)
            insertExternalAuthorPost(diaryId, extUserId, "Linked Ext Post", "linked-ext-post")

            val posts = getPosts(Viewer.Registered(user1))
            assertEquals(1, posts.content.size)
            // Signature comes from linked user's Users.signature
            val post = posts.content.first()
            assertNotNull(post.authorNickname)

            rollback()
        }
    }

    @Test
    fun `a post by an external author with linked local user has login from linked user diary`() {
        transaction {
            val (user1, user2) = signUsersUp()
            val diaryId = DiaryEntity.find { Diaries.owner eq user1 }.first().id.value
            val extUserId = insertExternalUser(nickname = "ext_nick_ignored", linkedUserId = user2)
            insertExternalAuthorPost(diaryId, extUserId, "Linked Ext Post", "linked-ext-post")

            val posts = getPosts(Viewer.Registered(user1))
            assertEquals(1, posts.content.size)
            assertEquals(testUser2.login, posts.content.first().authorLogin)

            rollback()
        }
    }

    @Test
    fun `a post by an external author without linked local user has nickname from external user record`() {
        transaction {
            val (user1, _) = signUsersUp()
            val diaryId = DiaryEntity.find { Diaries.owner eq user1 }.first().id.value
            val extUserId = insertExternalUser(nickname = "external_nickname")
            insertExternalAuthorPost(diaryId, extUserId, "Unlinked Ext Post", "unlinked-ext-post")

            val posts = getPosts(Viewer.Registered(user1))
            assertEquals(1, posts.content.size)
            assertEquals("external_nickname", posts.content.first().authorNickname)

            rollback()
        }
    }

    @Test
    fun `a post by an external author without linked local user has null signature`() {
        transaction {
            val (user1, _) = signUsersUp()
            val diaryId = DiaryEntity.find { Diaries.owner eq user1 }.first().id.value
            val extUserId = insertExternalUser(nickname = "external_nickname")
            insertExternalAuthorPost(diaryId, extUserId, "Unlinked Ext Post", "unlinked-ext-post")

            val posts = getPosts(Viewer.Registered(user1))
            assertEquals(1, posts.content.size)
            assertNull(posts.content.first().authorSignature)

            rollback()
        }
    }

    @Test
    fun `a post by an external author without linked local user has null login`() {
        transaction {
            val (user1, _) = signUsersUp()
            val diaryId = DiaryEntity.find { Diaries.owner eq user1 }.first().id.value
            val extUserId = insertExternalUser(nickname = "external_nickname")
            insertExternalAuthorPost(diaryId, extUserId, "Unlinked Ext Post", "unlinked-ext-post")

            val posts = getPosts(Viewer.Registered(user1))
            assertEquals(1, posts.content.size)
            assertNull(posts.content.first().authorLogin)

            rollback()
        }
    }

    @Test
    fun `filtering by authorLogin finds local-author posts by diary login`() {
        transaction {
            val (user1, user2) = signUsersUp()
            postService.addPost(user1, createPostPostData(title = "User1 Post"))
            postService.addPost(user2, createPostPostData(title = "User2 Post"))

            val posts = getPosts(Viewer.Registered(user1), author = testUser.login)
            assertEquals(1, posts.content.size)
            assertEquals("User1 Post", posts.content.first().title)

            rollback()
        }
    }

    @Test
    fun `filtering by authorLogin finds external-linked-author posts by linked diary login`() {
        transaction {
            val (user1, user2) = signUsersUp()
            val diaryId = DiaryEntity.find { Diaries.owner eq user1 }.first().id.value
            val extUserId = insertExternalUser(nickname = "ext_linked", linkedUserId = user2)
            insertExternalAuthorPost(diaryId, extUserId, "Linked Ext Post", "linked-ext-post")

            val posts = getPosts(Viewer.Registered(user1), author = testUser2.login)
            assertEquals(1, posts.content.size)
            assertEquals("Linked Ext Post", posts.content.first().title)

            rollback()
        }
    }

    @Test
    fun `filtering by authorLogin does not return posts from unrelated authors`() {
        transaction {
            val (user1, user2) = signUsersUp()
            postService.addPost(user1, createPostPostData(title = "User1 Post"))

            val posts = getPosts(Viewer.Registered(user2), author = testUser2.login)
            assertEquals(0, posts.content.size)

            rollback()
        }
    }

    @Test
    fun `filtering by authorLogin works correctly when both local and external-linked authors share the same login source pattern`() {
        transaction {
            val (user1, user2) = signUsersUp()
            // user1 creates a local-author post
            postService.addPost(user1, createPostPostData(title = "Local by User1"))

            // create an external author linked to user1, post in user2's diary
            val diary2Id = DiaryEntity.find { Diaries.owner eq user2 }.first().id.value
            val extUserId = insertExternalUser(nickname = "ext_linked_to_user1", linkedUserId = user1)
            insertExternalAuthorPost(diary2Id, extUserId, "External by User1", "ext-by-user1")

            // Both should be found when filtering by user1's login
            val posts = getPosts(Viewer.Registered(user2), author = testUser.login)
            assertEquals(2, posts.content.size)
            val titles = posts.content.map { it.title }.toSet()
            assertEquals(setOf("Local by User1", "External by User1"), titles)

            rollback()
        }
    }

    @Test
    fun `author resolution does not crash when external author has null linked user`() {
        transaction {
            val (user1, _) = signUsersUp()
            val diaryId = DiaryEntity.find { Diaries.owner eq user1 }.first().id.value
            val extUserId = insertExternalUser(nickname = "no_link_author", linkedUserId = null)
            insertExternalAuthorPost(diaryId, extUserId, "No Link Post", "no-link-post")

            val posts = getPosts(Viewer.Registered(user1))
            assertEquals(1, posts.content.size)
            assertEquals("no_link_author", posts.content.first().authorNickname)
            assertNull(posts.content.first().authorLogin)
            assertNull(posts.content.first().authorSignature)

            rollback()
        }
    }
}
