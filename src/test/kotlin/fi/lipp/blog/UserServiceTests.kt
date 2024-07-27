package fi.lipp.blog

import fi.lipp.blog.data.FileUploadData
import fi.lipp.blog.data.User
import fi.lipp.blog.domain.PasswordResetCodeEntity
import fi.lipp.blog.domain.UserEntity
import fi.lipp.blog.model.exceptions.*
import fi.lipp.blog.repository.*
import kotlinx.datetime.LocalDateTime
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.AfterClass
import org.junit.Assert.assertThrows
import org.junit.BeforeClass
import org.mockito.Mockito.verify
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import java.io.File
import java.util.*
import kotlin.test.*

class UserServiceTests : UnitTestBase() {
    companion object {
        @JvmStatic
        @BeforeClass
        fun setUp() {
            Database.connect("jdbc:h2:mem:test;DB_CLOSE_DELAY=-1;", driver = "org.h2.Driver")
            transaction {
                SchemaUtils.create(Users, Diaries, InviteCodes, PasswordResets, Files, UserAvatars)
            }
            File(properties.imagesDirectory.toString()).mkdirs()
        }

        @JvmStatic
        @AfterClass
        fun tearDown() {
            File(properties.basePath.toString()).deleteRecursively()
        }
    }

    @Test
    fun isLoginBusy() {
        transaction {
            assertFalse(userService.isLoginBusy(testUser.login))
            userService.signUp(testUser, "")
            assertTrue(userService.isLoginBusy(testUser.login))
            rollback()
        }
    }

    @Test
    fun isEmailBusy() {
        transaction {
            assertFalse(userService.isEmailBusy(testUser.email))
            userService.signUp(testUser, "")
            assertTrue(userService.isEmailBusy(testUser.email))
            rollback()
        }
    }

    @Test
    fun isNicknameBusy() {
        transaction {
            assertFalse(userService.isNicknameBusy(testUser.nickname))
            userService.signUp(testUser, "")
            assertTrue(userService.isNicknameBusy(testUser.nickname))
            rollback()
        }
    }

    @Test
    fun `successful registration`() {
        transaction {
            var foundUser = findUserByLogin(testUser.login)
            assertNull(foundUser)

            userService.signUp(testUser, "")
            foundUser = findUserByLogin(testUser.login)
            assertNotNull(foundUser)

            assertEquals(testUser.login, foundUser.login)
            assertEquals(testUser.email, foundUser.email)
            assertEquals(testUser.nickname, foundUser.nickname)
            assertTrue(encoder.matches(testUser.password, foundUser.password))
            assertNow(foundUser.registrationTime)

            rollback()
        }
    }

    @Test
    fun `registration with busy login`() {
        transaction {
            var foundUser = findUserByLogin(testUser.login)
            assertNull(foundUser)

            userService.signUp(testUser, "")
            foundUser = findUserByLogin(testUser.login)
            assertNotNull(foundUser)

            val inviteCode = userService.generateInviteCode(foundUser.id.value)
            assertThrows(LoginIsBusyException::class.java) {
                userService.signUp(
                    User(0L, testUser.login, "new" + testUser.email, testUser.password, "new" + testUser.nickname, testUser.registrationTime),
                    inviteCode
                )
            }

            rollback()
        }
    }

    @Test
    fun `registration with busy email`() {
        transaction {
            var foundUser = findUserByLogin(testUser.login)
            assertNull(foundUser)

            userService.signUp(testUser, "")
            foundUser = findUserByLogin(testUser.login)
            assertNotNull(foundUser)

            val inviteCode = userService.generateInviteCode(foundUser.id.value)
            assertThrows(EmailIsBusyException::class.java) {
                userService.signUp(
                    User(0L, "new" + testUser.login, testUser.email, testUser.password, "new" + testUser.nickname, testUser.registrationTime),
                    inviteCode
                )
            }

            rollback()
        }
    }

    @Test
    fun `registration with busy nickname`() {
        transaction {
            var foundUser = findUserByLogin(testUser.login)
            assertNull(foundUser)

            userService.signUp(testUser, "")
            foundUser = findUserByLogin(testUser.login)
            assertNotNull(foundUser)

            val inviteCode = userService.generateInviteCode(foundUser.id.value)
            assertThrows(NicknameIsBusyException::class.java) {
                userService.signUp(
                    User(0L, "new" + testUser.login, "new" + testUser.email, testUser.password, testUser.nickname, testUser.registrationTime),
                    inviteCode
                )
            }

            rollback()
        }
    }

