package fi.lipp.blog

import fi.lipp.blog.data.*
import fi.lipp.blog.domain.DiaryEntity
import fi.lipp.blog.domain.PostEntity
import fi.lipp.blog.model.Page
import fi.lipp.blog.model.Pageable
import fi.lipp.blog.model.TagPolicy
import fi.lipp.blog.model.exceptions.PostNotFoundException
import fi.lipp.blog.model.exceptions.WrongUserException
import fi.lipp.blog.repository.*
import fi.lipp.blog.service.MailService
import fi.lipp.blog.service.NotificationService
import fi.lipp.blog.service.PostService
import fi.lipp.blog.service.ReactionService
import fi.lipp.blog.service.Viewer
import fi.lipp.blog.service.implementations.PostServiceImpl
import fi.lipp.blog.service.implementations.ReactionServiceImpl
import fi.lipp.blog.service.implementations.StorageServiceImpl
import fi.lipp.blog.service.implementations.UserServiceImpl
import fi.lipp.blog.stubs.ApplicationPropertiesStub
import fi.lipp.blog.stubs.PasswordEncoderStub
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.AfterClass
import org.junit.Assert.*
import org.junit.BeforeClass
import org.junit.Test
import org.mockito.Mockito.mock
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull

class PostServiceTests : UnitTestBase() {
    companion object {
        @JvmStatic
        @BeforeClass
        fun setUp() {
            postService = PostServiceImpl(groupService, storageService, reactionService, notificationService)
        }

        @JvmStatic
        @AfterClass
        fun tearDown() {
        }

        private val properties = ApplicationPropertiesStub()
        private val encoder = PasswordEncoderStub()
        private val mailService = mock<MailService>()
        private val notificationService = mock<NotificationService>()
        private val storageService = StorageServiceImpl(properties)
        private val userService = UserServiceImpl(encoder, mailService, storageService, groupService, notificationService)
        private lateinit var postService: PostService

        private val testUser = UserDto.Registration(
            login = "barabaka",
            email = "barabaka@mail.com",
            password = "password123",
            nickname = "dog_lover37",
            language = Language.EN,
            timezone = "Europe/Moscow",
        )
        private val testUser2 = UserDto.Registration(
            login = "bigbabyboy",
            email = "piecelovingkebab@proton.com",
            password = "secure_password",
            nickname = "cat_hater44",
            language = Language.RU,
            timezone = "Europe/Moscow",
        )
    }

    @Test
    fun `test adding post`() {
        transaction {
            val pageable = Pageable(1, 10, SortOrder.DESC)
            val (user1, _) = signUsersUp()
            var page = getPosts(userId = user1, pageable = pageable)
            assertEquals(Page(emptyList(), 1, 0), page)

            val post1 = createPostPostData(title = "Hello World")
            postService.addPost(user1, post1)
            page = getPosts(userId = user1, pageable = pageable)
            assertEquals(1, page.currentPage)
            assertEquals(1, page.totalPages)
            assertEquals(1, page.content.size)
            val addedPost = page.content.first()
            assertEquals("hello-world", addedPost.uri)
            assertEquals(post1.title, addedPost.title)
            assertEquals(post1.text, addedPost.text)
            assertEquals(post1.tags, addedPost.tags)
            assertEquals(post1.classes, addedPost.classes)
            assertEquals(post1.avatar, addedPost.avatar)
            assertEquals(post1.isPreface, addedPost.isPreface)
            assertEquals(true, addedPost.isCommentable)
            assertEquals(post1.isEncrypted, addedPost.isEncrypted)
            assertEquals(testUser.login, addedPost.authorLogin)
            assertEquals(testUser.nickname, addedPost.authorNickname)
            assertNow(addedPost.creationTime)

            rollback()
        }
    }

    @Test
    fun `test updating post`() {
        transaction {
            val pageable = Pageable(1, 10, SortOrder.DESC)
            val (user1, _) = signUsersUp()
            var page = getPosts(userId = user1, pageable = pageable)
            assertEquals(Page(emptyList(), 1, 0), page)

            val post1 = createPostPostData(title = "Hello World")
            postService.addPost(user1, post1)
            page = getPosts(userId = user1, pageable = pageable)
            val addedPost = page.content.first()

            val newPost = createPostUpdateData(id = addedPost.id, avatar = "new url", title = "new title", text = "new text", readGroup = groupService.privateGroupUUID, commentGroup = groupService.privateGroupUUID, isEncrypted = !post1.isEncrypted, classes = "small", tags = setOf("cat", "dog"))
            postService.updatePost(user1, newPost)
            page = getPosts(userId = user1, pageable = pageable)
            assertEquals(1, page.currentPage)
            assertEquals(1, page.totalPages)
            assertEquals(1, page.content.size)
            val updatedPost = page.content.first()
            assertEquals("new-title", updatedPost.uri)
            assertEquals(newPost.title, updatedPost.title)
            assertEquals(newPost.text, updatedPost.text)
            assertEquals(newPost.tags, updatedPost.tags)
            assertEquals(newPost.classes, updatedPost.classes)
            assertEquals(newPost.avatar, updatedPost.avatar)
            assertEquals(addedPost.isPreface, updatedPost.isPreface)
            assertEquals(true, updatedPost.isCommentable)
            assertEquals(newPost.isEncrypted, updatedPost.isEncrypted)
            assertEquals(testUser.login, updatedPost.authorLogin)
            assertEquals(testUser.nickname, updatedPost.authorNickname)
            assertEquals(addedPost.creationTime, updatedPost.creationTime)

            rollback()
        }
    }

    @Test
    fun `test updating post url`() {
        transaction {
            val pageable = Pageable(1, 10, SortOrder.DESC)
            val (user1, _) = signUsersUp()
            var page = getPosts(userId = user1, pageable = pageable)
            assertEquals(Page(emptyList(), 1, 0), page)

            val post1 = createPostPostData(title = "Hello World")
            postService.addPost(user1, post1)
            page = getPosts(userId = user1, pageable = pageable)
            val addedPost = page.content.first()

            val newPost = createPostUpdateData(id = addedPost.id, avatar = "new url", uri = "new-uri", title = "new title", text = "new text", readGroup = groupService.privateGroupUUID, commentGroup = groupService.privateGroupUUID, isEncrypted = !post1.isEncrypted, classes = "small", tags = setOf("cat", "dog"))
            postService.updatePost(user1, newPost)
            page = getPosts(userId = user1, pageable = pageable)
            assertEquals(1, page.currentPage)
            assertEquals(1, page.totalPages)
            assertEquals(1, page.content.size)
            val updatedPost = page.content.first()
            assertEquals("new-uri", updatedPost.uri)
            assertEquals(newPost.title, updatedPost.title)
            assertEquals(newPost.text, updatedPost.text)
            assertEquals(newPost.tags, updatedPost.tags)
            assertEquals(newPost.classes, updatedPost.classes)
            assertEquals(newPost.avatar, updatedPost.avatar)
            assertEquals(addedPost.isPreface, updatedPost.isPreface)
            assertEquals(true, updatedPost.isCommentable)
            assertEquals(newPost.isEncrypted, updatedPost.isEncrypted)
            assertEquals(testUser.login, updatedPost.authorLogin)
            assertEquals(testUser.nickname, updatedPost.authorNickname)
            assertEquals(addedPost.creationTime, updatedPost.creationTime)

            rollback()
        }
    }

