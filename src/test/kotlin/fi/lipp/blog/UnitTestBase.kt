package fi.lipp.blog

import fi.lipp.blog.data.Language
import fi.lipp.blog.data.UserDto
import fi.lipp.blog.data.FileUploadData
import fi.lipp.blog.domain.DiaryEntity
import fi.lipp.blog.domain.UserEntity
import fi.lipp.blog.repository.AccessGroups
import fi.lipp.blog.repository.AnonymousPostReactions
import fi.lipp.blog.repository.Comments
import fi.lipp.blog.repository.CustomGroupUsers
import fi.lipp.blog.repository.Diaries
import fi.lipp.blog.repository.Files
import fi.lipp.blog.repository.InviteCodes
import fi.lipp.blog.repository.PasswordResets
import fi.lipp.blog.repository.PostReactions
import fi.lipp.blog.repository.PostTags
import fi.lipp.blog.repository.Posts
import fi.lipp.blog.repository.Reactions
import fi.lipp.blog.repository.Tags
import fi.lipp.blog.repository.UserAvatars
import fi.lipp.blog.repository.UserUploads
import fi.lipp.blog.repository.Users
import fi.lipp.blog.service.*
import fi.lipp.blog.service.implementations.AccessGroupServiceImpl
import fi.lipp.blog.service.implementations.StorageServiceImpl
import fi.lipp.blog.service.implementations.UserServiceImpl
import fi.lipp.blog.stubs.ApplicationPropertiesStub
import fi.lipp.blog.stubs.PasswordEncoderStub
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.toJavaLocalDateTime
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import org.mockito.kotlin.*
import java.io.ByteArrayInputStream
import java.io.File
import kotlin.io.path.Path
import kotlin.test.assertTrue
import io.ktor.server.application.*
import io.ktor.server.config.*
import org.slf4j.Logger
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import org.junit.After
import org.junit.AfterClass
import org.junit.Before
import org.junit.BeforeClass

abstract class UnitTestBase {
    companion object {
        private val mockConfig = MapApplicationConfig().apply {
            put("jwt.issuer", "test-issuer")
            put("jwt.audience", "test-audience")
            put("jwt.realm", "test-realm")
            put("jwt.secret", "test-secret")
        }

        private val mockEnvironment = object : ApplicationEnvironment {
            override val config = mockConfig
            override val log = mock<Logger>()
            override val monitor = mock<ApplicationEvents>()
            override val classLoader = this::class.java.classLoader
            override val rootPath = ""
            override val developmentMode = false
            override val parentCoroutineContext: CoroutineContext = EmptyCoroutineContext
        }

        @JvmStatic
        @BeforeClass
        fun setUpClass() {
            Database.connect("jdbc:h2:mem:test;DB_CLOSE_DELAY=-1;", driver = "org.h2.Driver")
            transaction {
                SchemaUtils.create(
                    Users,
                    Files,
                    UserAvatars,
                    Tags,
                    Diaries,
                    AccessGroups,
                    CustomGroupUsers,
                    InviteCodes,
                    PasswordResets,
                    Posts,
                    PostTags,
                    Comments,
                    Reactions,
                    PostReactions,
                    AnonymousPostReactions,
                    UserUploads
                )
            }
            startKoin {
                modules(module {
                    single<ApplicationEnvironment> { mockEnvironment }
                    single<ApplicationProperties> { ApplicationPropertiesStub() }
                    single<PasswordEncoder> { PasswordEncoderStub() }
                    single<MailService> { mock() }
                    single<StorageService> { StorageServiceImpl(get()) }
                    single<AccessGroupService> { AccessGroupServiceImpl() }
                    single<UserService> { UserServiceImpl(get(), get(), get(), get()) }
                })
            }
            // Initialize default access groups
            val accessGroupService = org.koin.core.context.GlobalContext.get().get<AccessGroupService>()
            transaction {
                // Access these properties to trigger group creation
                accessGroupService.everyoneGroupUUID
                accessGroupService.registeredGroupUUID
                accessGroupService.privateGroupUUID
            }
        }

        @JvmStatic
        @AfterClass
        fun tearDownClass() {
            stopKoin()
        }

        @JvmStatic
        protected val properties get() = org.koin.core.context.GlobalContext.get().get<ApplicationProperties>()
        @JvmStatic
        protected val encoder get() = org.koin.core.context.GlobalContext.get().get<PasswordEncoder>()
        @JvmStatic
        protected val mailService get() = org.koin.core.context.GlobalContext.get().get<MailService>()
        @JvmStatic
        protected val storageService get() = org.koin.core.context.GlobalContext.get().get<StorageService>()
        @JvmStatic
        protected val groupService get() = org.koin.core.context.GlobalContext.get().get<AccessGroupService>()
        @JvmStatic
        protected val userService get() = org.koin.core.context.GlobalContext.get().get<UserService>()

        @JvmStatic
        protected val testUser = UserDto.Registration(
            login = "barabaka",
            email = "barabaka@mail.com",
            password = "password123",
            nickname = "dog_lover37",
            language = Language.EN,
            timezone = "Europe/Moscow",
        )
        @JvmStatic
        protected val testUser2 = UserDto.Registration(
            login = "bigbabyboy",
            email = "piecelovingkebab@proton.com",
            password = "secure_password",
            nickname = "cat_hater44",
            language = Language.EN,
            timezone = "Europe/Moscow",
        )

        @JvmStatic
        protected val testAvatarsDirectory = Path("src", "test", "resources", "avatars")
        @JvmStatic
        protected val avatarFile1 = File(testAvatarsDirectory.resolve("1.png").toString())
        @JvmStatic
        protected val avatarFile2 = File(testAvatarsDirectory.resolve("2.png").toString())
        @JvmStatic
        protected val avatarFile3 = File(testAvatarsDirectory.resolve("3.gif").toString())
        @JvmStatic
        protected val avatarFileTxt = File(testAvatarsDirectory.resolve("6.txt").toString())
    }