    @Test
    fun `successful sign in does not throw exception`() {
        transaction {
            userService.signUp(testUser, "")
            userService.signIn(testUser)
            rollback()
        }
    }

    @Test
    fun `sign in with wrong password`() {
        transaction {
            userService.signUp(testUser, "")
            assertThrows(WrongPasswordException::class.java) {
                userService.signIn(User(0L, testUser.login, "", "wrong" + testUser.password, "", testUser.registrationTime))
            }
            rollback()
        }
    }

    @Test
    fun `sign in with nonexistent login`() {
        transaction {
            userService.signUp(testUser, "")
            assertThrows(UserNotFoundException::class.java) {
                userService.signIn(User(0L, "unknown" + testUser.login, "", testUser.password, "", testUser.registrationTime))
            }
            rollback()
        }
    }

    @Test
    fun `updating user info`() {
        transaction {
            userService.signUp(testUser, "")

            val userEntity = findUserByLogin(testUser.login)!!
            val registrationTime = userEntity.registrationTime
            val newUser = User(12L, "new" + testUser.login, "new" + testUser.email, "new" + testUser.password, "new" + testUser.nickname, LocalDateTime(2000, 1, 1, 1, 1, 1))
            userService.update(userEntity.id.value, newUser, testUser.password)

            val updatedUser = UserEntity.findById(userEntity.id)!!
            assertEquals(newUser.login, updatedUser.login)
            assertEquals(newUser.email, updatedUser.email)
            assertEquals(newUser.nickname, updatedUser.nickname)
            assertTrue(encoder.matches(newUser.password, updatedUser.password))
            assertEquals(registrationTime, updatedUser.registrationTime)

            rollback()
        }
    }

    @Test
    fun `updating only login`() {
        transaction {
            userService.signUp(testUser, "")

            val userEntity = findUserByLogin(testUser.login)!!
            val registrationTime = userEntity.registrationTime
            val newUser = User(12L, "new" + testUser.login, testUser.email, testUser.password, testUser.nickname, testUser.registrationTime)
            userService.update(userEntity.id.value, newUser, testUser.password)

            val updatedUser = UserEntity.findById(userEntity.id)!!
            assertEquals(newUser.login, updatedUser.login)
            assertEquals(newUser.email, updatedUser.email)
            assertEquals(newUser.nickname, updatedUser.nickname)
            assertTrue(encoder.matches(newUser.password, updatedUser.password))
            assertEquals(registrationTime, updatedUser.registrationTime)

            rollback()
        }
    }

    @Test
    fun `updating only email`() {
        transaction {
            userService.signUp(testUser, "")

            val userEntity = findUserByLogin(testUser.login)!!
            val registrationTime = userEntity.registrationTime
            val newUser = User(12L, testUser.login, "new" + testUser.email, testUser.password, testUser.nickname, testUser.registrationTime)
            userService.update(userEntity.id.value, newUser, testUser.password)

            val updatedUser = UserEntity.findById(userEntity.id)!!
            assertEquals(newUser.login, updatedUser.login)
            assertEquals(newUser.email, updatedUser.email)
            assertEquals(newUser.nickname, updatedUser.nickname)
            assertTrue(encoder.matches(newUser.password, updatedUser.password))
            assertEquals(registrationTime, updatedUser.registrationTime)

            rollback()
        }
    }

    @Test
    fun `updating only nickname`() {
        transaction {
            userService.signUp(testUser, "")

            val userEntity = findUserByLogin(testUser.login)!!
            val registrationTime = userEntity.registrationTime
            val newUser = User(12L, testUser.login, testUser.email, testUser.password, "new" + testUser.nickname, testUser.registrationTime)
            userService.update(userEntity.id.value, newUser, testUser.password)

            val updatedUser = UserEntity.findById(userEntity.id)!!
            assertEquals(newUser.login, updatedUser.login)
            assertEquals(newUser.email, updatedUser.email)
            assertEquals(newUser.nickname, updatedUser.nickname)
            assertTrue(encoder.matches(newUser.password, updatedUser.password))
            assertEquals(registrationTime, updatedUser.registrationTime)

            rollback()
        }
    }