    @Test
    fun `test updating post of other user`() {
        transaction {
            val pageable = Pageable(1, 10, SortOrder.DESC)
            val (user1, user2) = signUsersUp()
            var page = getPosts(userId = user1, pageable = pageable)
            assertEquals(Page(emptyList(), 1, 0), page)

            val post1 = createPostPostData(title = "Hello World")
            postService.addPost(user1, post1)
            page = getPosts(userId = user1, pageable = pageable)
            val addedPost = page.content.first()

            val newPost = createPostUpdateData(id = addedPost.id, avatar = "new url", title = "new title", text = "new text", readGroup = groupService.privateGroupUUID, commentGroup = groupService.privateGroupUUID, isEncrypted = !post1.isEncrypted, classes = "small", tags = setOf("cat", "dog"))
            assertThrows(WrongUserException::class.java) {
                postService.updatePost(user2, newPost)
            }

            page = getPosts(userId = user1, pageable = pageable)
            assertEquals(1, page.currentPage)
            assertEquals(1, page.totalPages)
            assertEquals(1, page.content.size)
            val updatedPost = page.content.first()
            assertEquals(addedPost, updatedPost)

            rollback()
        }
    }

    @Test
    fun `test add preface`() {
        transaction {
            val pageable = Pageable(1, 10, SortOrder.DESC)
            val (user1, user2) = signUsersUp()
            var page = getPosts(userId = user1, pageable = pageable)
            assertEquals(Page(emptyList(), 1, 0), page)

            val preface = createPostPostData(title = "Welcome to my blog", text = "preface text", isPreface = true, classes = "rounded", tags = setOf("info", "photos"))
            postService.addPost(user1, preface)
            page = getPosts(userId = user1, pageable = pageable)
            assertEquals(0, page.totalPages)
            assertEquals(0, page.content.size)

            val foundPreface1 = postService.getPreface(Viewer.Registered(user1), testUser.login)!!
            val foundPreface2 = postService.getPreface(Viewer.Registered(user2), testUser.login)!!
            assertEquals(foundPreface1, foundPreface2)

            assertEquals("welcome-to-my-blog", foundPreface1.uri)
            assertEquals(preface.title, foundPreface1.title)
            assertEquals(preface.text, foundPreface1.text)
            assertEquals(preface.tags, foundPreface1.tags)
            assertEquals(preface.classes, foundPreface1.classes)
            assertEquals(preface.avatar, foundPreface1.avatar)
            assertEquals(preface.isPreface, foundPreface1.isPreface)
            assertEquals(true, foundPreface1.isCommentable)
            assertEquals(preface.isEncrypted, foundPreface1.isEncrypted)
            assertEquals(testUser.login, foundPreface1.authorLogin)
            assertEquals(testUser.nickname, foundPreface1.authorNickname)
            assertNow(foundPreface1.creationTime)

            rollback()
        }
    }

    @Test
    fun `test add preface where there is existing preface`() {
        transaction {
            val (user1, _) = signUsersUp()

            val preface1 = createPostPostData(title = "Welcome to my blog", text = "preface text", isPreface = true, classes = "rounded", tags = setOf("info", "photos"))
            postService.addPost(user1, preface1)
            val foundPreface1 = postService.getPreface(Viewer.Registered(user1), testUser.login)!!

            val preface2 = createPostPostData(title = "Welcome to my blog2", text = "preface text2", isPreface = true, classes = "rounded2", tags = setOf("info2", "photos2"))
            postService.addPost(user1, preface2)
            val foundPreface2 = postService.getPreface(Viewer.Registered(user1), testUser.login)!!
            assertNotEquals(foundPreface1.id, foundPreface2.id)

            assertEquals("welcome-to-my-blog2", foundPreface2.uri)
            assertEquals(preface2.title, foundPreface2.title)
            assertEquals(preface2.text, foundPreface2.text)
            assertEquals(preface2.tags, foundPreface2.tags)
            assertEquals(preface2.classes, foundPreface2.classes)
            assertEquals(preface2.avatar, foundPreface2.avatar)
            assertEquals(preface2.isPreface, foundPreface2.isPreface)
            assertEquals(true, foundPreface2.isCommentable)
            assertEquals(preface2.isEncrypted, foundPreface2.isEncrypted)
            assertEquals(testUser.login, foundPreface2.authorLogin)
            assertEquals(testUser.nickname, foundPreface2.authorNickname)
            assertNow(foundPreface2.creationTime)

            rollback()
        }
    }

    @Test
    fun `test update preface`() {
        transaction {
            val pageable = Pageable(1, 10, SortOrder.DESC)
            val (user1, _) = signUsersUp()

            val preface = createPostPostData(title = "Welcome to my blog", text = "preface text", isPreface = true, classes = "rounded", tags = setOf("info", "photos"))
            postService.addPost(user1, preface)
            val foundPreface1 = postService.getPreface(Viewer.Registered(user1), testUser.login)!!

            val prefaceUpdate = createPostUpdateData(id = foundPreface1.id, title = "Welcome to my blog2", text = "preface text2", classes = "rounded2", tags = setOf("info2", "photos2"))
            postService.updatePost(user1, prefaceUpdate)
            val updatedPreface = postService.getPreface(Viewer.Registered(user1), testUser.login)!!

            assertEquals(foundPreface1.id, updatedPreface.id)
            assertEquals("welcome-to-my-blog2", updatedPreface.uri)
            assertEquals(prefaceUpdate.title, updatedPreface.title)
            assertEquals(prefaceUpdate.text, updatedPreface.text)
            assertEquals(prefaceUpdate.tags, updatedPreface.tags)
            assertEquals(prefaceUpdate.classes, updatedPreface.classes)
            assertEquals(prefaceUpdate.avatar, updatedPreface.avatar)
            assertEquals(true, updatedPreface.isPreface)
            assertEquals(true, updatedPreface.isCommentable)
            assertEquals(prefaceUpdate.isEncrypted, updatedPreface.isEncrypted)
            assertEquals(testUser.login, updatedPreface.authorLogin)
            assertEquals(testUser.nickname, updatedPreface.authorNickname)
            assertNow(updatedPreface.creationTime)

            val page = getPosts(userId = user1, pageable = pageable)
            assertEquals(Page(emptyList(), 1, 0), page)

            rollback()
        }
    }

