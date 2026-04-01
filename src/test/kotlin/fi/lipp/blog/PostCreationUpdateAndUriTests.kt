package fi.lipp.blog

import fi.lipp.blog.data.*
import fi.lipp.blog.domain.DiaryEntity
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

class PostCreationUpdateAndUriTests : UnitTestBase() {

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

    // --- Post creation and update ---

    @Test
    fun `creating a normal post stores all provided fields correctly`() {
        transaction {
            val (user1, _) = signUsersUp()
            val post = postService.addPost(user1, createPostPostData(
                title = "My Title",
                text = "My Body",
                classes = "bold italic",
                tags = setOf("tag1", "tag2"),
                isEncrypted = true,
            ))

            assertEquals("My Title", post.title)
            assertEquals("My Body", post.text)
            assertEquals("bold italic", post.classes)
            assertEquals(setOf("tag1", "tag2"), post.tags)
            assertTrue(post.isEncrypted)
            assertFalse(post.isPreface)
            assertFalse(post.isHidden)

            rollback()
        }
    }

    @Test
    fun `creating a preface archives any previous active preface for the same diary`() {
        transaction {
            val (user1, _) = signUsersUp()
            postService.addPost(user1, createPostPostData(title = "Old Preface", isPreface = true))
            postService.addPost(user1, createPostPostData(title = "New Preface", isPreface = true))

            val preface = postService.getPreface(Viewer.Registered(user1), testUser.login)
            assertNotNull(preface)
            assertEquals("New Preface", preface.title)

            // Old preface should be archived (not in list)
            val pageable = Pageable(1, 10, SortOrder.DESC)
            val posts = postService.getPosts(Viewer.Registered(user1), null, testUser.login, null, null, null, null, null, pageable, false)
            val prefacePosts = posts.content.filter { it.isPreface }
            assertEquals(1, prefacePosts.size)
            assertEquals("New Preface", prefacePosts.first().title)

            rollback()
        }
    }

    @Test
    fun `creating a preface does not archive archived prefaces again incorrectly`() {
        transaction {
            val (user1, _) = signUsersUp()
            // Create and archive a preface
            postService.addPost(user1, createPostPostData(title = "First Preface", isPreface = true))
            postService.addPost(user1, createPostPostData(title = "Second Preface", isPreface = true))
            // First is now archived; creating a third should only archive the second
            postService.addPost(user1, createPostPostData(title = "Third Preface", isPreface = true))

            val preface = postService.getPreface(Viewer.Registered(user1), testUser.login)
            assertNotNull(preface)
            assertEquals("Third Preface", preface.title)

            rollback()
        }
    }

    @Test
    fun `creating a post assigns local author when created by registered local user`() {
        transaction {
            val (user1, _) = signUsersUp()
            val post = postService.addPost(user1, createPostPostData(title = "Local Author Post"))

            assertEquals(testUser.login, post.authorLogin)
            assertEquals(testUser.nickname, post.authorNickname)

            rollback()
        }
    }

    @Test
    fun `creating a post validates read group belongs to same diary or is global`() {
        transaction {
            val (user1, user2) = signUsersUp()
            groupService.createAccessGroup(user2, testUser2.login, "user2-group")
            val user2GroupId = UUID.fromString(groupService.getAccessGroups(user2, testUser2.login).content["user2-group"])

            assertFailsWith<InvalidAccessGroupException> {
                postService.addPost(user1, createPostPostData(title = "Bad Group", readGroup = user2GroupId))
            }

            rollback()
        }
    }

    @Test
    fun `creating a post validates comment group belongs to same diary or is global`() {
        transaction {
            val (user1, user2) = signUsersUp()
            groupService.createAccessGroup(user2, testUser2.login, "user2-group")
            val user2GroupId = UUID.fromString(groupService.getAccessGroups(user2, testUser2.login).content["user2-group"])

            assertFailsWith<InvalidAccessGroupException> {
                postService.addPost(user1, createPostPostData(title = "Bad Comment Group", commentGroup = user2GroupId))
            }

            rollback()
        }
    }

    @Test
    fun `creating a post validates reaction group belongs to same diary or is global`() {
        transaction {
            val (user1, user2) = signUsersUp()
            groupService.createAccessGroup(user2, testUser2.login, "user2-group")
            val user2GroupId = UUID.fromString(groupService.getAccessGroups(user2, testUser2.login).content["user2-group"])

            assertFailsWith<InvalidAccessGroupException> {
                postService.addPost(user1, createPostPostData(title = "Bad React Group", reactionGroup = user2GroupId))
            }

            rollback()
        }
    }