    @Test
    fun `updating user with wrong password`() {
        transaction {
            userService.signUp(testUser, "")

            val userEntity = findUserByLogin(testUser.login)!!
            val registrationTime = userEntity.registrationTime
            val newUser = User(12L, testUser.login, testUser.email, testUser.password, "new" + testUser.nickname, testUser.registrationTime)
            assertThrows(WrongPasswordException::class.java) {
                userService.update(userEntity.id.value, newUser, "wrong" + testUser.password)
            }

            val updatedUser = UserEntity.findById(userEntity.id)!!
            assertEquals(testUser.login, updatedUser.login)
            assertEquals(testUser.email, updatedUser.email)
            assertEquals(testUser.nickname, updatedUser.nickname)
            assertTrue(encoder.matches(testUser.password, updatedUser.password))
            assertEquals(registrationTime, updatedUser.registrationTime)

            rollback()
        }
    }

    @Test
    fun `updating user with busy login`() {
        transaction {
            userService.signUp(testUser, "")
            val foundUser = findUserByLogin(testUser.login)!!
            val inviteCode = userService.generateInviteCode(foundUser.id.value)

            userService.signUp(testUser2, inviteCode)

            val userEntity = findUserByLogin(testUser.login)!!
            val updatedUser = User(12L, testUser2.login, testUser.email, testUser.password, testUser.nickname, testUser.registrationTime)
            assertThrows(LoginIsBusyException::class.java) {
                userService.update(userEntity.id.value, updatedUser, testUser.password)
            }

            rollback()
        }
    }

    @Test
    fun `updating user with busy email`() {
        transaction {
            userService.signUp(testUser, "")
            val foundUser = findUserByLogin(testUser.login)!!
            val inviteCode = userService.generateInviteCode(foundUser.id.value)
            userService.signUp(testUser2, inviteCode)

            val userEntity = findUserByLogin(testUser.login)!!
            val updatedUser = User(12L, testUser.login, testUser2.email, testUser.password, testUser.nickname, testUser.registrationTime)
            assertThrows(EmailIsBusyException::class.java) {
                userService.update(userEntity.id.value, updatedUser, testUser.password)
            }

            rollback()
        }
    }

    @Test
    fun `updating user with busy nickname`() {
        transaction {
            userService.signUp(testUser, "")
            val foundUser = findUserByLogin(testUser.login)!!
            val inviteCode = userService.generateInviteCode(foundUser.id.value)
            userService.signUp(testUser2, inviteCode)

            val userEntity = findUserByLogin(testUser.login)!!
            val updatedUser = User(12L, testUser.login, testUser.email, testUser.password, testUser2.nickname, testUser.registrationTime)
            assertThrows(NicknameIsBusyException::class.java) {
                userService.update(userEntity.id.value, updatedUser, testUser.password)
            }

            rollback()
        }
    }

    @Test
    fun `password reset test`() {
        transaction {
            val stringCaptor = argumentCaptor<String>()

            userService.signUp(testUser, "")
            val foundUser = findUserByLogin(testUser.login)!!
            userService.sendPasswordResetEmail(foundUser.email)

            verify(mailService).sendEmail(eq("Password Reset"), stringCaptor.capture(), eq(testUser.email))

            val pattern = "Reset code: (\\S+)".toRegex()
            val resetCode = pattern.find(stringCaptor.lastValue)?.groups?.get(1)?.value!!

            val passwordResetCodeEntity = PasswordResetCodeEntity.findById(UUID.fromString(resetCode))!!
            assertEquals(foundUser.id.value, passwordResetCodeEntity.userId.value)
            assertNow(passwordResetCodeEntity.resetIssuedAt)

            val newPassword  = "newPassword"
            userService.performPasswordReset(resetCode, newPassword)
            verify(mailService).sendEmail(
                eq("Password Change Notification"),
                eq("""
                Your password has been successfully changed. If you did not initiate this change, please ensure that your email access is secure and that no unauthorized parties can access your account. It is also recommended that you try to reset your password again immediately.

                If you requested this change, no further action is needed. For your security, please do not share your password with anyone.
                """.trimMargin()),
                eq(testUser.email)
            )
            val encodedNewPassword = UserEntity.findById(foundUser.id)!!.password
            assertTrue(encoder.matches(newPassword, encodedNewPassword))

            rollback()
        }
    }