    @Test
    fun `test update preface of other user`() {
        transaction {
            val pageable = Pageable(1, 10, SortOrder.DESC)
            val (user1, user2) = signUsersUp()

            val preface = createPostPostData(title = "Welcome to my blog", text = "preface text", isPreface = true, classes = "rounded", tags = setOf("info", "photos"))
            postService.addPost(user1, preface)
            val foundPreface1 = postService.getPreface(Viewer.Registered(user1), testUser.login)!!

            val prefaceUpdate = createPostUpdateData(id = foundPreface1.id, title = "Welcome to my blog2", text = "preface text2", classes = "rounded2", tags = setOf("info2", "photos2"))
            assertThrows(WrongUserException::class.java) {
                postService.updatePost(user2, prefaceUpdate)
            }
            val updatedPreface = postService.getPreface(Viewer.Registered(user1), testUser.login)!!

            assertEquals(foundPreface1.id, updatedPreface.id)
            assertEquals("welcome-to-my-blog", updatedPreface.uri)
            assertEquals(foundPreface1.title, updatedPreface.title)
            assertEquals(foundPreface1.text, updatedPreface.text)
            assertEquals(foundPreface1.tags, updatedPreface.tags)
            assertEquals(foundPreface1.classes, updatedPreface.classes)
            assertEquals(foundPreface1.avatar, updatedPreface.avatar)
            assertEquals(foundPreface1.isPreface, updatedPreface.isPreface)
            assertEquals(true, updatedPreface.isCommentable)
            assertEquals(foundPreface1.isEncrypted, updatedPreface.isEncrypted)
            assertEquals(foundPreface1.authorLogin, testUser.login)
            assertEquals(foundPreface1.authorNickname, testUser.nickname)
            assertNow(updatedPreface.creationTime)

            val page = getPosts(userId = user1, pageable = pageable)
            assertEquals(Page(emptyList(), 1, 0), page)

            rollback()
        }
    }

    @Test
    fun `test getting post by url`() {
        transaction {
            val (user1, user2) = signUsersUp()

            val post1 = createPostPostData(title = "Hello World")
            postService.addPost(user1, post1)
            val foundPost1 = postService.getPost(Viewer.Registered(user1), testUser.login, "hello-world")
            val foundPost2 = postService.getPost(Viewer.Registered(user2), testUser.login, "hello-world")
            assertNotNull(foundPost1)
            assertEquals(foundPost1, foundPost2)

            rollback()
        }
    }

    @Test
    fun `test getting private post by url`() {
        transaction {
            val (user1, user2) = signUsersUp()

            val post1 = createPostPostData(title = "Hello World", readGroup = groupService.privateGroupUUID)
            postService.addPost(user1, post1)
            val foundPost1 = postService.getPost(Viewer.Registered(user1), testUser.login, "hello-world")
            assertThrows(PostNotFoundException::class.java) {
                postService.getPost(Viewer.Registered(user2), testUser.login, "hello-world")
            }
            assertNotNull(foundPost1)

            rollback()
        }
    }

    @Test
    fun `test private preface`() {
        transaction {
            val pageable = Pageable(1, 10, SortOrder.DESC)
            val (user1, user2) = signUsersUp()
            var page = getPosts(userId = user1, pageable = pageable)
            assertEquals(Page(emptyList(), 1, 0), page)

            val preface = createPostPostData(title = "Welcome to my blog", text = "preface text", isPreface = true, readGroup = groupService.privateGroupUUID, commentGroup = groupService.privateGroupUUID, classes = "rounded", tags = setOf("info", "photos"))
            postService.addPost(user1, preface)
            page = getPosts(userId = user1, pageable = pageable)
            assertEquals(0, page.totalPages)
            assertEquals(0, page.content.size)

            val foundPreface1 = postService.getPreface(Viewer.Registered(user1), testUser.login)!!
            val foundPreface2 = postService.getPreface(Viewer.Registered(user2), testUser.login)
            assertNotNull(foundPreface1)
            assertNull(foundPreface2)

            rollback()
        }
    }

    @Test
    fun `test pagination`() {
        transaction {
            val (user1, _) = signUsersUp()

            val post1 = createPostPostData(title = "post1")
            postService.addPost(user1, post1)
            Thread.sleep(10)
            val post2 = createPostPostData(title = "post2")
            postService.addPost(user1, post2)
            Thread.sleep(10)
            val post3 = createPostPostData(title = "post3")
            postService.addPost(user1, post3)
            Thread.sleep(10)
            val post4 = createPostPostData(title = "post4")
            postService.addPost(user1, post4)
            Thread.sleep(10)
            val post5 = createPostPostData(title = "post5")
            postService.addPost(user1, post5)

            var page = getPosts(userId = user1, pageable = Pageable(1, 4, SortOrder.DESC))
            assertEquals(1, page.currentPage)
            assertEquals(2, page.totalPages)
            assertEquals(listOf("post5", "post4", "post3", "post2"), page.content.map { it.title })


            page = getPosts(userId = user1, pageable = Pageable(2, 4, SortOrder.DESC))
            assertEquals(2, page.currentPage)
            assertEquals(2, page.totalPages)
            assertEquals(listOf("post1"), page.content.map { it.title })

            rollback()
        }
    }

    @Test
    fun `test search by tags union`() {
        transaction {
            val (user1, _) = signUsersUp()

            val post1 = createPostPostData(title = "post1", tags = setOf("1", "2"))
            postService.addPost(user1, post1)
            Thread.sleep(10)
            val post2 = createPostPostData(title = "post2", tags = setOf("2", "3"))
            postService.addPost(user1, post2)
            Thread.sleep(10)
            val post3 = createPostPostData(title = "post3", tags = setOf("2", "3", "4"))
            postService.addPost(user1, post3)
            Thread.sleep(10)
            val post4 = createPostPostData(title = "post4", tags = setOf("3", "4"))
            postService.addPost(user1, post4)
            Thread.sleep(10)
            val post5 = createPostPostData(title = "post5", tags = setOf("4", "5"))
            postService.addPost(user1, post5)

            val page = getPosts(userId = user1, tags = TagPolicy.UNION to setOf("2", "3"), pageable = Pageable(1, 4, SortOrder.DESC))
            assertEquals(1, page.currentPage)
            assertEquals(1, page.totalPages)
            assertEquals(listOf("post4", "post3", "post2", "post1"), page.content.map { it.title })

            rollback()
        }
    }