    @Test
    fun `creating a post validates comment reaction group belongs to same diary or is global`() {
        transaction {
            val (user1, user2) = signUsersUp()
            groupService.createAccessGroup(user2, testUser2.login, "user2-group")
            val user2GroupId = UUID.fromString(groupService.getAccessGroups(user2, testUser2.login).content["user2-group"])

            assertFailsWith<InvalidAccessGroupException> {
                postService.addPost(user1, createPostPostData(title = "Bad CR Group", commentReactionGroup = user2GroupId))
            }

            rollback()
        }
    }

    @Test
    fun `creating a post validates reaction subset belongs to same diary`() {
        transaction {
            val (user1, user2) = signUsersUp()
            // Create a reaction subset for user2
            val subsetId = reactionService.createReactionSubset(user2, testUser2.login, "user2-subset", emptyList())

            assertFailsWith<WrongUserException> {
                postService.addPost(user1, PostDto.Create(
                    uri = "", avatar = "", title = "Bad Subset", text = "", isPreface = false,
                    isEncrypted = false, classes = "", tags = emptySet(),
                    readGroupId = groupService.everyoneGroupUUID,
                    commentGroupId = groupService.everyoneGroupUUID,
                    reactionGroupId = groupService.everyoneGroupUUID,
                    commentReactionGroupId = groupService.everyoneGroupUUID,
                    reactionSubset = subsetId,
                ))
            }

            rollback()
        }
    }

    @Test
    fun `creating a post with blank URI generates a URI`() {
        transaction {
            val (user1, _) = signUsersUp()
            val post = postService.addPost(user1, createPostPostData(title = "Auto URI Post", uri = ""))

            assertTrue(post.uri.isNotBlank())
            assertEquals("auto-uri-post", post.uri)

            rollback()
        }
    }

    @Test
    fun `creating a post with invalid explicit URI fails`() {
        transaction {
            val (user1, _) = signUsersUp()
            assertFailsWith<InvalidUriException> {
                postService.addPost(user1, createPostPostData(title = "Bad URI", uri = "has spaces"))
            }

            rollback()
        }
    }

    @Test
    fun `creating a post with already used explicit URI fails`() {
        transaction {
            val (user1, _) = signUsersUp()
            postService.addPost(user1, createPostPostData(title = "First", uri = "taken-uri"))

            assertFailsWith<UriIsBusyException> {
                postService.addPost(user1, createPostPostData(title = "Second", uri = "taken-uri"))
            }

            rollback()
        }
    }

    @Test
    fun `creating a post with generated URI collision retries and generates a unique one`() {
        transaction {
            val (user1, _) = signUsersUp()
            // Create first post with title that generates "hello-world"
            postService.addPost(user1, createPostPostData(title = "Hello World"))
            // Create second post with same title - should generate a unique prefixed URI
            val post2 = postService.addPost(user1, createPostPostData(title = "Hello World"))

            assertNotEquals("hello-world", post2.uri)
            assertTrue(post2.uri.contains("hello-world"))

            rollback()
        }
    }

    @Test
    fun `updating a post by owner succeeds`() {
        transaction {
            val (user1, _) = signUsersUp()
            val post = postService.addPost(user1, createPostPostData(title = "Original"))

            val updated = postService.updatePost(user1, createPostUpdateData(
                id = post.id,
                title = "Updated Title",
                text = "Updated Text"
            ))

            assertEquals("Updated Title", updated.title)
            assertEquals("Updated Text", updated.text)

            rollback()
        }
    }

    @Test
    fun `updating a post by non-owner fails`() {
        transaction {
            val (user1, user2) = signUsersUp()
            val post = postService.addPost(user1, createPostPostData(title = "Original"))

            assertFailsWith<WrongUserException> {
                postService.updatePost(user2, createPostUpdateData(id = post.id, title = "Hacked"))
            }

            rollback()
        }
    }

    @Test
    fun `updating a post changes URI when title changes and URI is blank or changed`() {
        transaction {
            val (user1, _) = signUsersUp()
            val post = postService.addPost(user1, createPostPostData(title = "Old Title"))
            assertEquals("old-title", post.uri)

            val updated = postService.updatePost(user1, createPostUpdateData(
                id = post.id,
                title = "New Title",
                uri = ""
            ))

            assertEquals("new-title", updated.uri)

            rollback()
        }
    }