    @Test
    fun `add avatar test`() {
        transaction {
            val avatarUpload1 = FileUploadData(avatarFile1.name, avatarFile1.inputStream())
            val avatarUpload2 = FileUploadData(avatarFile2.name, avatarFile2.inputStream())
            val avatarUpload3 = FileUploadData(avatarFile3.name, avatarFile3.inputStream())

            userService.signUp(testUser, "")
            val foundUser = findUserByLogin(testUser.login)!!
            val userId = foundUser.id.value

            userService.addAvatar(userId, listOf(avatarUpload1))
            var avatars = userService.getAvatars(userId)
            assertEquals(1, avatars.size)
            val avatar1 = avatars.last()
            assertEquals(userId, avatar1.ownerId)
            assertEquals(avatarUpload1.extension, avatar1.extension)
            assertEquals(avatarUpload1.type, avatar1.type)

            userService.addAvatar(userId, listOf(avatarUpload2))
            avatars = userService.getAvatars(userId)
            assertEquals(2, avatars.size)
            val avatar2 = avatars.last()
            assertEquals(userId, avatar2.ownerId)
            assertEquals(avatarUpload2.extension, avatar2.extension)
            assertEquals(avatarUpload2.type, avatar2.type)

            userService.addAvatar(userId, listOf(avatarUpload3))
            avatars = userService.getAvatars(userId)
            assertEquals(3, avatars.size)
            val avatar3 = avatars.last()
            assertEquals(userId, avatar3.ownerId)
            assertEquals(avatarUpload3.extension, avatar3.extension)
            assertEquals(avatarUpload3.type, avatar3.type)

            assertEquals(avatar1, avatars[0])
            assertEquals(avatar2, avatars[1])
            assertEquals(avatar3, avatars[2])

            rollback()
        }
    }

    @Test
    fun `adding avatar with wrong extension`() {
        transaction {
            val avatarUploadTxt = FileUploadData(avatarFileTxt.name, avatarFileTxt.inputStream())

            userService.signUp(testUser, "")
            val foundUser = findUserByLogin(testUser.login)!!
            val userId = foundUser.id.value

            assertThrows(InvalidAvatarExtensionException::class.java) {
                userService.addAvatar(userId, listOf(avatarUploadTxt))
            }
            val avatars = userService.getAvatars(userId)
            assertEquals(0, avatars.size)

            rollback()
        }
    }

    @Test
    fun `add multiple avatars at once test`() {
        transaction {
            val avatarUpload1 = FileUploadData(avatarFile1.name, avatarFile1.inputStream())
            val avatarUpload2 = FileUploadData(avatarFile2.name, avatarFile2.inputStream())
            val avatarUpload3 = FileUploadData(avatarFile3.name, avatarFile3.inputStream())

            userService.signUp(testUser, "")
            val foundUser = findUserByLogin(testUser.login)!!
            val userId = foundUser.id.value

            userService.addAvatar(userId, listOf(avatarUpload1, avatarUpload2, avatarUpload3))
            val avatars = userService.getAvatars(userId)
            assertEquals(3, avatars.size)
            val avatar1 = avatars[0]
            assertEquals(userId, avatar1.ownerId)
            assertEquals(avatarUpload1.extension, avatar1.extension)
            assertEquals(avatarUpload1.type, avatar1.type)

            val avatar2 = avatars[1]
            assertEquals(userId, avatar2.ownerId)
            assertEquals(avatarUpload2.extension, avatar2.extension)
            assertEquals(avatarUpload2.type, avatar2.type)

            val avatar3 = avatars[2]
            assertEquals(userId, avatar3.ownerId)
            assertEquals(avatarUpload3.extension, avatar3.extension)
            assertEquals(avatarUpload3.type, avatar3.type)

            rollback()
        }
    }