    @Test
    fun `test search by tags intersection`() {
        transaction {
            val (user1, _) = signUsersUp()

            val post1 = createPostPostData(title = "post1", tags = setOf("1", "2"))
            postService.addPost(user1, post1)
            Thread.sleep(10)
            val post2 = createPostPostData(title = "post2", tags = setOf("2", "3"))
            postService.addPost(user1, post2)
            Thread.sleep(10)
            val post3 = createPostPostData(title = "post3", tags = setOf("2", "3", "4"))
            postService.addPost(user1, post3)
            Thread.sleep(10)
            val post4 = createPostPostData(title = "post4", tags = setOf("3", "4"))
            postService.addPost(user1, post4)
            Thread.sleep(10)
            val post5 = createPostPostData(title = "post5", tags = setOf("4", "5"))
            postService.addPost(user1, post5)

            val page = getPosts(userId = user1, tags = TagPolicy.INTERSECTION to setOf("2", "3"), pageable = Pageable(1, 4, SortOrder.DESC))
            assertEquals(1, page.currentPage)
            assertEquals(1, page.totalPages)
            assertEquals(listOf("post3", "post2"), page.content.map { it.title })

            rollback()
        }
    }

    @Test
    fun `test get user posts with different access groups`() {
        transaction {
            val users = signUsersUp(3)
            val (user1Id, user1Login) = users[0]
            val (user2Id, user2Login) = users[1]
            val (user3Id, _) = users[2]

            // Create post with EVERYONE access
            val post1 = createPostPostData(title = "post1")
            postService.addPost(user1Id, post1)
            Thread.sleep(10)

            // Create post with REGISTERED_USERS access
            val post2 = createPostPostData(title = "post2")
            postService.addPost(user1Id, post2)
            Thread.sleep(10)

            // Create post with CUSTOM access
            groupService.createAccessGroup(user1Id, user1Login, "Custom Group")
            val customGroupUUID = UUID.fromString(groupService.getAccessGroups(user1Id, user1Login)
                .content["Custom Group"]!!)

            val post3 = createPostPostData(title = "post3", readGroup = customGroupUUID)
            postService.addPost(user1Id, post3)
            Thread.sleep(10)

            // Add user2 to custom group
            groupService.addUserToGroup(user1Id, user2Login, customGroupUUID)

            // Create archived and preface posts
            val post4 = createPostPostData(title = "post4", uri = "post4")
            postService.addPost(user1Id, post4)
            transaction {
                Posts.update({ Posts.uri eq "post4" }) {
                    it[isArchived] = true
                }
            }
            Thread.sleep(10)

            val post5 = createPostPostData(title = "post5", isPreface = true)
            postService.addPost(user1Id, post5)

            // Test access for user1 (owner)
            var page = getPosts(Viewer.Registered(user1Id), pageable = Pageable(0, 10, SortOrder.DESC))
            assertEquals(3, page.content.size)
            assertEquals(listOf("post3", "post2", "post1"), page.content.map { it.title })

            // Test access for user2 (has access to custom group)
            page = getPosts(Viewer.Registered(user2Id), pageable = Pageable(0, 10, SortOrder.DESC))
            assertEquals(3, page.content.size)
            assertEquals(listOf("post3", "post2", "post1"), page.content.map { it.title })

            // Test access for user3 (no access to custom group)
            page = getPosts(Viewer.Registered(user3Id), pageable = Pageable(0, 10, SortOrder.DESC))
            assertEquals(2, page.content.size)
            assertEquals(listOf("post2", "post1"), page.content.map { it.title })

            rollback()
        }
    }

    @Test
    fun `test get posts with access group filtering`() {
        transaction {
            val users = signUsersUp(4)
            val (user1Id, user1Login) = users[0]
            val (user2Id, user2Login) = users[1]
            val (user3Id, _) = users[2]
            val (user4Id, _) = users[3]

            // Create custom groups
            groupService.createAccessGroup(user1Id, user1Login, "Friends")
            val friendsGroupUUID = UUID.fromString(groupService.getAccessGroups(user1Id, user1Login).content["Friends"])

            groupService.createAccessGroup(user1Id, user1Login, "Family")
            val familyGroupUUID = UUID.fromString(groupService.getAccessGroups(user1Id, user1Login).content["Family"])

            // Add user2 to Friends group and user3 to Family group
            groupService.addUserToGroup(user1Id, user2Login, friendsGroupUUID)
            groupService.addUserToGroup(user1Id, users[2].second, familyGroupUUID)

            // Create posts with different access groups
            val post1 = createPostPostData(title = "everyone post") // Uses default everyoneGroupUUID
            postService.addPost(user1Id, post1)
            Thread.sleep(10)

            val post2 = createPostPostData(title = "friends post", readGroup = friendsGroupUUID)
            postService.addPost(user1Id, post2)
            Thread.sleep(10)

            val post3 = createPostPostData(title = "family post", readGroup = familyGroupUUID)
            postService.addPost(user1Id, post3)

            // Test owner access (user1) - can see all posts
            var page = getPosts(Viewer.Registered(user1Id), pageable = Pageable(0, 10, SortOrder.DESC))
            assertEquals(3, page.content.size)
            assertEquals(listOf("family post", "friends post", "everyone post"), 
                page.content.map { it.title })

            // Test user2 access (in Friends group) - can see friends and everyone posts
            page = getPosts(Viewer.Registered(user2Id), pageable = Pageable(0, 10, SortOrder.DESC))
            assertEquals(2, page.content.size)
            assertEquals(listOf("friends post", "everyone post"), 
                page.content.map { it.title })

            // Test user3 access (in Family group) - can see family and everyone posts
            page = getPosts(Viewer.Registered(user3Id), pageable = Pageable(0, 10, SortOrder.DESC))
            assertEquals(2, page.content.size)
            assertEquals(listOf("family post", "everyone post"), 
                page.content.map { it.title })

            // Test user4 access (no custom groups) - can see only everyone posts
            page = getPosts(Viewer.Registered(user4Id), pageable = Pageable(0, 10, SortOrder.DESC))
            assertEquals(1, page.content.size)
            assertEquals(listOf("everyone post"), 
                page.content.map { it.title })

            // Test anonymous access - can see only everyone posts
            page = getPosts(Viewer.Anonymous("127.0.0.1", "test"), pageable = Pageable(0, 10, SortOrder.DESC))
            assertEquals(1, page.content.size)
            assertEquals(listOf("everyone post"), 
                page.content.map { it.title })

            rollback()
        }
    }

