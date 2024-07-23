package fi.lipp.blog

import fi.lipp.blog.data.User
import fi.lipp.blog.domain.UserEntity
import fi.lipp.blog.repository.Users
import fi.lipp.blog.service.MailService
import fi.lipp.blog.service.implementations.StorageServiceImpl
import fi.lipp.blog.service.implementations.UserServiceImpl
import fi.lipp.blog.stubs.ApplicationPropertiesStub
import fi.lipp.blog.stubs.PasswordEncoderStub
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.toJavaLocalDateTime
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.mockito.Mockito.mock
import java.io.File
import kotlin.io.path.Path
import kotlin.test.assertTrue

abstract class UnitTestBase {
    companion object {
        @JvmStatic
        protected val properties = ApplicationPropertiesStub()
        @JvmStatic
        protected val encoder = PasswordEncoderStub()
        @JvmStatic
        protected val mailService = mock<MailService>()
        @JvmStatic
        protected val storageService = StorageServiceImpl(properties)
        @JvmStatic
        protected val userService = UserServiceImpl(encoder, mailService, storageService)

        @JvmStatic
        protected val testUser = User(
            id = 2412412L,
            login = "barabaka",
            email = "barabaka@mail.com",
            password = "password123",
            nickname = "dog_lover37",
            registrationTime = LocalDateTime(2024, 4, 17, 1, 2)
        )
        @JvmStatic
        protected val testUser2 = User(
            id = 1751348L,
            login = "bigbabyboy",
            email = "piecelovingkebab@proton.com",
            password = "secure_password",
            nickname = "cat_hater44",
            registrationTime = LocalDateTime(2019, 4, 17, 1, 2)
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

    protected fun findUserByLogin(login: String): UserEntity? {
        return UserEntity.find { Users.login eq login }.firstOrNull()
    }

    protected fun assertNow(dateTime: LocalDateTime) {
        val javaDateTime = dateTime.toJavaLocalDateTime()
        assertTrue(javaDateTime.isAfter(java.time.LocalDateTime.now().minusSeconds(20)))
        assertTrue(javaDateTime.isBefore(java.time.LocalDateTime.now()))
    }
}