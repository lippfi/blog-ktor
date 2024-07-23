package fi.lipp.blog

import fi.lipp.blog.data.Post
import fi.lipp.blog.data.User
import fi.lipp.blog.domain.DiaryEntity
import fi.lipp.blog.model.Page
import fi.lipp.blog.model.Pageable
import fi.lipp.blog.model.TagPolicy
import fi.lipp.blog.model.exceptions.WrongUserException
import fi.lipp.blog.repository.*
import fi.lipp.blog.service.MailService
import fi.lipp.blog.service.implementations.PostServiceImpl
import fi.lipp.blog.service.implementations.StorageServiceImpl
import fi.lipp.blog.service.implementations.UserServiceImpl
import fi.lipp.blog.stubs.ApplicationPropertiesStub
import fi.lipp.blog.stubs.PasswordEncoderStub
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.toKotlinLocalDateTime
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.AfterClass
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
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
            Database.connect("jdbc:h2:mem:test;DB_CLOSE_DELAY=-1;", driver = "org.h2.Driver")
            transaction {
                SchemaUtils.create(Users, Diaries, InviteCodes, PasswordResets, Files, UserAvatars, Tags, Posts, PostTags)
            }
        }

        @JvmStatic
        @AfterClass
        fun tearDown() {
        }

        private val properties = ApplicationPropertiesStub()
        private val encoder = PasswordEncoderStub()
        private val mailService = mock<MailService>()
        private val storageService = StorageServiceImpl(properties)
        private val userService = UserServiceImpl(encoder, mailService, storageService)
        private val postService = PostServiceImpl()

        private val testUser = User(
            id = 2412412L,
            login = "barabaka",
            email = "barabaka@mail.com",
            password = "password123",
            nickname = "dog_lover37",
            registrationTime = LocalDateTime(2024, 4, 17, 1, 2)
        )
        private val testUser2 = User(
            id = 1751348L,
            login = "bigbabyboy",
            email = "piecelovingkebab@proton.com",
            password = "secure_password",
            nickname = "cat_hater44",
            registrationTime = LocalDateTime(2019, 4, 17, 1, 2)
        )
    }

    @Test
    fun `test adding post`() {
        transaction {
            val pageable = Pageable(1, 10, SortOrder.DESC)
            val (user1, _) = signUsersUp()
            var page = getPosts(user = user1, pageable = pageable)
            assertEquals(Page(emptyList(), 1, 0), page)

            val post1 = createPost(user1, title = "Hello World")
            postService.addPost(user1.id, post1)
            page = getPosts(user = user1, pageable = pageable)
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
            assertEquals(post1.isPrivate, addedPost.isPrivate)
            assertEquals(post1.isEncrypted, addedPost.isEncrypted)
            assertEquals(post1.authorLogin, user1.login)
            assertEquals(post1.authorNickname, user1.nickname)
            assertNow(addedPost.creationTime)

            rollback()
        }
    }

    @Test
    fun `test updating post`() {
        transaction {
            val pageable = Pageable(1, 10, SortOrder.DESC)
            val (user1, _) = signUsersUp()
            var page = getPosts(user = user1, pageable = pageable)
            assertEquals(Page(emptyList(), 1, 0), page)

            val post1 = createPost(user1, title = "Hello World")
            postService.addPost(user1.id, post1)
            page = getPosts(user = user1, pageable = pageable)
            val addedPost = page.content.first()

            val newPost = createPost(user1, id = addedPost.id, avatar = "new url", title = "new title", text = "new text", isPreface = !post1.isPreface, isPrivate = !post1.isPrivate, isEncrypted = !post1.isEncrypted, classes = "small", tags = setOf("cat", "dog"))
            postService.updatePost(user1.id, newPost)
            page = getPosts(user = user1, pageable = pageable)
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
            assertEquals(newPost.isPrivate, updatedPost.isPrivate)
            assertEquals(newPost.isEncrypted, updatedPost.isEncrypted)
            assertEquals(newPost.authorLogin, user1.login)
            assertEquals(newPost.authorNickname, user1.nickname)
            assertEquals(addedPost.creationTime, updatedPost.creationTime)

            rollback()
        }
    }

    @Test
    fun `test updating post url`() {
        transaction {
            val pageable = Pageable(1, 10, SortOrder.DESC)
            val (user1, _) = signUsersUp()
            var page = getPosts(user = user1, pageable = pageable)
            assertEquals(Page(emptyList(), 1, 0), page)

            val post1 = createPost(user1, title = "Hello World")
            postService.addPost(user1.id, post1)
            page = getPosts(user = user1, pageable = pageable)
            val addedPost = page.content.first()

            val newPost = createPost(user1, id = addedPost.id, avatar = "new url", uri = "new-uri", title = "new title", text = "new text", isPreface = !post1.isPreface, isPrivate = !post1.isPrivate, isEncrypted = !post1.isEncrypted, classes = "small", tags = setOf("cat", "dog"))
            postService.updatePost(user1.id, newPost)
            page = getPosts(user = user1, pageable = pageable)
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
            assertEquals(newPost.isPrivate, updatedPost.isPrivate)
            assertEquals(newPost.isEncrypted, updatedPost.isEncrypted)
            assertEquals(newPost.authorLogin, user1.login)
            assertEquals(newPost.authorNickname, user1.nickname)
            assertEquals(addedPost.creationTime, updatedPost.creationTime)

            rollback()
        }
    }

    @Test
    fun `test updating post of other user`() {
        transaction {
            val pageable = Pageable(1, 10, SortOrder.DESC)
            val (user1, user2) = signUsersUp()
            var page = getPosts(user = user1, pageable = pageable)
            assertEquals(Page(emptyList(), 1, 0), page)

            val post1 = createPost(user1, title = "Hello World")
            postService.addPost(user1.id, post1)
            page = getPosts(user = user1, pageable = pageable)
            val addedPost = page.content.first()

            val newPost = createPost(user1, id = addedPost.id, avatar = "new url", title = "new title", text = "new text", isPreface = !post1.isPreface, isPrivate = !post1.isPrivate, isEncrypted = !post1.isEncrypted, classes = "small", tags = setOf("cat", "dog"))
            assertThrows(WrongUserException::class.java) {
                postService.updatePost(user2.id, newPost)
            }

            page = getPosts(user = user1, pageable = pageable)
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
            var page = getPosts(user = user1, pageable = pageable)
            assertEquals(Page(emptyList(), 1, 0), page)

            val preface = createPost(user1, title = "Welcome to my blog", text = "preface text", isPreface = true, classes = "rounded", tags = setOf("info", "photos"))
            postService.addPost(user1.id, preface)
            page = getPosts(user = user1, pageable = pageable)
            assertEquals(0, page.totalPages)
            assertEquals(0, page.content.size)

            val diaryId = DiaryEntity.find { Diaries.owner eq user1.id }.first().id.value
            val foundPreface1 = postService.getPreface(user1.id, diaryId)!!
            val foundPreface2 = postService.getPreface(user2.id, diaryId)!!
            assertEquals(foundPreface1, foundPreface2)

            assertEquals("welcome-to-my-blog", foundPreface1.uri)
            assertEquals(preface.title, foundPreface1.title)
            assertEquals(preface.text, foundPreface1.text)
            assertEquals(preface.tags, foundPreface1.tags)
            assertEquals(preface.classes, foundPreface1.classes)
            assertEquals(preface.avatar, foundPreface1.avatar)
            assertEquals(preface.isPreface, foundPreface1.isPreface)
            assertEquals(preface.isPrivate, foundPreface1.isPrivate)
            assertEquals(preface.isEncrypted, foundPreface1.isEncrypted)
            assertEquals(user1.login, foundPreface1.authorLogin)
            assertEquals(user1.nickname, foundPreface1.authorNickname)
            assertNow(foundPreface1.creationTime)

            rollback()
        }
    }

    @Test
    fun `test add preface where there is existing preface`() {
        transaction {
            val (user1, _) = signUsersUp()
            val diaryId = DiaryEntity.find { Diaries.owner eq user1.id }.first().id.value

            val preface1 = createPost(user1, title = "Welcome to my blog", text = "preface text", isPreface = true, classes = "rounded", tags = setOf("info", "photos"))
            postService.addPost(user1.id, preface1)
            val foundPreface1 = postService.getPreface(user1.id, diaryId)!!

            val preface2 = createPost(user1, title = "Welcome to my blog2", text = "preface text2", isPreface = true, classes = "rounded2", tags = setOf("info2", "photos2"))
            postService.addPost(user1.id, preface2)
            val foundPreface2 = postService.getPreface(user1.id, diaryId)!!
            assertNotEquals(foundPreface1.id, foundPreface2.id)

            assertEquals("welcome-to-my-blog2", foundPreface2.uri)
            assertEquals(preface2.title, foundPreface2.title)
            assertEquals(preface2.text, foundPreface2.text)
            assertEquals(preface2.tags, foundPreface2.tags)
            assertEquals(preface2.classes, foundPreface2.classes)
            assertEquals(preface2.avatar, foundPreface2.avatar)
            assertEquals(preface2.isPreface, foundPreface2.isPreface)
            assertEquals(preface2.isPrivate, foundPreface2.isPrivate)
            assertEquals(preface2.isEncrypted, foundPreface2.isEncrypted)
            assertEquals(preface2.authorLogin, user1.login)
            assertEquals(preface2.authorNickname, user1.nickname)
            assertNow(foundPreface2.creationTime)

            rollback()
        }
    }

    @Test
    fun `test update preface`() {
        transaction {
            val pageable = Pageable(1, 10, SortOrder.DESC)
            val (user1, _) = signUsersUp()
            val diaryId = DiaryEntity.find { Diaries.owner eq user1.id }.first().id.value

            val preface = createPost(user1, title = "Welcome to my blog", text = "preface text", isPreface = true, classes = "rounded", tags = setOf("info", "photos"))
            postService.addPost(user1.id, preface)
            val foundPreface1 = postService.getPreface(user1.id, diaryId)!!

            val prefaceUpdate = createPost(user1, id = foundPreface1.id, title = "Welcome to my blog2", text = "preface text2", isPreface = true, classes = "rounded2", tags = setOf("info2", "photos2"))
            postService.updatePost(user1.id, prefaceUpdate)
            val updatedPreface = postService.getPreface(user1.id, diaryId)!!

            assertEquals(foundPreface1.id, updatedPreface.id)
            assertEquals("welcome-to-my-blog2", updatedPreface.uri)
            assertEquals(prefaceUpdate.title, updatedPreface.title)
            assertEquals(prefaceUpdate.text, updatedPreface.text)
            assertEquals(prefaceUpdate.tags, updatedPreface.tags)
            assertEquals(prefaceUpdate.classes, updatedPreface.classes)
            assertEquals(prefaceUpdate.avatar, updatedPreface.avatar)
            assertEquals(prefaceUpdate.isPreface, updatedPreface.isPreface)
            assertEquals(prefaceUpdate.isPrivate, updatedPreface.isPrivate)
            assertEquals(prefaceUpdate.isEncrypted, updatedPreface.isEncrypted)
            assertEquals(prefaceUpdate.authorLogin, user1.login)
            assertEquals(prefaceUpdate.authorNickname, user1.nickname)
            assertNow(updatedPreface.creationTime)

            val page = getPosts(user = user1, pageable = pageable)
            assertEquals(Page(emptyList(), 1, 0), page)

            rollback()
        }
    }

    @Test
    fun `test update preface of other user`() {
        transaction {
            val pageable = Pageable(1, 10, SortOrder.DESC)
            val (user1, user2) = signUsersUp()
            val diaryId = DiaryEntity.find { Diaries.owner eq user1.id }.first().id.value

            val preface = createPost(user1, title = "Welcome to my blog", text = "preface text", isPreface = true, classes = "rounded", tags = setOf("info", "photos"))
            postService.addPost(user1.id, preface)
            val foundPreface1 = postService.getPreface(user1.id, diaryId)!!

            val prefaceUpdate = createPost(user1, id = foundPreface1.id, title = "Welcome to my blog2", text = "preface text2", isPreface = true, classes = "rounded2", tags = setOf("info2", "photos2"))
            assertThrows(WrongUserException::class.java) {
                postService.updatePost(user2.id, prefaceUpdate)
            }
            val updatedPreface = postService.getPreface(user1.id, diaryId)!!

            assertEquals(foundPreface1.id, updatedPreface.id)
            assertEquals("welcome-to-my-blog", updatedPreface.uri)
            assertEquals(foundPreface1.title, updatedPreface.title)
            assertEquals(foundPreface1.text, updatedPreface.text)
            assertEquals(foundPreface1.tags, updatedPreface.tags)
            assertEquals(foundPreface1.classes, updatedPreface.classes)
            assertEquals(foundPreface1.avatar, updatedPreface.avatar)
            assertEquals(foundPreface1.isPreface, updatedPreface.isPreface)
            assertEquals(foundPreface1.isPrivate, updatedPreface.isPrivate)
            assertEquals(foundPreface1.isEncrypted, updatedPreface.isEncrypted)
            assertEquals(foundPreface1.authorLogin, user1.login)
            assertEquals(foundPreface1.authorNickname, user1.nickname)
            assertNow(updatedPreface.creationTime)

            val page = getPosts(user = user1, pageable = pageable)
            assertEquals(Page(emptyList(), 1, 0), page)

            rollback()
        }
    }

    @Test
    fun `test getting post by url`() {
        transaction {
            val (user1, user2) = signUsersUp()

            val post1 = createPost(user1, title = "Hello World")
            postService.addPost(user1.id, post1)
            val foundPost1 = postService.getPost(user1.id, user1.login, "hello-world")
            val foundPost2 = postService.getPost(user2.id, user1.login, "hello-world")
            assertNotNull(foundPost1)
            assertEquals(foundPost1, foundPost2)

            rollback()
        }
    }

    @Test
    fun `test getting private post by url`() {
        transaction {
            val (user1, user2) = signUsersUp()

            val post1 = createPost(user1, title = "Hello World", isPrivate = false)
            postService.addPost(user1.id, post1)
            val foundPost1 = postService.getPost(user1.id, user1.login, "hello-world")
            val foundPost2 = postService.getPost(user2.id, user1.login, "hello-world")
            assertNotNull(foundPost1)
            assertEquals(foundPost1, foundPost2)

            rollback()

        }
    }

    @Test
    fun `test private preface`() {
        transaction {
            val pageable = Pageable(1, 10, SortOrder.DESC)
            val (user1, user2) = signUsersUp()
            var page = getPosts(user = user1, pageable = pageable)
            assertEquals(Page(emptyList(), 1, 0), page)

            val preface = createPost(user1, title = "Welcome to my blog", text = "preface text", isPreface = true, isPrivate = true, classes = "rounded", tags = setOf("info", "photos"))
            postService.addPost(user1.id, preface)
            page = getPosts(user = user1, pageable = pageable)
            assertEquals(0, page.totalPages)
            assertEquals(0, page.content.size)

            val diaryId = DiaryEntity.find { Diaries.owner eq user1.id }.first().id.value
            val foundPreface1 = postService.getPreface(user1.id, diaryId)!!
            val foundPreface2 = postService.getPreface(user2.id, diaryId)
            assertNotNull(foundPreface1)
            assertNull(foundPreface2)

            rollback()
        }
    }

    @Test
    fun `test pagination`() {
        transaction {
            val (user1, _) = signUsersUp()

            val post1 = createPost(user1, title = "post1")
            postService.addPost(user1.id, post1)
            Thread.sleep(10)
            val post2 = createPost(user1, title = "post2")
            postService.addPost(user1.id, post2)
            Thread.sleep(10)
            val post3 = createPost(user1, title = "post3")
            postService.addPost(user1.id, post3)
            Thread.sleep(10)
            val post4 = createPost(user1, title = "post4")
            postService.addPost(user1.id, post4)
            Thread.sleep(10)
            val post5 = createPost(user1, title = "post5")
            postService.addPost(user1.id, post5)

            var page = getPosts(user = user1, pageable = Pageable(1, 4, SortOrder.DESC))
            assertEquals(1, page.currentPage)
            assertEquals(2, page.totalPages)
            assertEquals(listOf("post5", "post4", "post3", "post2"), page.content.map { it.title })


            page = getPosts(user = user1, pageable = Pageable(2, 4, SortOrder.DESC))
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

            val post1 = createPost(user1, title = "post1", tags = setOf("1", "2"))
            postService.addPost(user1.id, post1)
            Thread.sleep(10)
            val post2 = createPost(user1, title = "post2", tags = setOf("2", "3"))
            postService.addPost(user1.id, post2)
            Thread.sleep(10)
            val post3 = createPost(user1, title = "post3", tags = setOf("2", "3", "4"))
            postService.addPost(user1.id, post3)
            Thread.sleep(10)
            val post4 = createPost(user1, title = "post4", tags = setOf("3", "4"))
            postService.addPost(user1.id, post4)
            Thread.sleep(10)
            val post5 = createPost(user1, title = "post5", tags = setOf("4", "5"))
            postService.addPost(user1.id, post5)

            val page = getPosts(user = user1, tags = TagPolicy.UNION to setOf("2", "3"), pageable = Pageable(1, 4, SortOrder.DESC))
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

            val post1 = createPost(user1, title = "post1", tags = setOf("1", "2"))
            postService.addPost(user1.id, post1)
            Thread.sleep(10)
            val post2 = createPost(user1, title = "post2", tags = setOf("2", "3"))
            postService.addPost(user1.id, post2)
            Thread.sleep(10)
            val post3 = createPost(user1, title = "post3", tags = setOf("2", "3", "4"))
            postService.addPost(user1.id, post3)
            Thread.sleep(10)
            val post4 = createPost(user1, title = "post4", tags = setOf("3", "4"))
            postService.addPost(user1.id, post4)
            Thread.sleep(10)
            val post5 = createPost(user1, title = "post5", tags = setOf("4", "5"))
            postService.addPost(user1.id, post5)

            val page = getPosts(user = user1, tags = TagPolicy.INTERSECTION to setOf("2", "3"), pageable = Pageable(1, 4, SortOrder.DESC))
            assertEquals(1, page.currentPage)
            assertEquals(1, page.totalPages)
            assertEquals(listOf("post3", "post2"), page.content.map { it.title })

            rollback()
        }
    }

    @Test
    fun `test search by text`() {
        transaction {
            val (user1, _) = signUsersUp()

            val post1 = createPost(user1, title = "post1", text = "some text")
            postService.addPost(user1.id, post1)
            Thread.sleep(10)
            val post2 = createPost(user1, title = "post2", text = "some text")
            postService.addPost(user1.id, post2)
            Thread.sleep(10)
            val post3 = createPost(user1, title = "post3", text = "dog")
            postService.addPost(user1.id, post3)
            Thread.sleep(10)
            val post4 = createPost(user1, title = "post4", text = "funny dog")
            postService.addPost(user1.id, post4)
            Thread.sleep(10)
            val post5 = createPost(user1, title = "post5", text = "some text")
            postService.addPost(user1.id, post5)

            val page = getPosts(user = user1, pattern = "dog", pageable = Pageable(1, 4, SortOrder.DESC))
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

            val post1 = createPost(user1, title = "post1")
            postService.addPost(user1.id, post1)
            Thread.sleep(10)
            val post2 = createPost(user1, title = "post2")
            postService.addPost(user1.id, post2)
            Thread.sleep(10)
            val post3 = createPost(user1, title = "post3")
            postService.addPost(user1.id, post3)
            Thread.sleep(10)
            val post4 = createPost(user1, title = "post4")
            postService.addPost(user1.id, post4)
            Thread.sleep(10)
            val post5 = createPost(user1, title = "post5")
            postService.addPost(user1.id, post5)

            var page = getPosts(user = user1, author = user1, pageable = Pageable(1, 10, SortOrder.DESC))
            assertEquals(1, page.currentPage)
            assertEquals(1, page.totalPages)
            assertEquals(listOf("post5", "post4", "post3", "post2", "post1"), page.content.map { it.title })

            page = getPosts(user = user1, author = user2, pageable = Pageable(1, 10, SortOrder.DESC))
            assertEquals(1, page.currentPage)
            assertEquals(0, page.totalPages)
            assertEquals(emptyList(), page.content)

            page = getPosts(user = user2, author = user2, pageable = Pageable(1, 10, SortOrder.DESC))
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
            val diaryId1 = DiaryEntity.find { Diaries.owner eq user1.id }.first().id.value
            val diaryId2 = DiaryEntity.find { Diaries.owner eq user2.id }.first().id.value

            val post1 = createPost(user1, title = "post1")
            postService.addPost(user1.id, post1)
            Thread.sleep(10)
            val post2 = createPost(user1, title = "post2")
            postService.addPost(user1.id, post2)
            Thread.sleep(10)
            val post3 = createPost(user1, title = "post3")
            postService.addPost(user1.id, post3)
            Thread.sleep(10)
            val post4 = createPost(user1, title = "post4")
            postService.addPost(user1.id, post4)
            Thread.sleep(10)
            val post5 = createPost(user1, title = "post5")
            postService.addPost(user1.id, post5)

            var page = getPosts(user = user1, diaryId = diaryId1, pageable = Pageable(1, 10, SortOrder.DESC))
            assertEquals(1, page.currentPage)
            assertEquals(1, page.totalPages)
            assertEquals(listOf("post5", "post4", "post3", "post2", "post1"), page.content.map { it.title })

            page = getPosts(user = user1, diaryId = diaryId2, pageable = Pageable(1, 10, SortOrder.DESC))
            assertEquals(1, page.currentPage)
            assertEquals(0, page.totalPages)
            assertEquals(emptyList(), page.content)

            page = getPosts(user = user2, diaryId = diaryId2, pageable = Pageable(1, 10, SortOrder.DESC))
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

            val post1 = createPost(user1, title = "post1")
            postService.addPost(user1.id, post1)
            Thread.sleep(10)
            val post2 = createPost(user1, title = "post2")
            postService.addPost(user1.id, post2)
            Thread.sleep(10)
            val post3 = createPost(user1, title = "post3")
            postService.addPost(user1.id, post3)
            Thread.sleep(10)
            val post4 = createPost(user1, title = "post4")
            postService.addPost(user1.id, post4)
            Thread.sleep(10)
            val post5 = createPost(user1, title = "post5")
            postService.addPost(user1.id, post5)

            val foundPost3Page = getPosts(user = user1, pattern = "post3", pageable = Pageable(1, 4, SortOrder.DESC))
            assertEquals(1, foundPost3Page.content.size)
            val foundPost3 = foundPost3Page.content.first()

            postService.deletePost(user1.id, foundPost3.id!!)
            val page = getPosts(user = user1, pageable = Pageable(1, 10, SortOrder.DESC))
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

            val post1 = createPost(user1, title = "post1")
            postService.addPost(user1.id, post1)
            Thread.sleep(10)
            val post2 = createPost(user1, title = "post2")
            postService.addPost(user1.id, post2)
            Thread.sleep(10)
            val post3 = createPost(user1, title = "post3")
            postService.addPost(user1.id, post3)
            Thread.sleep(10)
            val post4 = createPost(user1, title = "post4")
            postService.addPost(user1.id, post4)
            Thread.sleep(10)
            val post5 = createPost(user1, title = "post5")
            postService.addPost(user1.id, post5)

            val foundPost3Page = getPosts(user = user1, pattern = "post3", pageable = Pageable(1, 4, SortOrder.DESC))
            assertEquals(1, foundPost3Page.content.size)
            val foundPost3 = foundPost3Page.content.first()

            assertThrows(WrongUserException::class.java) {
                postService.deletePost(user2.id, foundPost3.id!!)
            }
            val page = getPosts(user = user1, pageable = Pageable(1, 10, SortOrder.DESC))
            assertEquals(1, page.currentPage)
            assertEquals(1, page.totalPages)
            assertEquals(listOf("post5", "post4", "post3", "post2", "post1"), page.content.map { it.title })

            rollback()
        }
    }

    // todo generating url when busy
    // todo generating url when russian

    private fun signUsersUp(): Pair<User, User> {
        userService.signUp(testUser, "")
        val user1 = findUserByLogin(testUser.login)!!
        val inviteCode = userService.generateInviteCode(user1.id.value)

        userService.signUp(testUser2, inviteCode)
        val user2 = findUserByLogin(testUser2.login)!!
        return user1.toUser() to user2.toUser()
    }

    private fun getPosts(user: User, author: User? = null, diaryId: Long? = null, pattern: String? = null, tags: Pair<TagPolicy, Set<String>>? = null, pageable: Pageable): Page<Post> {
        return postService.getPosts(user.id, author?.id, diaryId, pattern, tags, null, null, pageable)
    }

    private fun createPost(
        user: User,
        id: UUID? = null,
        uri : String = "",
        avatar : String = "avatar url",
        title : String = "sample title",
        text : String = "sample text",
        creationTime : LocalDateTime = java.time.LocalDateTime.now().toKotlinLocalDateTime(),
        isPreface : Boolean = false,
        isPrivate : Boolean = false,
        isEncrypted: Boolean = false,
        classes : String = "bold",
        tags : Set<String> = emptySet(),
    ): Post {
        return Post(
            id = id,
            uri  = uri,

            avatar  = avatar,
            authorNickname  = user.nickname,
            authorLogin = user.login,

            title  = title,
            text  = text,
            creationTime  = creationTime,

            isPreface  = isPreface,
            isPrivate  = isPrivate,
            isEncrypted = isEncrypted,

            classes  = classes,
            tags = tags,
        )
    }
}