    @Test
    fun `test friend-only posts`() {
        transaction {
            // Set up users
            val users = signUsersUp(4)
            val (user1Id, user1Login) = users[0]
            val (user2Id, user2Login) = users[1]
            val (user3Id, _) = users[2]
            val (user4Id, _) = users[3]

            // Create a post that's only visible to friends
            val friendPost = createPostPostData(title = "friends only post", readGroup = groupService.friendsGroupUUID)
            postService.addPost(user1Id, friendPost)

            // Create a post visible to everyone
            val publicPost = createPostPostData(title = "public post")
            postService.addPost(user1Id, publicPost)

            // Test owner access (user1) - can see all posts (owner can always see their own posts)
            var page = getPosts(Viewer.Registered(user1Id), pageable = Pageable(0, 10, SortOrder.DESC))
            assertEquals(2, page.content.size)
            assertEquals(listOf("public post", "friends only post"), page.content.map { it.title })

            // Test other user access (user2) - can only see public post (since they're not friends yet)
            page = getPosts(Viewer.Registered(user2Id), pageable = Pageable(0, 10, SortOrder.DESC))
            assertEquals(1, page.content.size)
            assertEquals(listOf("public post"), page.content.map { it.title })

            // Test other user access (user3) - can only see public post
            page = getPosts(Viewer.Registered(user3Id), pageable = Pageable(0, 10, SortOrder.DESC))
            assertEquals(1, page.content.size)
            assertEquals(listOf("public post"), page.content.map { it.title })

            // Test other user access (user4) - can only see public post
            page = getPosts(Viewer.Registered(user4Id), pageable = Pageable(0, 10, SortOrder.DESC))
            assertEquals(1, page.content.size)
            assertEquals(listOf("public post"), page.content.map { it.title })

            // Test anonymous access - can only see public post
            page = getPosts(Viewer.Anonymous("127.0.0.1", "test"), pageable = Pageable(0, 10, SortOrder.DESC))
            assertEquals(1, page.content.size)
            assertEquals(listOf("public post"), page.content.map { it.title })

            userService.sendFriendRequest(user1Id, FriendRequestDto.Create(user2Login, "", null))
            val friendRequests = userService.getReceivedFriendRequests(user2Id)
            assertEquals(1, friendRequests.size)
            userService.acceptFriendRequest(user2Id, friendRequests.first().id, null)

            page = getPosts(Viewer.Registered(user2Id), pageable = Pageable(0, 10, SortOrder.DESC))
            assertEquals(2, page.content.size)

            rollback()
        }
    }

    @Test
    fun `test friendship access control`() {
        transaction {
            // Set up users
            val users = signUsersUp(2)
            val (user1Id, _) = users[0]
            val (user2Id, _) = users[1]

            // Initially, user2 should not be in user1's friends group
            var inFriendsGroup = groupService.inGroup(Viewer.Registered(user2Id), groupService.friendsGroupUUID, user1Id)
            assertEquals(false, inFriendsGroup)

            // Directly insert a friendship record
            Friends.insert {
                it[user1] = user1Id
                it[user2] = user2Id
            }

            // Now user2 should be in user1's friends group
            inFriendsGroup = groupService.inGroup(Viewer.Registered(user2Id), groupService.friendsGroupUUID, user1Id)
            assertEquals(true, inFriendsGroup)

            rollback()
        }
    }

    @Test
    fun `test access group post hiding`() {
        transaction {
            val users = signUsersUp(3)
            val (user1Id, user1Login) = users[0]
            val (user2Id, user2Login) = users[1]
            val (user3Id, _) = users[2]

            // Create post with EVERYONE access (should be visible to all)
            val post1 = createPostPostData(title = "everyone post")
            postService.addPost(user1Id, post1)
            Thread.sleep(10)

            // Create post with REGISTERED_USERS access (should be visible only to registered users)
            val post2 = createPostPostData(title = "registered post", readGroup = groupService.registeredGroupUUID)
            postService.addPost(user1Id, post2)
            Thread.sleep(10)

            // Create post with CUSTOM access (should be visible only to group members)
            groupService.createAccessGroup(user1Id, user1Login, "Custom Group")
            val customGroupUUID = UUID.fromString(groupService.getAccessGroups(user1Id, user1Login).content["Custom Group"])

            val post3 = createPostPostData(title = "custom post", readGroup = customGroupUUID)
            postService.addPost(user1Id, post3)
            Thread.sleep(10)

            // Add user2 to custom group
            groupService.addUserToGroup(user1Id, user2Login, customGroupUUID)

            // Test anonymous user access (should see only EVERYONE posts)
            var page = getPosts(Viewer.Anonymous("127.0.0.1", "test"), pageable = Pageable(0, 10, SortOrder.DESC))
            assertEquals(1, page.content.size)
            assertEquals(listOf("everyone post"), page.content.map { it.title })

            // Test owner access (should see all posts)
            page = getPosts(Viewer.Registered(user1Id), pageable = Pageable(0, 10, SortOrder.DESC))
            assertEquals(3, page.content.size)
            assertEquals(listOf("custom post", "registered post", "everyone post"), page.content.map { it.title })

            // Test user2 access (in custom group, should see all except CUSTOM posts)
            page = getPosts(Viewer.Registered(user2Id), pageable = Pageable(0, 10, SortOrder.DESC))
            assertEquals(3, page.content.size)
            assertEquals(listOf("custom post", "registered post", "everyone post"), page.content.map { it.title })

            // Test user3 access (registered but not in custom group, should see REGISTERED and EVERYONE posts)
            page = getPosts(Viewer.Registered(user3Id), pageable = Pageable(0, 10, SortOrder.DESC))
            assertEquals(2, page.content.size)
            assertEquals(listOf("registered post", "everyone post"), page.content.map { it.title })

            rollback()
        }
    }

    @Test
    fun `test search by text`() {
        transaction {
            val (user1, _) = signUsersUp()

            val post1 = createPostPostData(title = "post1", text = "some text")
            postService.addPost(user1, post1)
            Thread.sleep(10)
            val post2 = createPostPostData(title = "post2", text = "some text")
            postService.addPost(user1, post2)
            Thread.sleep(10)
            val post3 = createPostPostData(title = "post3", text = "dog")
            postService.addPost(user1, post3)
            Thread.sleep(10)
            val post4 = createPostPostData(title = "post4", text = "funny dog")
            postService.addPost(user1, post4)
            Thread.sleep(10)
            val post5 = createPostPostData(title = "post5", text = "some text")
            postService.addPost(user1, post5)

            val page = getPosts(userId = user1, pattern = "dog", pageable = Pageable(1, 4, SortOrder.DESC))
            assertEquals(1, page.currentPage)
            assertEquals(1, page.totalPages)
            assertEquals(listOf("post4", "post3"), page.content.map { it.title })

            rollback()
        }
    }