    @Test
    fun `updating a post keeps URI when title unchanged and URI unchanged`() {
        transaction {
            val (user1, _) = signUsersUp()
            val post = postService.addPost(user1, createPostPostData(title = "Same Title"))

            val updated = postService.updatePost(user1, createPostUpdateData(
                id = post.id,
                title = "Same Title",
                uri = post.uri
            ))

            assertEquals(post.uri, updated.uri)

            rollback()
        }
    }

    @Test
    fun `updating a post updates comment reaction group correctly`() {
        transaction {
            val (user1, _) = signUsersUp()
            val post = postService.addPost(user1, createPostPostData(title = "CR Group Test"))

            val updated = postService.updatePost(user1, createPostUpdateData(
                id = post.id,
                title = "CR Group Test",
                uri = post.uri,
                commentReactionGroup = groupService.registeredGroupUUID
            ))

            assertEquals(groupService.registeredGroupUUID, updated.commentReactionGroupId)

            rollback()
        }
    }

    @Test
    fun `updating a post updates tags correctly by adding new tags`() {
        transaction {
            val (user1, _) = signUsersUp()
            val post = postService.addPost(user1, createPostPostData(title = "Tag Test", tags = setOf("a")))

            val updated = postService.updatePost(user1, createPostUpdateData(
                id = post.id,
                title = "Tag Test",
                uri = post.uri,
                tags = setOf("a", "b")
            ))

            assertEquals(setOf("a", "b"), updated.tags)

            rollback()
        }
    }

    @Test
    fun `updating a post updates tags correctly by removing missing tags`() {
        transaction {
            val (user1, _) = signUsersUp()
            val post = postService.addPost(user1, createPostPostData(title = "Tag Test", tags = setOf("a", "b")))

            val updated = postService.updatePost(user1, createPostUpdateData(
                id = post.id,
                title = "Tag Test",
                uri = post.uri,
                tags = setOf("a")
            ))

            assertEquals(setOf("a"), updated.tags)

            rollback()
        }
    }

    @Test
    fun `updating a post preserves tags not removed`() {
        transaction {
            val (user1, _) = signUsersUp()
            val post = postService.addPost(user1, createPostPostData(title = "Tag Test", tags = setOf("a", "b", "c")))

            val updated = postService.updatePost(user1, createPostUpdateData(
                id = post.id,
                title = "Tag Test",
                uri = post.uri,
                tags = setOf("a", "c", "d")
            ))

            assertEquals(setOf("a", "c", "d"), updated.tags)

            rollback()
        }
    }

    @Test
    fun `updating a post rejects invalid access groups from another diary`() {
        transaction {
            val (user1, user2) = signUsersUp()
            val post = postService.addPost(user1, createPostPostData(title = "Test"))

            groupService.createAccessGroup(user2, testUser2.login, "other-group")
            val otherGroupId = UUID.fromString(groupService.getAccessGroups(user2, testUser2.login).content["other-group"])

            assertFailsWith<InvalidAccessGroupException> {
                postService.updatePost(user1, createPostUpdateData(
                    id = post.id,
                    title = "Test",
                    uri = post.uri,
                    readGroup = otherGroupId,
                ))
            }

            rollback()
        }
    }

    @Test
    fun `deleting a post archives it rather than physically removing it`() {
        transaction {
            val (user1, _) = signUsersUp()
            val post = postService.addPost(user1, createPostPostData(title = "To Delete"))

            postService.deletePost(user1, post.id)

            // Should be archived but still in DB
            val entity = PostEntity.findById(post.id)
            assertNotNull(entity)
            assertTrue(entity.isArchived)

            rollback()
        }
    }

    @Test
    fun `deleting a post randomizes URI after archive`() {
        transaction {
            val (user1, _) = signUsersUp()
            val post = postService.addPost(user1, createPostPostData(title = "To Delete"))
            val originalUri = post.uri

            postService.deletePost(user1, post.id)

            val entity = PostEntity.findById(post.id)
            assertNotNull(entity)
            assertNotEquals(originalUri, entity.uri)

            rollback()
        }
    }

    @Test
    fun `hiding a post sets isHidden true`() {
        transaction {
            val (user1, _) = signUsersUp()
            val post = postService.addPost(user1, createPostPostData(title = "To Hide"))

            postService.hidePost(user1, post.id)

            val entity = PostEntity.findById(post.id)!!
            assertTrue(entity.isHidden)

            rollback()
        }
    }

