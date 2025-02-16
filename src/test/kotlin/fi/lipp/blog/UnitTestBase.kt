package fi.lipp.blog

import fi.lipp.blog.data.Language
import fi.lipp.blog.data.UserDto
import fi.lipp.blog.domain.DiaryEntity
import fi.lipp.blog.domain.UserEntity
import fi.lipp.blog.repository.*
import fi.lipp.blog.service.MailService
import fi.lipp.blog.service.implementations.AccessGroupServiceImpl
import fi.lipp.blog.service.implementations.StorageServiceImpl
import fi.lipp.blog.service.implementations.UserServiceImpl
import fi.lipp.blog.stubs.ApplicationPropertiesStub
import fi.lipp.blog.stubs.PasswordEncoderStub
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.toJavaLocalDateTime
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import org.mockito.Mockito.mock
import java.io.File
import kotlin.io.path.Path
import kotlin.test.assertTrue

abstract class UnitTestBase {
    companion object {
        init {
            Database.connect("jdbc:h2:mem:test;DB_CLOSE_DELAY=-1;", driver = "org.h2.Driver")
            transaction {
                SchemaUtils.create(
                    Users, Diaries, InviteCodes, PasswordResets, Files, UserAvatars, Tags, Posts,
                    PostDislikes, AnonymousPostDislikes, PostTags, AccessGroups, CustomGroupUsers, Comments,
                    Reactions, ReactionLocalizations, PostReactions, AnonymousPostReactions
                )
            }
        }

        @JvmStatic
        protected val properties = ApplicationPropertiesStub()
        @JvmStatic
        protected val encoder = PasswordEncoderStub()
        @JvmStatic
        protected val mailService = mock<MailService>()
        @JvmStatic
        protected val storageService = StorageServiceImpl(properties)
        @JvmStatic
        protected val groupService = AccessGroupServiceImpl()
        @JvmStatic
        protected val userService = UserServiceImpl(encoder, mailService, storageService, groupService)

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
}