    @Test
    fun `test search by author`() {
        transaction {
            val (user1, user2) = signUsersUp()

            val post1 = createPostPostData(title = "post1")
            postService.addPost(user1, post1)
            Thread.sleep(10)
            val post2 = createPostPostData(title = "post2")
            postService.addPost(user1, post2)
            Thread.sleep(10)
            val post3 = createPostPostData(title = "post3")
            postService.addPost(user1, post3)
            Thread.sleep(10)
            val post4 = createPostPostData(title = "post4")
            postService.addPost(user1, post4)
            Thread.sleep(10)
            val post5 = createPostPostData(title = "post5")
            postService.addPost(user1, post5)

            var page = getPosts(userId = user1, author = testUser.login, pageable = Pageable(1, 10, SortOrder.DESC))
            assertEquals(1, page.currentPage)
            assertEquals(1, page.totalPages)
            assertEquals(listOf("post5", "post4", "post3", "post2", "post1"), page.content.map { it.title })

            page = getPosts(userId = user1, author = testUser2.login, pageable = Pageable(1, 10, SortOrder.DESC))
            assertEquals(1, page.currentPage)
            assertEquals(0, page.totalPages)
            assertEquals(emptyList(), page.content)

            page = getPosts(userId = user2, author = testUser2.login, pageable = Pageable(1, 10, SortOrder.DESC))
            assertEquals(1, page.currentPage)
            assertEquals(0, page.totalPages)
            assertEquals(emptyList(), page.content)

            rollback()
        }
    }

    @Test
    fun `test search by diary`() {
        transaction {
            val (user1, user2) = signUsersUp()
            val diary1 = DiaryEntity.find { Diaries.owner eq user1 }.first()
            val diary2 = DiaryEntity.find { Diaries.owner eq user2 }.first()

            val post1 = createPostPostData(title = "post1")
            postService.addPost(user1, post1)
            Thread.sleep(10)
            val post2 = createPostPostData(title = "post2")
            postService.addPost(user1, post2)
            Thread.sleep(10)
            val post3 = createPostPostData(title = "post3")
            postService.addPost(user1, post3)
            Thread.sleep(10)
            val post4 = createPostPostData(title = "post4")
            postService.addPost(user1, post4)
            Thread.sleep(10)
            val post5 = createPostPostData(title = "post5")
            postService.addPost(user1, post5)

            var page = getPosts(userId = user1, diary = diary1.login, pageable = Pageable(1, 10, SortOrder.DESC))
            assertEquals(1, page.currentPage)
            assertEquals(1, page.totalPages)
            assertEquals(listOf("post5", "post4", "post3", "post2", "post1"), page.content.map { it.title })

            page = getPosts(userId = user1, diary = diary2.login, pageable = Pageable(1, 10, SortOrder.DESC))
            assertEquals(1, page.currentPage)
            assertEquals(0, page.totalPages)
            assertEquals(emptyList(), page.content)

            page = getPosts(userId = user2, diary = diary2.login, pageable = Pageable(1, 10, SortOrder.DESC))
            assertEquals(1, page.currentPage)
            assertEquals(0, page.totalPages)
            assertEquals(emptyList(), page.content)

            rollback()
        }
    }

    @Test
    fun `test deleting post`() {
        transaction {
            val (user1, _) = signUsersUp()

            val post1 = createPostPostData(title = "post1")
            postService.addPost(user1, post1)
            Thread.sleep(10)
            val post2 = createPostPostData(title = "post2")
            postService.addPost(user1, post2)
            Thread.sleep(10)
            val post3 = createPostPostData(title = "post3")
            postService.addPost(user1, post3)
            Thread.sleep(10)
            val post4 = createPostPostData(title = "post4")
            postService.addPost(user1, post4)
            Thread.sleep(10)
            val post5 = createPostPostData(title = "post5")
            postService.addPost(user1, post5)

            val foundPost3Page = getPosts(userId = user1, pattern = "post3", pageable = Pageable(1, 4, SortOrder.DESC))
            assertEquals(1, foundPost3Page.content.size)
            val foundPost3 = foundPost3Page.content.first()

            postService.deletePost(user1, foundPost3.id)
            val page = getPosts(userId = user1, pageable = Pageable(1, 10, SortOrder.DESC))
            assertEquals(1, page.currentPage)
            assertEquals(1, page.totalPages)
            assertEquals(listOf("post5", "post4", "post2", "post1"), page.content.map { it.title })

            rollback()
        }
    }

    @Test
    fun `test deleting post of other user`() {
        transaction {
            val (user1, user2) = signUsersUp()

            val post1 = createPostPostData(title = "post1")
            postService.addPost(user1, post1)
            Thread.sleep(10)
            val post2 = createPostPostData(title = "post2")
            postService.addPost(user1, post2)
            Thread.sleep(10)
            val post3 = createPostPostData(title = "post3")
            postService.addPost(user1, post3)
            Thread.sleep(10)
            val post4 = createPostPostData(title = "post4")
            postService.addPost(user1, post4)
            Thread.sleep(10)
            val post5 = createPostPostData(title = "post5")
            postService.addPost(user1, post5)

            val foundPost3Page = getPosts(userId = user1, pattern = "post3", pageable = Pageable(1, 4, SortOrder.DESC))
            assertEquals(1, foundPost3Page.content.size)
            val foundPost3 = foundPost3Page.content.first()

            assertThrows(WrongUserException::class.java) {
                postService.deletePost(user2, foundPost3.id)
            }
            val page = getPosts(userId = user1, pageable = Pageable(1, 10, SortOrder.DESC))
            assertEquals(1, page.currentPage)
            assertEquals(1, page.totalPages)
            assertEquals(listOf("post5", "post4", "post3", "post2", "post1"), page.content.map { it.title })

            rollback()
        }
    }