    @Test
    fun `showing a post sets isHidden false`() {
        transaction {
            val (user1, _) = signUsersUp()
            val post = postService.addPost(user1, createPostPostData(title = "To Show", isHidden = true))

            postService.showPost(user1, post.id)

            val entity = PostEntity.findById(post.id)!!
            assertFalse(entity.isHidden)

            rollback()
        }
    }

    @Test
    fun `hiding or showing a post by non-owner fails`() {
        transaction {
            val (user1, user2) = signUsersUp()
            val post = postService.addPost(user1, createPostPostData(title = "Not Yours"))

            assertFailsWith<WrongUserException> {
                postService.hidePost(user2, post.id)
            }
            assertFailsWith<WrongUserException> {
                postService.showPost(user2, post.id)
            }

            rollback()
        }
    }

    // --- URI generation ---

    @Test
    fun `URI validation accepts letters, digits, and hyphens`() {
        transaction {
            val (user1, _) = signUsersUp()
            val post = postService.addPost(user1, createPostPostData(title = "Test", uri = "abc-123-XYZ"))
            assertEquals("abc-123-XYZ", post.uri)

            rollback()
        }
    }

    @Test
    fun `URI validation rejects spaces`() {
        transaction {
            val (user1, _) = signUsersUp()
            assertFailsWith<InvalidUriException> {
                postService.addPost(user1, createPostPostData(title = "Test", uri = "has space"))
            }

            rollback()
        }
    }

    @Test
    fun `URI validation rejects underscores`() {
        transaction {
            val (user1, _) = signUsersUp()
            assertFailsWith<InvalidUriException> {
                postService.addPost(user1, createPostPostData(title = "Test", uri = "has_underscore"))
            }

            rollback()
        }
    }

    @Test
    fun `URI generation transliterates Cyrillic correctly for supported characters`() {
        transaction {
            val (user1, _) = signUsersUp()
            val post = postService.addPost(user1, createPostPostData(title = "Привет мир"))

            assertEquals("privet-mir", post.uri)

            rollback()
        }
    }

    @Test
    fun `URI generation strips unsupported punctuation`() {
        transaction {
            val (user1, _) = signUsersUp()
            val post = postService.addPost(user1, createPostPostData(title = "Hello! World?"))

            assertEquals("hello-world", post.uri)

            rollback()
        }
    }

    @Test
    fun `URI generation collapses whitespace into single hyphens`() {
        transaction {
            val (user1, _) = signUsersUp()
            val post = postService.addPost(user1, createPostPostData(title = "Hello   World"))

            assertEquals("hello-world", post.uri)

            rollback()
        }
    }

    @Test
    fun `URI generation falls back to random UUID when title yields blank slug`() {
        transaction {
            val (user1, _) = signUsersUp()
            val post = postService.addPost(user1, createPostPostData(title = "!!!"))

            assertTrue(post.uri.isNotBlank())
            // UUID format check - should contain hyphens typical of UUID
            assertTrue(post.uri.contains("-"))

            rollback()
        }
    }

    @Test
    fun `URI busy check detects existing post in same diary`() {
        transaction {
            val (user1, _) = signUsersUp()
            postService.addPost(user1, createPostPostData(title = "Test", uri = "busy-uri"))

            assertFailsWith<UriIsBusyException> {
                postService.addPost(user1, createPostPostData(title = "Test2", uri = "busy-uri"))
            }

            rollback()
        }
    }

    @Test
    fun `URI busy check does not conflict across different diaries if that is intended by schema`() {
        transaction {
            val (user1, user2) = signUsersUp()
            postService.addPost(user1, createPostPostData(title = "Test", uri = "same-uri"))

            // user2 can use same URI in different diary
            val post2 = postService.addPost(user2, createPostPostData(title = "Test", uri = "same-uri"))
            assertEquals("same-uri", post2.uri)

            rollback()
        }
    }

    @Test
    fun `generated URI prefix retry increases prefix length when collisions repeat`() {
        transaction {
            val (user1, _) = signUsersUp()
            postService.addPost(user1, createPostPostData(title = "Repeat Title"))
            val second = postService.addPost(user1, createPostPostData(title = "Repeat Title"))

            // Second should have a prefixed URI
            assertTrue(second.uri.endsWith("repeat-title"))
            assertNotEquals("repeat-title", second.uri)

            rollback()
        }
    }
}
