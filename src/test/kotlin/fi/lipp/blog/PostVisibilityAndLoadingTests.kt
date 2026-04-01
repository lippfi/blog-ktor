package fi.lipp.blog

import fi.lipp.blog.data.*
import fi.lipp.blog.domain.DiaryEntity
import fi.lipp.blog.domain.PostEntity
import fi.lipp.blog.model.Page
import fi.lipp.blog.model.Pageable
import fi.lipp.blog.model.TagPolicy
import fi.lipp.blog.model.exceptions.PostNotFoundException
import fi.lipp.blog.repository.*
import fi.lipp.blog.service.Viewer
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.Test
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PostVisibilityAndLoadingTests : UnitTestBase() {

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

    private fun createPostUpdateData(
        id: UUID,
        uri: String = "",
        avatar: String = "avatar url",
        title: String = "sample title",
        text: String = "sample text",
        isEncrypted: Boolean = false,
        isHidden: Boolean = false,
        classes: String = "bold",
        tags: Set<String> = emptySet(),
        readGroup: UUID = groupService.everyoneGroupUUID,
        commentGroup: UUID = groupService.everyoneGroupUUID,
        reactionGroup: UUID = groupService.everyoneGroupUUID,
        commentReactionGroup: UUID = reactionGroup,
    ): PostDto.Update {
        return PostDto.Update(
            id = id,
            uri = uri,
            avatar = avatar,
            title = title,
            text = text,
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

    private fun insertExternalAuthorPost(
        diaryId: UUID,
        externalUserId: UUID,
        title: String,
        uri: String,
        readGroupId: UUID = groupService.everyoneGroupUUID,
        commentGroupId: UUID = groupService.everyoneGroupUUID,
        reactionGroupId: UUID = groupService.everyoneGroupUUID,
        commentReactionGroupId: UUID = groupService.everyoneGroupUUID,
        isPreface: Boolean = false,
        isArchived: Boolean = false,
        isHidden: Boolean = false,
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
            it[Posts.isPreface] = isPreface
            it[Posts.isEncrypted] = false
            it[Posts.isHidden] = isHidden
            it[Posts.isArchived] = isArchived
            it[Posts.classes] = ""
            it[Posts.readGroup] = readGroupId
            it[Posts.commentGroup] = commentGroupId
            it[Posts.reactionGroup] = reactionGroupId
            it[Posts.commentReactionGroup] = commentReactionGroupId
        }.value
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

    private fun makeFriends(user1Id: UUID, user2Login: String, user2Id: UUID) {
        userService.sendFriendRequest(user1Id, FriendRequestDto.Create(user2Login, "Hi", null))
        val requestId = userService.getReceivedFriendRequests(user2Id).first().id
        userService.acceptFriendRequest(user2Id, requestId, null)
    }

    // --- Post visibility and loading ---

    @Test
    fun `a public post created by a local author is visible to an anonymous viewer`() {
        transaction {
            val (user1, _) = signUsersUp()
            postService.addPost(user1, createPostPostData(title = "Public Post"))

            val anonymousViewer = Viewer.Anonymous("127.0.0.1", "fingerprint1")
            val posts = getPosts(anonymousViewer)
            assertEquals(1, posts.content.size)
            assertEquals("Public Post", posts.content.first().title)

            rollback()
        }
    }

    @Test
    fun `a public post created by a local author is visible to a registered viewer`() {
        transaction {
            val (user1, user2) = signUsersUp()
            postService.addPost(user1, createPostPostData(title = "Public Post"))

            val posts = getPosts(Viewer.Registered(user2))
            assertEquals(1, posts.content.size)
            assertEquals("Public Post", posts.content.first().title)

            rollback()
        }
    }

    @Test
    fun `a public post created by an external author without linked local user is visible to an anonymous viewer`() {
        transaction {
            val (user1, _) = signUsersUp()
            val diaryId = DiaryEntity.find { Diaries.owner eq user1 }.first().id.value
            val extUserId = insertExternalUser(nickname = "ext_author")
            insertExternalAuthorPost(diaryId, extUserId, "External Post", "ext-post")

            val anonymousViewer = Viewer.Anonymous("127.0.0.1", "fp1")
            val posts = getPosts(anonymousViewer)
            assertEquals(1, posts.content.size)
            assertEquals("External Post", posts.content.first().title)

            rollback()
        }
    }

    @Test
    fun `a public post created by an external author with linked local user is visible to a registered viewer`() {
        transaction {
            val (user1, user2) = signUsersUp()
            val diaryId = DiaryEntity.find { Diaries.owner eq user1 }.first().id.value
            val extUserId = insertExternalUser(nickname = "ext_linked", linkedUserId = user2)
            insertExternalAuthorPost(diaryId, extUserId, "Linked External Post", "linked-ext-post")

            val posts = getPosts(Viewer.Registered(user1))
            assertEquals(1, posts.content.size)
            assertEquals("Linked External Post", posts.content.first().title)

            rollback()
        }
    }

    @Test
    fun `a post with readGroup EVERYONE is returned by single-post loading`() {
        transaction {
            val (user1, _) = signUsersUp()
            postService.addPost(user1, createPostPostData(title = "Everyone Post", readGroup = groupService.everyoneGroupUUID))

            val postPage = postService.getPost(Viewer.Registered(user1), testUser.login, "everyone-post")
            assertEquals("Everyone Post", postPage.post.title)

            rollback()
        }
    }

    @Test
    fun `a post with readGroup REGISTERED_USERS is not visible to an anonymous viewer`() {
        transaction {
            val (user1, _) = signUsersUp()
            postService.addPost(user1, createPostPostData(title = "Registered Only", readGroup = groupService.registeredGroupUUID))

            val anonymousViewer = Viewer.Anonymous("127.0.0.1", "fp1")
            val posts = getPosts(anonymousViewer)
            assertEquals(0, posts.content.size)

            rollback()
        }
    }

    @Test
    fun `a post with readGroup REGISTERED_USERS is visible to a registered viewer`() {
        transaction {
            val (user1, user2) = signUsersUp()
            postService.addPost(user1, createPostPostData(title = "Registered Only", readGroup = groupService.registeredGroupUUID))

            val posts = getPosts(Viewer.Registered(user2))
            assertEquals(1, posts.content.size)
            assertEquals("Registered Only", posts.content.first().title)

            rollback()
        }
    }

    @Test
    fun `a post with readGroup FRIENDS is visible to a friend of the diary owner`() {
        transaction {
            val (user1, user2) = signUsersUp()
            postService.addPost(user1, createPostPostData(title = "Friends Post", readGroup = groupService.friendsGroupUUID))

            makeFriends(user1, testUser2.login, user2)

            val posts = getPosts(Viewer.Registered(user2))
            assertEquals(1, posts.content.size)
            assertEquals("Friends Post", posts.content.first().title)

            rollback()
        }
    }

    @Test
    fun `a post with readGroup FRIENDS is not visible to a non-friend registered viewer`() {
        transaction {
            val (user1, user2) = signUsersUp()
            postService.addPost(user1, createPostPostData(title = "Friends Post", readGroup = groupService.friendsGroupUUID))

            val posts = getPosts(Viewer.Registered(user2))
            assertEquals(0, posts.content.size)

            rollback()
        }
    }

    @Test
    fun `a post with readGroup CUSTOM is visible to a user included in the custom group`() {
        transaction {
            val (user1, user2) = signUsersUp()
            groupService.createAccessGroup(user1, testUser.login, "my-custom-group")
            val customGroupId = UUID.fromString(groupService.getAccessGroups(user1, testUser.login).content["my-custom-group"])

            groupService.addUserToGroup(user1, testUser2.login, customGroupId)

            postService.addPost(user1, createPostPostData(title = "Custom Group Post", readGroup = customGroupId))

            val posts = getPosts(Viewer.Registered(user2))
            assertEquals(1, posts.content.size)
            assertEquals("Custom Group Post", posts.content.first().title)

            rollback()
        }
    }

    @Test
    fun `a post with readGroup CUSTOM is not visible to a user excluded from the custom group`() {
        transaction {
            val (user1, user2) = signUsersUp()
            groupService.createAccessGroup(user1, testUser.login, "my-custom-group")
            val customGroupId = UUID.fromString(groupService.getAccessGroups(user1, testUser.login).content["my-custom-group"])

            // user2 is NOT added to the custom group
            postService.addPost(user1, createPostPostData(title = "Custom Group Post", readGroup = customGroupId))

            val posts = getPosts(Viewer.Registered(user2))
            assertEquals(0, posts.content.size)

            rollback()
        }
    }

    @Test
    fun `a local author can load their own post even if its read group would otherwise deny access`() {
        transaction {
            val (user1, _) = signUsersUp()
            postService.addPost(user1, createPostPostData(title = "Private Post", readGroup = groupService.privateGroupUUID))

            val posts = getPosts(Viewer.Registered(user1))
            assertEquals(1, posts.content.size)
            assertEquals("Private Post", posts.content.first().title)

            rollback()
        }
    }

    @Test
    fun `a linked external author can load their own post even if its read group would otherwise deny access`() {
        transaction {
            val (user1, user2) = signUsersUp()
            val diaryId = DiaryEntity.find { Diaries.owner eq user1 }.first().id.value
            val extUserId = insertExternalUser(nickname = "ext_linked", linkedUserId = user2)
            insertExternalAuthorPost(diaryId, extUserId, "Private External", "private-ext", readGroupId = groupService.privateGroupUUID)

            val posts = getPosts(Viewer.Registered(user2))
            assertEquals(1, posts.content.size)
            assertEquals("Private External", posts.content.first().title)

            rollback()
        }
    }

    @Test
    fun `an unrelated registered user cannot load a restricted post by direct URI lookup`() {
        transaction {
            val (user1, user2) = signUsersUp()
            postService.addPost(user1, createPostPostData(title = "Private Post", readGroup = groupService.privateGroupUUID))

            assertFailsWith<PostNotFoundException> {
                postService.getPost(Viewer.Registered(user2), testUser.login, "private-post")
            }

            rollback()
        }
    }

    @Test
    fun `an unrelated anonymous user cannot load a restricted post by direct URI lookup`() {
        transaction {
            val (user1, _) = signUsersUp()
            postService.addPost(user1, createPostPostData(title = "Private Post", readGroup = groupService.privateGroupUUID))

            val anonymousViewer = Viewer.Anonymous("127.0.0.1", "fp1")
            assertFailsWith<PostNotFoundException> {
                postService.getPost(anonymousViewer, testUser.login, "private-post")
            }

            rollback()
        }
    }

    @Test
    fun `archived posts are excluded from list endpoints`() {
        transaction {
            val (user1, _) = signUsersUp()
            val post = postService.addPost(user1, createPostPostData(title = "To Archive"))

            postService.deletePost(user1, post.id)

            val posts = getPosts(Viewer.Registered(user1))
            assertEquals(0, posts.content.size)

            rollback()
        }
    }

    @Test
    fun `archived posts are excluded from single-post endpoint`() {
        transaction {
            val (user1, _) = signUsersUp()
            val post = postService.addPost(user1, createPostPostData(title = "To Archive"))

            postService.deletePost(user1, post.id)

            assertFailsWith<PostNotFoundException> {
                postService.getPost(Viewer.Registered(user1), testUser.login, "to-archive")
            }

            rollback()
        }
    }

    @Test
    fun `preface posts are returned by getPreface when visible`() {
        transaction {
            val (user1, user2) = signUsersUp()
            postService.addPost(user1, createPostPostData(title = "My Preface", isPreface = true))

            val preface = postService.getPreface(Viewer.Registered(user2), testUser.login)
            assertNotNull(preface)
            assertEquals("My Preface", preface.title)
            assertTrue(preface.isPreface)

            rollback()
        }
    }

    @Test
    fun `getPreface returns null when no preface exists`() {
        transaction {
            val (user1, user2) = signUsersUp()
            // No preface created
            postService.addPost(user1, createPostPostData(title = "Normal Post", isPreface = false))

            val preface = postService.getPreface(Viewer.Registered(user2), testUser.login)
            assertNull(preface)

            rollback()
        }
    }

    @Test
    fun `getPreface returns null when preface exists but viewer has no access`() {
        transaction {
            val (user1, user2) = signUsersUp()
            postService.addPost(user1, createPostPostData(title = "Private Preface", isPreface = true, readGroup = groupService.privateGroupUUID))

            val preface = postService.getPreface(Viewer.Registered(user2), testUser.login)
            assertNull(preface)

            rollback()
        }
    }

    @Test
    fun `getPreface does not accidentally return a non-preface post`() {
        transaction {
            val (user1, user2) = signUsersUp()
            postService.addPost(user1, createPostPostData(title = "Regular Post", isPreface = false))

            val preface = postService.getPreface(Viewer.Registered(user2), testUser.login)
            assertNull(preface)

            rollback()
        }
    }

    @Test
    fun `single-post loading and paged post loading apply the same visibility rules`() {
        transaction {
            val (user1, user2) = signUsersUp()
            postService.addPost(user1, createPostPostData(title = "Restricted Post", readGroup = groupService.registeredGroupUUID))

            // Both should be visible to registered viewer
            val posts = getPosts(Viewer.Registered(user2))
            assertEquals(1, posts.content.size)

            val singlePost = postService.getPost(Viewer.Registered(user2), testUser.login, "restricted-post")
            assertEquals("Restricted Post", singlePost.post.title)

            // Both should be invisible to anonymous viewer
            val anonymousViewer = Viewer.Anonymous("127.0.0.1", "fp1")
            val anonPosts = getPosts(anonymousViewer)
            assertEquals(0, anonPosts.content.size)

            assertFailsWith<PostNotFoundException> {
                postService.getPost(anonymousViewer, testUser.login, "restricted-post")
            }

            rollback()
        }
    }

    @Test
    fun `toPostViewById does not return a post hidden by ignore rules`() {
        transaction {
            val (user1, user2) = signUsersUp()
            val post = postService.addPost(user1, createPostPostData(title = "Ignored Post"))

            userService.ignoreUser(user2, testUser.login)
            commit()

            assertFailsWith<PostNotFoundException> {
                postService.getPost(Viewer.Registered(user2), testUser.login, "ignored-post")
            }

            rollback()
        }
    }

    @Test
    fun `toPostViewById does not return a post hidden by read-group rules`() {
        transaction {
            val (user1, user2) = signUsersUp()
            postService.addPost(user1, createPostPostData(title = "Private Post", readGroup = groupService.privateGroupUUID))

            assertFailsWith<PostNotFoundException> {
                postService.getPost(Viewer.Registered(user2), testUser.login, "private-post")
            }

            rollback()
        }
    }

    @Test
    fun `loading a post by URI with the wrong diary login returns not found`() {
        transaction {
            val (user1, user2) = signUsersUp()
            postService.addPost(user1, createPostPostData(title = "Some Post"))

            assertFailsWith<PostNotFoundException> {
                postService.getPost(Viewer.Registered(user2), testUser2.login, "some-post")
            }

            rollback()
        }
    }

    @Test
    fun `loading a post by URI with correct diary but wrong URI returns not found`() {
        transaction {
            val (user1, user2) = signUsersUp()
            postService.addPost(user1, createPostPostData(title = "Some Post"))

            assertFailsWith<PostNotFoundException> {
                postService.getPost(Viewer.Registered(user2), testUser.login, "wrong-uri")
            }

            rollback()
        }
    }
}