    @Test
    fun `test custom access groups`() {
        transaction {
            val pageable = Pageable(1, 4, SortOrder.DESC)

            val users = signUsersUp(10)
            val (user1, login1) = users[0]

            groupService.createAccessGroup(user1, login1, "empty group")
            val emptyGroupUUID = UUID.fromString(groupService.getAccessGroups(user1, login1).content["empty group"])

            groupService.createAccessGroup(user1, login1, "team members")
            val teamGroupUUID = UUID.fromString(groupService.getAccessGroups(user1, login1).content["team members"])
            groupService.addUserToGroup(user1, users[2].second, teamGroupUUID)
            groupService.addUserToGroup(user1, users[4].second, teamGroupUUID)
            groupService.addUserToGroup(user1, users[6].second, teamGroupUUID)
            groupService.addUserToGroup(user1, users[8].second, teamGroupUUID)

            val hiddenPost = createPostPostData(title = "post1", readGroup = emptyGroupUUID)
            postService.addPost(user1, hiddenPost)
            assertEquals(1, getPosts(userId = user1, pageable = pageable).content.size)
            assertEquals(0, getPosts(userId = users[1].first, pageable = pageable).content.size)
            assertEquals(0, getPosts(userId = users[2].first, pageable = pageable).content.size)
            assertEquals(0, getPosts(userId = users[3].first, pageable = pageable).content.size)
            assertEquals(0, getPosts(userId = users[4].first, pageable = pageable).content.size)
            assertEquals(0, getPosts(userId = users[5].first, pageable = pageable).content.size)
            assertEquals(0, getPosts(userId = users[6].first, pageable = pageable).content.size)
            assertEquals(0, getPosts(userId = users[7].first, pageable = pageable).content.size)
            assertEquals(0, getPosts(userId = users[8].first, pageable = pageable).content.size)
            assertEquals(0, getPosts(userId = users[9].first, pageable = pageable).content.size)

            val teamPost = createPostPostData(title = "post2", readGroup = teamGroupUUID)
            postService.addPost(user1, teamPost)
            assertEquals(2, getPosts(userId = users[0].first, pageable = pageable).content.size)
            assertEquals(0, getPosts(userId = users[1].first, pageable = pageable).content.size)
            assertEquals(1, getPosts(userId = users[2].first, pageable = pageable).content.size)
            assertEquals(0, getPosts(userId = users[3].first, pageable = pageable).content.size)
            assertEquals(1, getPosts(userId = users[4].first, pageable = pageable).content.size)
            assertEquals(0, getPosts(userId = users[5].first, pageable = pageable).content.size)
            assertEquals(1, getPosts(userId = users[6].first, pageable = pageable).content.size)
            assertEquals(0, getPosts(userId = users[7].first, pageable = pageable).content.size)
            assertEquals(1, getPosts(userId = users[8].first, pageable = pageable).content.size)
            assertEquals(0, getPosts(userId = users[9].first, pageable = pageable).content.size)

            rollback()
        }
    }

    @Test
    fun `test comment post`() {
        transaction {
            val (user1, user2) = signUsersUp()

            val post = createPostPostData(title = "post1")
            postService.addPost(user1, post)
            val postId = postService.getPost(Viewer.Registered(user1), testUser.login, "post1").id

            val comment1 = CommentDto.Create(postId = postId, avatar = "my avatar", text = "oh, hi Mark")
            val comment2 = CommentDto.Create(postId = postId, avatar = "my avatar", text = "hi")
            postService.addComment(user1, comment1)
            postService.addComment(user2, comment2)

            val comments = postService.getPost(Viewer.Registered(user1), testUser.login, "post1").comments
            assertEquals(2, comments.size)
            assertEquals(comment1.text, comments[0].text)
            assertEquals(comment1.avatar, comments[0].avatar)
            assertEquals(testUser.login, comments[0].authorLogin)
            assertEquals(testUser.nickname, comments[0].authorNickname)
            assertNow(comments[0].creationTime)

            assertEquals(comment2.text, comments[1].text)
            assertEquals(comment2.avatar, comments[1].avatar)
            assertEquals(testUser2.login, comments[1].authorLogin)
            assertEquals(testUser2.nickname, comments[1].authorNickname)
            assertNow(comments[1].creationTime)

            rollback()
        }
    }

    @Test
    fun `test comment post with friends-only commenting`() {
        transaction {
            // Set up users
            val users = signUsersUp(3)
            val (user1Id, user1Login) = users[0]
            val (user2Id, user2Login) = users[1]
            val (user3Id, _) = users[2]

            // Create a post with friends-only commenting
            val post = createPostPostData(
                uri = "friends-comment",
                title = "Friends only commenting",
                commentGroup = groupService.friendsGroupUUID
            )
            postService.addPost(user1Id, post)
            val postId = postService.getPost(Viewer.Registered(user1Id), user1Login, "friends-comment").id

            // Owner can always comment on their own post
            val ownerComment = CommentDto.Create(postId = postId, avatar = "avatar1", text = "Owner comment")
            postService.addComment(user1Id, ownerComment)

            // User2 tries to comment but fails (not friends yet)
            val user2Comment = CommentDto.Create(postId = postId, avatar = "avatar2", text = "User2 comment")
            assertThrows(WrongUserException::class.java) {
                postService.addComment(user2Id, user2Comment)
            }

            // User3 tries to comment but fails (not friends)
            val user3Comment = CommentDto.Create(postId = postId, avatar = "avatar3", text = "User3 comment")
            assertThrows(WrongUserException::class.java) {
                postService.addComment(user3Id, user3Comment)
            }

            // Make user1 and user2 friends
            userService.sendFriendRequest(user1Id, FriendRequestDto.Create(user2Login, "", null))
            val friendRequests = userService.getReceivedFriendRequests(user2Id)
            assertEquals(1, friendRequests.size)
            userService.acceptFriendRequest(user2Id, friendRequests.first().id, null)

            // Now user2 can comment
            postService.addComment(user2Id, user2Comment)

            // User3 still can't comment
            assertThrows(WrongUserException::class.java) {
                postService.addComment(user3Id, user3Comment)
            }

            // Verify comments
            val comments = postService.getPost(Viewer.Registered(user1Id), user1Login, "friends-comment").comments
            assertEquals(2, comments.size)
            assertEquals("Owner comment", comments[0].text)
            assertEquals("User2 comment", comments[1].text)

            rollback()
        }
    }

    @Test
    fun `test post with friends-only reacting access control`() {
        transaction {
            // Set up users
            val users = signUsersUp(3)
            val (user1Id, user1Login) = users[0]
            val (user2Id, user2Login) = users[1]
            val (user3Id, _) = users[2]

            // Create a post with friends-only reacting
            val post = createPostPostData(
                uri = "friends-react",
                title = "Friends only reacting",
                reactionGroup = groupService.friendsGroupUUID
            )
            postService.addPost(user1Id, post)
            val postView = postService.getPost(Viewer.Registered(user1Id), user1Login, "friends-react")

            // Verify the post was created with the friends-only reaction group
            val postEntity = PostEntity.findById(postView.id)
            assertNotNull(postEntity)
            assertEquals(groupService.friendsGroupUUID, postEntity!!.reactionGroupId.value)

            // Verify friendship affects access
            // Initially user2 is not friends with user1
            var user2InFriendsGroup = groupService.inGroup(Viewer.Registered(user2Id), groupService.friendsGroupUUID, user1Id)
            assertEquals(false, user2InFriendsGroup)

            // Make user1 and user2 friends
            userService.sendFriendRequest(user1Id, FriendRequestDto.Create(user2Login, "", null))
            val friendRequests = userService.getReceivedFriendRequests(user2Id)
            assertEquals(1, friendRequests.size)
            userService.acceptFriendRequest(user2Id, friendRequests.first().id, null)

            // Now user2 should be in user1's friends group
            user2InFriendsGroup = groupService.inGroup(Viewer.Registered(user2Id), groupService.friendsGroupUUID, user1Id)
            assertEquals(true, user2InFriendsGroup)

            val reactions = reactionService.getBasicReactions()
            assertTrue(reactions.isNotEmpty())
            // Now user2 can react
            reactionService.addReaction(Viewer.Registered(user2Id), user1Login, "friends-react", "fire")

            // User3 still can't react
            assertThrows(WrongUserException::class.java) {
                reactionService.addReaction(Viewer.Registered(user3Id), user1Login, "friends-react", "fire")
            }

            rollback()
        }
    }