    @Test
    fun `delete first avatar`() {
        transaction {
            val avatarUpload1 = FileUploadData(avatarFile1.name, avatarFile1.inputStream())
            val avatarUpload2 = FileUploadData(avatarFile2.name, avatarFile2.inputStream())
            val avatarUpload3 = FileUploadData(avatarFile3.name, avatarFile3.inputStream())

            userService.signUp(testUser, "")
            val foundUser = findUserByLogin(testUser.login)!!
            val userId = foundUser.id.value

            userService.addAvatar(userId, listOf(avatarUpload1, avatarUpload2, avatarUpload3))
            val avatars = userService.getAvatars(userId)
            val id1 = avatars[0].id
            val id2 = avatars[1].id
            val id3 = avatars[2].id

            userService.deleteAvatar(userId, id1)
            assertEquals(listOf(id2, id3), userService.getAvatars(userId).map { it.id })

            rollback()
        }
    }

    @Test
    fun `delete last avatar`() {
        transaction {
            val avatarUpload1 = FileUploadData(avatarFile1.name, avatarFile1.inputStream())
            val avatarUpload2 = FileUploadData(avatarFile2.name, avatarFile2.inputStream())
            val avatarUpload3 = FileUploadData(avatarFile3.name, avatarFile3.inputStream())

            userService.signUp(testUser, "")
            val foundUser = findUserByLogin(testUser.login)!!
            val userId = foundUser.id.value

            userService.addAvatar(userId, listOf(avatarUpload1, avatarUpload2, avatarUpload3))
            val avatars = userService.getAvatars(userId)
            val id1 = avatars[0].id
            val id2 = avatars[1].id
            val id3 = avatars[2].id

            userService.deleteAvatar(userId, id3)
            assertEquals(listOf(id1, id2), userService.getAvatars(userId).map { it.id })

            rollback()
        }
    }

    @Test
    fun `delete in between avatar`() {
        transaction {
            val avatarUpload1 = FileUploadData(avatarFile1.name, avatarFile1.inputStream())
            val avatarUpload2 = FileUploadData(avatarFile2.name, avatarFile2.inputStream())
            val avatarUpload3 = FileUploadData(avatarFile3.name, avatarFile3.inputStream())

            userService.signUp(testUser, "")
            val foundUser = findUserByLogin(testUser.login)!!
            val userId = foundUser.id.value

            userService.addAvatar(userId, listOf(avatarUpload1, avatarUpload2, avatarUpload3))
            val avatars = userService.getAvatars(userId)
            val id1 = avatars[0].id
            val id2 = avatars[1].id
            val id3 = avatars[2].id

            userService.deleteAvatar(userId, id2)
            assertEquals(listOf(id1, id3), userService.getAvatars(userId).map { it.id })

            rollback()
        }
    }

    @Test
    fun `reorder avatars`() {
        transaction {
            val avatarUpload1 = FileUploadData(avatarFile1.name, avatarFile1.inputStream())
            val avatarUpload2 = FileUploadData(avatarFile2.name, avatarFile2.inputStream())
            val avatarUpload3 = FileUploadData(avatarFile3.name, avatarFile3.inputStream())

            userService.signUp(testUser, "")
            val foundUser = findUserByLogin(testUser.login)!!
            val userId = foundUser.id.value

            userService.addAvatar(userId, listOf(avatarUpload1, avatarUpload2, avatarUpload3))
            val avatars = userService.getAvatars(userId)
            val id1 = avatars[0].id
            val id2 = avatars[1].id
            val id3 = avatars[2].id

            userService.reorderAvatars(userId, listOf(id3, id2, id1))
            assertEquals(listOf(id3, id2, id1), userService.getAvatars(userId).map { it.id })

            rollback()
        }
    }

    @Test
    fun `reorder avatars with one missing`() {
        transaction {
            val avatarUpload1 = FileUploadData(avatarFile1.name, avatarFile1.inputStream())
            val avatarUpload2 = FileUploadData(avatarFile2.name, avatarFile2.inputStream())
            val avatarUpload3 = FileUploadData(avatarFile3.name, avatarFile3.inputStream())

            userService.signUp(testUser, "")
            val foundUser = findUserByLogin(testUser.login)!!
            val userId = foundUser.id.value

            userService.addAvatar(userId, listOf(avatarUpload1, avatarUpload2, avatarUpload3))
            val avatars = userService.getAvatars(userId)
            val id2 = avatars[1].id
            val id3 = avatars[2].id

            userService.reorderAvatars(userId, listOf(id3, id2))
            assertEquals(listOf(id3, id2), userService.getAvatars(userId).map { it.id })

            rollback()
        }
    }