    protected fun findUserByLogin(login: String): UserDto.FullProfileInfo? {
        return transaction {
            val diaryEntity = DiaryEntity.find { Diaries.login eq login }.firstOrNull() ?: return@transaction null
            val userEntity = UserEntity.findById(diaryEntity.owner.value) ?: return@transaction null
            UserDto.FullProfileInfo(
                id = userEntity.id.value,
                login = diaryEntity.login,
                email = userEntity.email,
                nickname = userEntity.nickname,
                registrationTime = userEntity.registrationTime,
                password = userEntity.password,
            )
        }
    }

    protected fun assertNow(dateTime: LocalDateTime) {
        val javaDateTime = dateTime.toJavaLocalDateTime()
        assertTrue(javaDateTime.isAfter(java.time.LocalDateTime.now().minusSeconds(20)))
        assertTrue(javaDateTime.isBefore(java.time.LocalDateTime.now()))
    }

    @Before
    fun cleanDatabase() {
        transaction {
            // Delete all data except access groups in the correct order to handle dependencies
            exec("DELETE FROM ${AnonymousPostReactions.tableName}")
            exec("DELETE FROM ${PostReactions.tableName}")
            exec("DELETE FROM ${Reactions.tableName}")
            exec("DELETE FROM ${Comments.tableName}")
            exec("DELETE FROM ${PostTags.tableName}")
            exec("DELETE FROM ${Posts.tableName}")
            exec("DELETE FROM ${PasswordResets.tableName}")
            exec("DELETE FROM ${InviteCodes.tableName}")
            exec("DELETE FROM ${CustomGroupUsers.tableName}")
            exec("DELETE FROM ${UserAvatars.tableName}")
            exec("DELETE FROM ${Files.tableName}")
            exec("DELETE FROM ${Diaries.tableName}")
            exec("DELETE FROM ${Users.tableName}")
            // Don't delete AccessGroups as they are required for the system to work
            commit()
        }
    }

    @After
    fun cleanStoredFiles() {
        transaction {
            val testFile = FileUploadData(
                fullName = "test.png",
                inputStream = ByteArrayInputStream(ByteArray(10))
            )
            val user = findUserByLogin(testUser.login)
            if (user != null) {
                val storedFile = storageService.store(user.id, listOf(testFile))[0]
                val file = storageService.getFile(storedFile)
                val userDir = file.parentFile.parentFile // Go up to user's directory
                if (userDir.exists()) {
                    userDir.walkBottomUp().forEach<File> { f: File ->
                        f.delete()
                    }
                }
            }
        }
    }
}
