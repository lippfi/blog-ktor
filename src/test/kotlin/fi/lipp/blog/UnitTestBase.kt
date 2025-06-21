package fi.lipp.blog

import fi.lipp.blog.data.Language
import fi.lipp.blog.data.UserDto
import fi.lipp.blog.data.FileUploadData
import java.util.UUID
import fi.lipp.blog.domain.DiaryEntity
import fi.lipp.blog.domain.PendingRegistrationEntity
import fi.lipp.blog.domain.UserEntity
import fi.lipp.blog.domain.Notifications
import fi.lipp.blog.repository.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import fi.lipp.blog.service.*
import fi.lipp.blog.service.implementations.AccessGroupServiceImpl
import fi.lipp.blog.service.implementations.DialogServiceImpl
import fi.lipp.blog.service.implementations.StorageServiceImpl
import fi.lipp.blog.service.implementations.UserServiceImpl
import fi.lipp.blog.service.implementations.ReactionServiceImpl
import fi.lipp.blog.service.implementations.ReactionDatabaseSeeder
import fi.lipp.blog.service.implementations.DatabaseInitializer
import fi.lipp.blog.service.implementations.PostServiceImpl
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
            Database.connect(
                url = "jdbc:h2:mem:test;DB_CLOSE_DELAY=-1;",
                user = "root",
                driver = "org.h2.Driver",
                password = ""
            )
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
                    PendingRegistrations,
                    PendingEmailChanges,
                    Posts,
                    PostTags,
                    Comments,
                    Reactions,
                    PostReactions,
                    AnonymousPostReactions,
                    UserUploads,
                    FriendRequests,
                    Friends,
                    FriendLabels,
                    Notifications,
                    Dialogs,
                    Messages,
                    HiddenDialogs,
                    UserFollows,
                    CommentReactions,
                    NotificationSettings,
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
                    single<NotificationService> { mock() }
                    single<CommentWebSocketService> { mock() }
                    single<UserService> { UserServiceImpl(get(), get(), get(), get(), get()) }

                    // Database seeders
                    single { ReactionDatabaseSeeder(get(), get()) }

                    // Database initializer
                    single { DatabaseInitializer(listOf(get<ReactionDatabaseSeeder>())) }

                    // Services that depend on seeders
                    single<ReactionService> { ReactionServiceImpl(
                        storageService = get(),
                        accessGroupService = get(),
                        notificationService = get(),
                        userService = get(),
                        reactionDatabaseSeeder = get(),
                        commentWebSocketService = get()
                    ) }
                    single<PostService> { PostServiceImpl(
                        accessGroupService = get(),
                        storageService = get(),
                        reactionService = get(),
                        notificationService = get(),
                        commentWebSocketService = get()
                    ) }
                    single<DialogService> { DialogServiceImpl(get(), get()) }
                })
            }
            // Initialize default access groups
            val accessGroupService = org.koin.core.context.GlobalContext.get().get<AccessGroupService>()
            transaction {
                // Access these properties to trigger group creation
                accessGroupService.everyoneGroupUUID
                accessGroupService.registeredGroupUUID
                accessGroupService.privateGroupUUID
                accessGroupService.friendsGroupUUID

                // Initialize the database with seeders
                val databaseInitializer = org.koin.core.context.GlobalContext.get().get<DatabaseInitializer>()
                databaseInitializer.initialize()
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
        protected val reactionService get() = org.koin.core.context.GlobalContext.get().get<ReactionService>()
        @JvmStatic
        protected val postService get() = org.koin.core.context.GlobalContext.get().get<PostService>()

        @JvmStatic
        protected val notificationService get() = org.koin.core.context.GlobalContext.get().get<NotificationService>()

        @JvmStatic
        protected val dialogService get() = org.koin.core.context.GlobalContext.get().get<DialogService>()

        @JvmStatic
        protected val commentWebSocketService get() = org.koin.core.context.GlobalContext.get().get<CommentWebSocketService>()

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

        // Re-initialize the database with seeders
        val databaseInitializer = org.koin.core.context.GlobalContext.get().get<DatabaseInitializer>()
        databaseInitializer.initialize()
    }

    @After
    fun cleanStoredFiles() {
        // Delete all files in the base path
        val basePath = (properties as ApplicationPropertiesStub).basePath.toFile()
        if (basePath.exists()) {
            basePath.listFiles()?.forEach { dir ->
                if (dir.isDirectory) {
                    dir.walkBottomUp().forEach { file ->
                        file.delete()
                    }
                }
            }
        }
    }

    protected fun signUsersUp(): Pair<UUID, UUID> {
        // Get or create system user to generate invite codes
        val systemUserId = userService.getOrCreateSystemUser()
        val inviteCode = userService.generateInviteCode(systemUserId)

        // Sign up first user with invite code
        userService.signUp(testUser, inviteCode)

        // Get confirmation code for first user
        val pendingRegistration1 = transaction {
            PendingRegistrationEntity.find { 
                (PendingRegistrations.email eq testUser.email)
            }.first()
        }
        val confirmationCode1 = pendingRegistration1.id.value.toString()

        // Confirm registration for first user
        userService.confirmRegistration(confirmationCode1)
        val user1 = findUserByLogin(testUser.login)!!

        val nextInviteCode = userService.generateInviteCode(user1.id)

        // Sign up second user with invite code
        userService.signUp(testUser2, nextInviteCode)

        // Get confirmation code for second user
        val pendingRegistration2 = transaction {
            PendingRegistrationEntity.find { 
                (PendingRegistrations.email eq testUser2.email)
            }.first()
        }
        val confirmationCode2 = pendingRegistration2.id.value.toString()

        // Confirm registration for second user
        userService.confirmRegistration(confirmationCode2)
        val user2 = findUserByLogin(testUser2.login)!!

        return user1.id to user2.id
    }

    @Suppress("SameParameterValue")
    protected fun signUsersUp(count: Int): List<Pair<UUID, String>> {
        val users = mutableListOf<Pair<UUID, String>>()

        // Get or create system user to generate invite codes
        val systemUserId = userService.getOrCreateSystemUser()
        val inviteCode = userService.generateInviteCode(systemUserId)

        // Sign up first user with invite code
        userService.signUp(testUser, inviteCode)

        // Get confirmation code for first user
        val pendingRegistration1 = transaction {
            PendingRegistrationEntity.find { 
                (PendingRegistrations.email eq testUser.email)
            }.first()
        }
        val confirmationCode1 = pendingRegistration1.id.value.toString()

        // Confirm registration for first user
        userService.confirmRegistration(confirmationCode1)
        var userEntity = findUserByLogin(testUser.login)!!
        users.add(userEntity.id to testUser.login)

        var i = count - 1
        while (i > 0) {
            val nextInviteCode = userService.generateInviteCode(userEntity.id)
            val randomUser = UserDto.Registration(login = UUID.randomUUID().toString(), email = "${UUID.randomUUID()}@mail.com", password = "123", nickname = UUID.randomUUID().toString(), language = Language.KK, timezone = "Asia/Qostanay")
            userService.signUp(randomUser, nextInviteCode)

            // Get confirmation code for random user
            val pendingRegistration = transaction {
                PendingRegistrationEntity.find { 
                    (PendingRegistrations.email eq randomUser.email)
                }.first()
            }
            val confirmationCode = pendingRegistration.id.value.toString()

            // Confirm registration for random user
            userService.confirmRegistration(confirmationCode)
            userEntity = findUserByLogin(randomUser.login)!!
            users.add(userEntity.id to randomUser.login)
            --i
        }
        return users
    }
}