    @Test
    fun `test get discussed posts`() {
        transaction {
            val (user1, user2) = signUsersUp()

            // Create three posts: one with recent comment, one with old comment, one without comments
            val post1 = createPostPostData(uri = "post1", title = "Post with recent comment")
            val post2 = createPostPostData(uri = "post2", title = "Post with old comment")
            val post3 = createPostPostData(uri = "post3", title = "Post without comments")

            postService.addPost(user1, post1)
            postService.addPost(user1, post2)
            postService.addPost(user1, post3)

            // Add comment to post2 (older comment)
            val comment1 = CommentDto.Create(
                postId = transaction { Posts.select { Posts.uri eq "post2" }.first()[Posts.id].value },
                avatar = "avatar1",
                text = "Old comment"
            )
            postService.addComment(user1, comment1)

            Thread.sleep(100) // Ensure time difference

            // Add comment to post1 (newer comment)
            val comment2 = CommentDto.Create(
                postId = transaction { Posts.select { Posts.uri eq "post1" }.first()[Posts.id].value },
                avatar = "avatar2",
                text = "Recent comment"
            )
            postService.addComment(user2, comment2)

            // Test sorting for registered user
            val discussedPosts = postService.getDiscussedPosts(Viewer.Registered(user1), Pageable(0, 10, SortOrder.DESC))
            assertEquals(3, discussedPosts.content.size)
            assertEquals("Post with recent comment", discussedPosts.content[0].title) // Most recent comment
            assertEquals("Post with old comment", discussedPosts.content[1].title) // Older comment
            assertEquals("Post without comments", discussedPosts.content[2].title) // No comments

            // Test sorting for anonymous user (should only see posts with EVERYONE access)
            val anonymousPosts = postService.getDiscussedPosts(
                Viewer.Anonymous("127.0.0.1", "test-fingerprint"),
                Pageable(0, 10, SortOrder.DESC)
            )
            assertEquals(3, anonymousPosts.content.size) // All posts are public by default
            assertEquals("Post with recent comment", anonymousPosts.content[0].title)
            assertEquals("Post with old comment", anonymousPosts.content[1].title)
            assertEquals("Post without comments", anonymousPosts.content[2].title)

            // Create a private post with a very recent comment
            val privatePost = createPostPostData(
                uri = "private",
                title = "Private post with very recent comment",
                readGroup = groupService.registeredGroupUUID
            )
            postService.addPost(user1, privatePost)
            val privateComment = CommentDto.Create(
                postId = transaction { Posts.select { Posts.uri eq "private" }.first()[Posts.id].value },
                avatar = "avatar3",
                text = "Very recent comment"
            )
            postService.addComment(user1, privateComment)

            // Verify registered user can see the private post
            val discussedPostsWithPrivate = postService.getDiscussedPosts(Viewer.Registered(user1), Pageable(0, 10, SortOrder.DESC))
            assertEquals(4, discussedPostsWithPrivate.content.size)
            assertEquals("Private post with very recent comment", discussedPostsWithPrivate.content[0].title)

            // Verify anonymous user cannot see the private post
            val anonymousPostsAfterPrivate = postService.getDiscussedPosts(
                Viewer.Anonymous("127.0.0.1", "test-fingerprint"),
                Pageable(0, 10, SortOrder.DESC)
            )
            assertEquals(3, anonymousPostsAfterPrivate.content.size)
            assertNotEquals("Private post with very recent comment", anonymousPostsAfterPrivate.content[0].title)

            rollback()
        }
    }

    // todo access groups
    // todo commenting
    // todo generating url when busy
    // todo generating url when russian


    private fun getPosts(viewer: Viewer, author: String? = null, diary: String? = null, pattern: String? = null, tags: Pair<TagPolicy, Set<String>>? = null, pageable: Pageable): Page<PostDto.View> {
        return postService.getPosts(viewer, author, diary, pattern, tags, null, null, pageable)
    }

    private fun getPosts(userId: UUID, author: String? = null, diary: String? = null, pattern: String? = null, tags: Pair<TagPolicy, Set<String>>? = null, pageable: Pageable): Page<PostDto.View> {
        return getPosts(Viewer.Registered(userId), author, diary, pattern, tags, pageable)
    }

    private fun createPostPostData(
        uri: String = "",
        avatar : String = "avatar url",
        title : String = "sample title",
        text : String = "sample text",
        isPreface : Boolean = false,
        isEncrypted: Boolean = false,
        classes : String = "bold",
        tags : Set<String> = emptySet(),
        readGroup: UUID = groupService.everyoneGroupUUID,
        commentGroup: UUID = groupService.everyoneGroupUUID,
        reactionGroup: UUID = groupService.everyoneGroupUUID,
        commentReactionGroup: UUID = reactionGroup,
    ): PostDto.Create {
        return PostDto.Create(
            uri  = uri,
            avatar  = avatar,
            title  = title,
            text  = text,
            isPreface  = isPreface,
            isEncrypted = isEncrypted,
            classes  = classes,
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
        avatar : String = "avatar url",
        title : String = "sample title",
        text : String = "sample text",
        isEncrypted: Boolean = false,
        classes : String = "bold",
        tags : Set<String> = emptySet(),
        readGroup: UUID = groupService.everyoneGroupUUID,
        commentGroup: UUID = groupService.everyoneGroupUUID,
        reactionGroup: UUID = groupService.everyoneGroupUUID,
        commentReactionGroup: UUID = reactionGroup,
    ): PostDto.Update {
        return PostDto.Update(
            id = id,
            uri  = uri,
            avatar  = avatar,
            title  = title,
            text  = text,
            isEncrypted = isEncrypted,
            classes  = classes,
            tags = tags,
            readGroupId = readGroup,
            commentGroupId = commentGroup,
            reactionGroupId = reactionGroup,
            commentReactionGroupId = commentReactionGroup,
        )
    }
}