    @Test
    fun `delete avatar with nonexistent uuid`() {
        transaction {
            val avatarUpload1 = FileUploadData(avatarFile1.name, avatarFile1.inputStream())
            val avatarUpload2 = FileUploadData(avatarFile2.name, avatarFile2.inputStream())
            val avatarUpload3 = FileUploadData(avatarFile3.name, avatarFile3.inputStream())

            userService.signUp(testUser, "")
            val foundUser = findUserByLogin(testUser.login)!!
            val userId = foundUser.id.value

            userService.addAvatar(userId, listOf(avatarUpload1, avatarUpload2, avatarUpload3))
            userService.deleteAvatar(userId, UUID.randomUUID())
            val avatars = userService.getAvatars(userId)
            assertEquals(3, avatars.size)

            rollback()
        }
    }

    @Test
    fun `delete avatar of other user`() {
        transaction {
            val avatarUpload1 = FileUploadData(avatarFile1.name, avatarFile1.inputStream())
            val avatarUpload2 = FileUploadData(avatarFile2.name, avatarFile2.inputStream())

            userService.signUp(testUser, "")
            val userId1 = findUserByLogin(testUser.login)!!.id.value
            val inviteCode = userService.generateInviteCode(userId1)
            userService.signUp(testUser2, inviteCode)
            val userId2 = findUserByLogin(testUser2.login)!!.id.value

            userService.addAvatar(userId1, listOf(avatarUpload1))
            var avatars1 = userService.getAvatars(userId1)
            assertEquals(1, avatars1.size)
            val avatar1 = avatars1[0]

            userService.addAvatar(userId2, listOf(avatarUpload2))
            var avatars2 = userService.getAvatars(userId2)
            assertEquals(1, avatars2.size)
            val avatar2 = avatars2[0]

            userService.deleteAvatar(userId2, avatar1.id)

            avatars1 = userService.getAvatars(userId1)
            assertEquals(listOf(avatar1), avatars1)

            avatars2 = userService.getAvatars(userId2)
            assertEquals(listOf(avatar2), avatars2)

            rollback()
        }
    }

    @Test
    fun `reorder avatars with uuid of other users file`() {
        transaction {
            val avatarUpload1 = FileUploadData(avatarFile1.name, avatarFile1.inputStream())
            val avatarUpload2 = FileUploadData(avatarFile2.name, avatarFile2.inputStream())
            val avatarUpload3 = FileUploadData(avatarFile3.name, avatarFile3.inputStream())

            userService.signUp(testUser, "")
            val userId1 = findUserByLogin(testUser.login)!!.id.value
            val inviteCode = userService.generateInviteCode(userId1)

            userService.signUp(testUser2, inviteCode)
            val userId2 = findUserByLogin(testUser2.login)!!.id.value

            userService.addAvatar(userId1, listOf(avatarUpload1, avatarUpload2))
            val avatars1 = userService.getAvatars(userId1)

            userService.addAvatar(userId2, listOf(avatarUpload3))
            val avatars2 = userService.getAvatars(userId2)

            assertThrows(WrongUserException::class.java) {
                userService.reorderAvatars(userId2, avatars2.map { it.id } + avatars1[0].id)
            }
            assertEquals(avatars1, userService.getAvatars(userId1))
            assertEquals(avatars2, userService.getAvatars(userId2))

            rollback()
        }
    }

    @Test
    fun `reorder avatars with non-avatar uuid`() {
        transaction {
            val avatarUpload1 = FileUploadData(avatarFile1.name, avatarFile1.inputStream())
            val avatarUpload2 = FileUploadData(avatarFile2.name, avatarFile2.inputStream())
            val avatarUpload3 = FileUploadData(avatarFile3.name, avatarFile3.inputStream())

            userService.signUp(testUser, "")
            val userId1 = findUserByLogin(testUser.login)!!.id.value

            userService.addAvatar(userId1, listOf(avatarUpload1, avatarUpload2))
            val avatars = userService.getAvatars(userId1)

            val storedFiles = storageService.store(userId1, listOf(avatarUpload3))
            assertEquals(1, storedFiles.size)
            val storedFileUUID = storedFiles[0].id

            assertThrows(WrongUserException::class.java) {
                userService.reorderAvatars(userId1, avatars.map { it.id } + storedFileUUID)
            }

            rollback()
        }
    }
}