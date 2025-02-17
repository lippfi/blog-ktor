package fi.lipp.blog

import fi.lipp.blog.data.FileUploadData
import fi.lipp.blog.data.FriendRequestDto
import fi.lipp.blog.data.Language
import fi.lipp.blog.data.UserDto
import fi.lipp.blog.domain.FriendLabelEntity
import fi.lipp.blog.domain.PasswordResetCodeEntity
import fi.lipp.blog.domain.UserEntity
import fi.lipp.blog.model.exceptions.*
import fi.lipp.blog.repository.*
import fi.lipp.blog.stubs.ApplicationPropertiesStub
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.or
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
import javax.imageio.ImageIO

class UserServiceTests : UnitTestBase() {
    companion object {
        @JvmStatic
        @BeforeClass
        fun setUp() {
            File(properties.imagesDirectory("").toString()).mkdirs()
        }

        @JvmStatic
        @AfterClass
        fun tearDown() {
            File((properties as ApplicationPropertiesStub).basePath.toString()).deleteRecursively()
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

            val inviteCode = userService.generateInviteCode(foundUser.id)
            assertThrows(LoginIsBusyException::class.java) {
                userService.signUp(
                    UserDto.Registration(testUser.login, "new" + testUser.email, testUser.password, "new" + testUser.nickname, language = Language.EN, timezone = "Asia/Seoul"),
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

            val inviteCode = userService.generateInviteCode(foundUser.id)
            assertThrows(EmailIsBusyException::class.java) {
                userService.signUp(
                    UserDto.Registration("new" + testUser.login, testUser.email, testUser.password, "new" + testUser.nickname, language = Language.EN, timezone = "Asia/Seoul"),
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

            val inviteCode = userService.generateInviteCode(foundUser.id)
            assertThrows(NicknameIsBusyException::class.java) {
                userService.signUp(
                    UserDto.Registration("new" + testUser.login, "new" + testUser.email, testUser.password, testUser.nickname, language = Language.EN, timezone = "Asia/Seoul"),
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
            userService.signIn(UserDto.Login(testUser.login, testUser.password))
            rollback()
        }
    }

    @Test
    fun `sign in with wrong password`() {
        transaction {
            userService.signUp(testUser, "")
            assertThrows(WrongPasswordException::class.java) {
                userService.signIn(UserDto.Login(testUser.login, "wrong" + testUser.password))
            }
            rollback()
        }
    }

    @Test
    fun `sign in with nonexistent login`() {
        transaction {
            userService.signUp(testUser, "")
            assertThrows(UserNotFoundException::class.java) {
                userService.signIn(UserDto.Login("unknown" + testUser.login, testUser.password))
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
            val newUser = UserDto.Registration(testUser.login, "new" + testUser.email, "new" + testUser.password, "new" + testUser.nickname, language = Language.EN, timezone = "Asia/Seoul")
            userService.update(userEntity.id, newUser, testUser.password)

            val updatedUser = findUserByLogin(newUser.login)!!
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
            val newUser = UserDto.Registration(testUser.login, "new" + testUser.email, testUser.password, testUser.nickname, language = Language.EN, timezone = "Asia/Seoul")
            userService.update(userEntity.id, newUser, testUser.password)

            val updatedUser = findUserByLogin(newUser.login)!!
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
            val newUser = UserDto.Registration(testUser.login, testUser.email, testUser.password, "new" + testUser.nickname, language = Language.EN, timezone = "Asia/Seoul")
            userService.update(userEntity.id, newUser, testUser.password)

            val updatedUser = findUserByLogin(newUser.login)!!
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
            val newUser = UserDto.Registration(testUser.login, testUser.email, testUser.password, "new" + testUser.nickname, language = Language.EN, timezone = "Asia/Seoul")
            assertThrows(WrongPasswordException::class.java) {
                userService.update(userEntity.id, newUser, "wrong" + testUser.password)
            }

            val updatedUser = findUserByLogin(newUser.login)!!
            assertEquals(testUser.login, updatedUser.login)
            assertEquals(testUser.email, updatedUser.email)
            assertEquals(testUser.nickname, updatedUser.nickname)
            assertTrue(encoder.matches(testUser.password, updatedUser.password))
            assertEquals(registrationTime, updatedUser.registrationTime)

            rollback()
        }
    }

    @Test
    fun `updating user with busy email`() {
        transaction {
            userService.signUp(testUser, "")
            val foundUser = findUserByLogin(testUser.login)!!
            val inviteCode = userService.generateInviteCode(foundUser.id)
            userService.signUp(testUser2, inviteCode)

            val userEntity = findUserByLogin(testUser.login)!!
            val updatedUser = UserDto.Registration(testUser.login, testUser2.email, testUser.password, testUser.nickname, language = Language.EN, timezone = "Asia/Seoul")
            assertThrows(EmailIsBusyException::class.java) {
                userService.update(userEntity.id, updatedUser, testUser.password)
            }

            rollback()
        }
    }

    @Test
    fun `updating user with busy nickname`() {
        transaction {
            userService.signUp(testUser, "")
            val foundUser = findUserByLogin(testUser.login)!!
            val inviteCode = userService.generateInviteCode(foundUser.id)
            userService.signUp(testUser2, inviteCode)

            val userEntity = findUserByLogin(testUser.login)!!
            val updatedUser = UserDto.Registration(testUser.login, testUser.email, testUser.password, testUser2.nickname, language = Language.EN, timezone = "Asia/Seoul")
            assertThrows(NicknameIsBusyException::class.java) {
                userService.update(userEntity.id, updatedUser, testUser.password)
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
            assertEquals(foundUser.id, passwordResetCodeEntity.userId.value)
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
            val userId = foundUser.id

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
    fun `adding avatar with wrong dimensions`() {
        transaction {
            val avatarUpload1 = FileUploadData(avatarFile1.name, avatarFile1.inputStream())
            // Modify the image dimensions by wrapping it in a new FileUploadData
            val modifiedInputStream = avatarFile1.inputStream().use { input ->
                val originalImage = ImageIO.read(input)
                val resizedImage = java.awt.image.BufferedImage(200, 200, originalImage.type)
                val g = resizedImage.createGraphics()
                g.drawImage(originalImage, 0, 0, 200, 200, null)
                g.dispose()

                val outputStream = java.io.ByteArrayOutputStream()
                ImageIO.write(resizedImage, "png", outputStream)
                java.io.ByteArrayInputStream(outputStream.toByteArray())
            }
            val wrongSizeUpload = FileUploadData(avatarFile1.name, modifiedInputStream)

            userService.signUp(testUser, "")
            val foundUser = findUserByLogin(testUser.login)!!
            val userId = foundUser.id

            assertThrows(InvalidAvatarDimensionsException::class.java) {
                userService.addAvatar(userId, listOf(wrongSizeUpload))
            }
            val avatars = userService.getAvatars(userId)
            assertEquals(0, avatars.size)

            rollback()
        }
    }

    @Test
    fun `adding avatar larger than 1MB`() {
        transaction {
            // Create a large image that's over 1MB
            val width = 1000
            val height = 1000
            val image = java.awt.image.BufferedImage(width, height, java.awt.image.BufferedImage.TYPE_INT_ARGB)
            val g = image.createGraphics()
            // Fill with random colors to ensure file size is large
            for (x in 0 until width) {
                for (y in 0 until height) {
                    g.color = java.awt.Color((Math.random() * 0xFFFFFF).toInt())
                    g.fillRect(x, y, 1, 1)
                }
            }
            g.dispose()

            val outputStream = java.io.ByteArrayOutputStream()
            ImageIO.write(image, "png", outputStream)
            val largeFileData = FileUploadData("large_avatar.png", java.io.ByteArrayInputStream(outputStream.toByteArray()))

            userService.signUp(testUser, "")
            val foundUser = findUserByLogin(testUser.login)!!
            val userId = foundUser.id

            assertThrows(InvalidAvatarSizeException::class.java) {
                userService.addAvatar(userId, listOf(largeFileData))
            }
            val avatars = userService.getAvatars(userId)
            assertEquals(0, avatars.size)

            rollback()
        }
    }

    @Test
    fun `adding avatar with wrong extension`() {
        transaction {
            val avatarUploadTxt = FileUploadData(avatarFileTxt.name, avatarFileTxt.inputStream())

            userService.signUp(testUser, "")
            val foundUser = findUserByLogin(testUser.login)!!
            val userId = foundUser.id

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
            val userId = foundUser.id

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
            val userId = foundUser.id

            userService.addAvatar(userId, listOf(avatarUpload1, avatarUpload2, avatarUpload3))
            val avatars = userService.getAvatarUrls(userId)
            val avatar1 = avatars[0]
            val avatar2 = avatars[1]
            val avatar3 = avatars[2]

            userService.deleteAvatar(userId, avatar1)
            assertEquals(listOf(avatar2, avatar3), userService.getAvatarUrls(userId))

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
            val userId = foundUser.id

            userService.addAvatar(userId, listOf(avatarUpload1, avatarUpload2, avatarUpload3))
            val avatars = userService.getAvatarUrls(userId)
            val avatar1 = avatars[0]
            val avatar2 = avatars[1]
            val avatar3 = avatars[2]

            userService.deleteAvatar(userId, avatar3)
            assertEquals(listOf(avatar1, avatar2), userService.getAvatarUrls(userId))

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
            val userId = foundUser.id

            userService.addAvatar(userId, listOf(avatarUpload1, avatarUpload2, avatarUpload3))
            val avatars = userService.getAvatarUrls(userId)
            val avatar1 = avatars[0]
            val avatar2 = avatars[1]
            val avatar3 = avatars[2]

            userService.deleteAvatar(userId, avatar2)
            assertEquals(listOf(avatar1, avatar3), userService.getAvatarUrls(userId))

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
            val userId = foundUser.id

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
            val userId = foundUser.id

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
            val userId = foundUser.id

            userService.addAvatar(userId, listOf(avatarUpload1, avatarUpload2, avatarUpload3))
            userService.deleteAvatar(userId, "nonexistent uri")
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
            val userId1 = findUserByLogin(testUser.login)!!.id
            val inviteCode = userService.generateInviteCode(userId1)
            userService.signUp(testUser2, inviteCode)
            val userId2 = findUserByLogin(testUser2.login)!!.id

            userService.addAvatar(userId1, listOf(avatarUpload1))
            var avatars1 = userService.getAvatars(userId1)
            assertEquals(1, avatars1.size)
            val avatar1 = avatars1[0]

            userService.addAvatar(userId2, listOf(avatarUpload2))
            var avatars2 = userService.getAvatars(userId2)
            assertEquals(1, avatars2.size)
            val avatar2 = avatars2[0]

            userService.deleteAvatar(userId2, storageService.getFileURL(avatar1))

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
            val userId1 = findUserByLogin(testUser.login)!!.id
            val inviteCode = userService.generateInviteCode(userId1)

            userService.signUp(testUser2, inviteCode)
            val userId2 = findUserByLogin(testUser2.login)!!.id

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
            val userId1 = findUserByLogin(testUser.login)!!.id

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

    @Test
    fun `first avatar becomes primary`() {
        transaction {
            userService.signUp(testUser, "")
            val userId = findUserByLogin(testUser.login)!!.id

            // Add first avatar
            val avatarUpload1 = FileUploadData(avatarFile1.name, avatarFile1.inputStream())
            userService.addAvatar(userId, listOf(avatarUpload1))
            val avatars1 = userService.getAvatars(userId)
            assertEquals(1, avatars1.size)

            // Verify it became primary
            val user = UserEntity.findById(userId)!!
            assertNotNull(user.primaryAvatar)
            assertEquals(avatars1[0].id, user.primaryAvatar!!.value)

            // Add second avatar
            val avatarUpload2 = FileUploadData(avatarFile2.name, avatarFile2.inputStream())
            userService.addAvatar(userId, listOf(avatarUpload2))
            val avatars2 = userService.getAvatars(userId)
            assertEquals(2, avatars2.size)

            // Verify first avatar is still primary
            assertEquals(avatars1[0].id, user.primaryAvatar!!.value)

            rollback()
        }
    }

    @Test
    fun `change primary avatar`() {
        transaction {
            userService.signUp(testUser, "")
            val userId = findUserByLogin(testUser.login)!!.id

            // Add two avatars
            val avatarUpload1 = FileUploadData(avatarFile1.name, avatarFile1.inputStream())
            val avatarUpload2 = FileUploadData(avatarFile2.name, avatarFile2.inputStream())
            userService.addAvatar(userId, listOf(avatarUpload1, avatarUpload2))
            val avatars = userService.getAvatars(userId)
            assertEquals(2, avatars.size)

            // Change primary avatar to the second one
            val user = UserEntity.findById(userId)!!
            val firstPrimaryId = user.primaryAvatar!!.value
            val secondAvatarUrl = storageService.getFileURL(avatars[1])
            userService.changePrimaryAvatar(userId, secondAvatarUrl)

            // Verify primary avatar changed
            assertNotEquals(firstPrimaryId, user.primaryAvatar!!.value)
            assertEquals(avatars[1].id, user.primaryAvatar!!.value)

            rollback()
        }
    }

    @Test
    fun `upload existing avatar file`() {
        transaction {
            // Create two users
            userService.signUp(testUser, "")
            val userId1 = findUserByLogin(testUser.login)!!.id
            val inviteCode = userService.generateInviteCode(userId1)
            userService.signUp(testUser2, inviteCode)
            val userId2 = findUserByLogin(testUser2.login)!!.id

            // Add avatar to first user
            val avatarUpload = FileUploadData(avatarFile1.name, avatarFile1.inputStream())
            userService.addAvatar(userId1, listOf(avatarUpload))
            val avatars1 = userService.getAvatars(userId1)
            assertEquals(1, avatars1.size)

            // Upload the same avatar for second user
            val avatarUrl = storageService.getFileURL(avatars1[0])
            userService.uploadAvatar(userId2, avatarUrl)

            // Verify that the same avatar was added to second user
            val avatars2 = userService.getAvatars(userId2)
            assertEquals(1, avatars2.size)

            rollback()
        }
    }

    @Test
    fun `upload non-avatar file as avatar`() {
        transaction {
            userService.signUp(testUser, "")
            val userId = findUserByLogin(testUser.login)!!.id

            // Upload a non-avatar file
            val fileUpload = FileUploadData(avatarFile1.name, avatarFile1.inputStream())
            val files = storageService.store(userId, listOf(fileUpload))
            assertEquals(1, files.size)

            // Try to use it as avatar
            val fileUrl = storageService.getFileURL(files[0])
            userService.uploadAvatar(userId, fileUrl)

            // Verify that a new avatar was created
            val avatars = userService.getAvatars(userId)
            assertEquals(1, avatars.size)
            assertNotEquals(files[0].id, avatars[0].id)

            rollback()
        }
    }

    @Test
    fun `change primary avatar to other user avatar`() {
        transaction {
            // Create two users
            userService.signUp(testUser, "")
            val userId1 = findUserByLogin(testUser.login)!!.id
            val inviteCode = userService.generateInviteCode(userId1)
            userService.signUp(testUser2, inviteCode)
            val userId2 = findUserByLogin(testUser2.login)!!.id

            // Add avatar to first user
            val avatarUpload = FileUploadData(avatarFile1.name, avatarFile1.inputStream())
            userService.addAvatar(userId1, listOf(avatarUpload))
            val avatars1 = userService.getAvatars(userId1)
            assertEquals(1, avatars1.size)

            // Try to set first user's avatar as primary for second user
            val avatarUrl = storageService.getFileURL(avatars1[0])
            userService.changePrimaryAvatar(userId2, avatarUrl)

            // Verify that a new copy of the avatar was created
            val avatars2 = userService.getAvatars(userId2)
            assertEquals(1, avatars2.size)
            assertNotEquals(avatars1[0].id, avatars2[0].id)

            // Verify it was set as primary
            val user2 = UserEntity.findById(userId2)!!
            assertNotNull(user2.primaryAvatar)
            assertEquals(avatars2[0].id, user2.primaryAvatar!!.value)

            rollback()
        }
    }

    @Test
    fun `delete primary avatar`() {
        transaction {
            userService.signUp(testUser, "")
            val userId = findUserByLogin(testUser.login)!!.id

            // Add two avatars
            val avatarUpload1 = FileUploadData(avatarFile1.name, avatarFile1.inputStream())
            val avatarUpload2 = FileUploadData(avatarFile2.name, avatarFile2.inputStream())
            userService.addAvatar(userId, listOf(avatarUpload1, avatarUpload2))
            val avatars = userService.getAvatars(userId)
            assertEquals(2, avatars.size)

            // Delete primary avatar
            val user = UserEntity.findById(userId)!!
            val firstPrimaryId = user.primaryAvatar!!.value
            val firstAvatarUrl = storageService.getFileURL(avatars[0])
            userService.deleteAvatar(userId, firstAvatarUrl)

            // Verify second avatar became primary
            assertNotEquals(firstPrimaryId, user.primaryAvatar!!.value)
            assertEquals(avatars[1].id, user.primaryAvatar!!.value)

            // Delete last avatar
            val secondAvatarUrl = storageService.getFileURL(avatars[1])
            userService.deleteAvatar(userId, secondAvatarUrl)

            // Verify no primary avatar
            assertNull(user.primaryAvatar)

            rollback()
        }
    }

    @Test
    fun `send friend request successfully`() {
        transaction {
            userService.signUp(testUser, "")
            val userId1 = findUserByLogin(testUser.login)!!.id
            val inviteCode = userService.generateInviteCode(userId1)
            userService.signUp(testUser2, inviteCode)
            val userId2 = findUserByLogin(testUser2.login)!!.id

            val request = FriendRequestDto.Create("bigbabyboy", "Let's be friends!", "coworker")
            userService.sendFriendRequest(userId1, request)

            val sentRequests = userService.getSentFriendRequests(userId1)
            assertEquals(1, sentRequests.size)
            assertEquals(request.message, sentRequests[0].message)
            assertEquals(request.label, sentRequests[0].label)
            assertEquals(testUser2.login, sentRequests[0].user.login)

            val receivedRequests = userService.getReceivedFriendRequests(userId2)
            assertEquals(1, receivedRequests.size)
            assertEquals(sentRequests[0].id, receivedRequests[0].id)

            rollback()
        }
    }

    @Test
    fun `send friend request to nonexistent user`() {
        transaction {
            userService.signUp(testUser, "")
            val userId1 = findUserByLogin(testUser.login)!!.id

            val request = FriendRequestDto.Create("nonexistent_user", "Let's be friends!", null)
            assertThrows(UserNotFoundException::class.java) {
                userService.sendFriendRequest(userId1, request)
            }

            rollback()
        }
    }

    @Test
    fun `send friend request when already friends`() {
        transaction {
            userService.signUp(testUser, "")
            val userId1 = findUserByLogin(testUser.login)!!.id
            val inviteCode = userService.generateInviteCode(userId1)
            userService.signUp(testUser2, inviteCode)
            val userId2 = findUserByLogin(testUser2.login)!!.id

            val request = FriendRequestDto.Create("bigbabyboy", "Let's be friends!", null)
            userService.sendFriendRequest(userId1, request)
            userService.acceptFriendRequest(userId2, userService.getReceivedFriendRequests(userId2)[0].id, null)

            assertThrows(AlreadyFriendsException::class.java) {
                userService.sendFriendRequest(userId1, request)
            }

            rollback()
        }
    }

    @Test
    fun `send duplicate friend request`() {
        transaction {
            userService.signUp(testUser, "")
            val userId1 = findUserByLogin(testUser.login)!!.id
            val inviteCode = userService.generateInviteCode(userId1)
            userService.signUp(testUser2, inviteCode)
            val userId2 = findUserByLogin(testUser2.login)!!.id

            val request = FriendRequestDto.Create("bigbabyboy", "Let's be friends!", null)
            userService.sendFriendRequest(userId1, request)

            assertThrows(FriendRequestAlreadyExistsException::class.java) {
                userService.sendFriendRequest(userId1, request)
            }

            rollback()
        }
    }

    @Test
    fun `accept friend request successfully`() {
        transaction {
            userService.signUp(testUser, "")
            val userId1 = findUserByLogin(testUser.login)!!.id
            val inviteCode = userService.generateInviteCode(userId1)
            userService.signUp(testUser2, inviteCode)
            val userId2 = findUserByLogin(testUser2.login)!!.id

            val request = FriendRequestDto.Create("bigbabyboy", "Let's be friends!", "coworker")
            userService.sendFriendRequest(userId1, request)
            val requestId = userService.getReceivedFriendRequests(userId2)[0].id

            // Accept with a different label
            userService.acceptFriendRequest(userId2, requestId, "classmate")

            // Request should be removed
            assertEquals(0, userService.getSentFriendRequests(userId1).size)
            assertEquals(0, userService.getReceivedFriendRequests(userId2).size)

            // Both users should be friends
            val user1Friends = userService.getFriends(userId1)
            val user2Friends = userService.getFriends(userId2)
            assertEquals(1, user1Friends.size)
            assertEquals(1, user2Friends.size)
            assertEquals(testUser2.login, user1Friends[0].login)
            assertEquals(testUser.login, user2Friends[0].login)

            rollback()
        }
    }

    @Test
    fun `accept nonexistent friend request`() {
        transaction {
            userService.signUp(testUser, "")
            val userId = findUserByLogin(testUser.login)!!.id

            assertThrows(FriendRequestNotFoundException::class.java) {
                userService.acceptFriendRequest(userId, UUID.randomUUID(), null)
            }

            rollback()
        }
    }

    @Test
    fun `accept friend request as non-recipient`() {
        transaction {
            userService.signUp(testUser, "")
            val userId1 = findUserByLogin(testUser.login)!!.id
            val inviteCode = userService.generateInviteCode(userId1)
            userService.signUp(testUser2, inviteCode)
            val userId2 = findUserByLogin(testUser2.login)!!.id

            val request = FriendRequestDto.Create("bigbabyboy", "Let's be friends!", null)
            userService.sendFriendRequest(userId1, request)
            val requestId = userService.getReceivedFriendRequests(userId2)[0].id

            assertThrows(NotRequestRecipientException::class.java) {
                userService.acceptFriendRequest(userId1, requestId, null)
            }

            rollback()
        }
    }

    @Test
    fun `decline friend request successfully`() {
        transaction {
            userService.signUp(testUser, "")
            val userId1 = findUserByLogin(testUser.login)!!.id
            val inviteCode = userService.generateInviteCode(userId1)
            userService.signUp(testUser2, inviteCode)
            val userId2 = findUserByLogin(testUser2.login)!!.id

            val request = FriendRequestDto.Create("bigbabyboy", "Let's be friends!", null)
            userService.sendFriendRequest(userId1, request)
            val requestId = userService.getReceivedFriendRequests(userId2)[0].id

            userService.declineFriendRequest(userId2, requestId)

            // Request should be removed
            assertEquals(0, userService.getSentFriendRequests(userId1).size)
            assertEquals(0, userService.getReceivedFriendRequests(userId2).size)

            // Users should not be friends
            assertEquals(0, userService.getFriends(userId1).size)
            assertEquals(0, userService.getFriends(userId2).size)

            rollback()
        }
    }

    @Test
    fun `cancel friend request successfully`() {
        transaction {
            userService.signUp(testUser, "")
            val userId1 = findUserByLogin(testUser.login)!!.id
            val inviteCode = userService.generateInviteCode(userId1)
            userService.signUp(testUser2, inviteCode)
            val userId2 = findUserByLogin(testUser2.login)!!.id

            val request = FriendRequestDto.Create("bigbabyboy", "Let's be friends!", null)
            userService.sendFriendRequest(userId1, request)
            val requestId = userService.getSentFriendRequests(userId1)[0].id

            userService.cancelFriendRequest(userId1, requestId)

            // Request should be removed
            assertEquals(0, userService.getSentFriendRequests(userId1).size)
            assertEquals(0, userService.getReceivedFriendRequests(userId2).size)

            // Users should not be friends
            assertEquals(0, userService.getFriends(userId1).size)
            assertEquals(0, userService.getFriends(userId2).size)

            rollback()
        }
    }

    @Test
    fun `cancel nonexistent friend request`() {
        transaction {
            userService.signUp(testUser, "")
            val userId1 = findUserByLogin(testUser.login)!!.id

            assertThrows(FriendRequestNotFoundException::class.java) {
                userService.cancelFriendRequest(userId1, UUID.randomUUID())
            }

            rollback()
        }
    }

    @Test
    fun `cancel friend request as non-sender`() {
        transaction {
            userService.signUp(testUser, "")
            val userId1 = findUserByLogin(testUser.login)!!.id
            val inviteCode = userService.generateInviteCode(userId1)
            userService.signUp(testUser2, inviteCode)
            val userId2 = findUserByLogin(testUser2.login)!!.id

            val request = FriendRequestDto.Create("bigbabyboy", "Let's be friends!", null)
            userService.sendFriendRequest(userId1, request)
            val requestId = userService.getSentFriendRequests(userId1)[0].id

            assertThrows(NotRequestSenderException::class.java) {
                userService.cancelFriendRequest(userId2, requestId)
            }

            rollback()
        }
    }

    @Test
    fun `remove friend successfully`() {
        transaction {
            userService.signUp(testUser, "")
            val userId1 = findUserByLogin(testUser.login)!!.id
            val inviteCode = userService.generateInviteCode(userId1)
            userService.signUp(testUser2, inviteCode)
            val userId2 = findUserByLogin(testUser2.login)!!.id

            val request = FriendRequestDto.Create("bigbabyboy", "Let's be friends!", null)
            userService.sendFriendRequest(userId1, request)
            userService.acceptFriendRequest(userId2, userService.getReceivedFriendRequests(userId2)[0].id, null)

            assertEquals(1, userService.getFriends(userId1).size)
            assertEquals(1, userService.getFriends(userId2).size)

            userService.removeFriend(userId1, testUser2.login)

            assertEquals(0, userService.getFriends(userId1).size)
            assertEquals(0, userService.getFriends(userId2).size)

            rollback()
        }
    }

    @Test
    fun `remove nonexistent friend`() {
        transaction {
            userService.signUp(testUser, "")
            val userId1 = findUserByLogin(testUser.login)!!.id
            val inviteCode = userService.generateInviteCode(userId1)
            userService.signUp(testUser2, inviteCode)
            val userId2 = findUserByLogin(testUser2.login)!!.id

            assertThrows(NotFriendsException::class.java) {
                userService.removeFriend(userId1, testUser2.login)
            }

            rollback()
        }
    }

    @Test
    fun `verify friend labels after accepting request`() {
        transaction {
            userService.signUp(testUser, "")
            val userId1 = findUserByLogin(testUser.login)!!.id
            val inviteCode = userService.generateInviteCode(userId1)
            userService.signUp(testUser2, inviteCode)
            val userId2 = findUserByLogin(testUser2.login)!!.id

            // Send friend request with label "coworker"
            val request = FriendRequestDto.Create("bigbabyboy", "Let's be friends!", "coworker")
            userService.sendFriendRequest(userId1, request)
            val requestId = userService.getReceivedFriendRequests(userId2)[0].id

            // Accept with a different label "classmate"
            userService.acceptFriendRequest(userId2, requestId, "classmate")

            // Verify that both users have their respective labels
            val user1Friends = userService.getFriends(userId1)
            val user2Friends = userService.getFriends(userId2)

            assertEquals("coworker", FriendLabelEntity.find {
                FriendLabels.user eq userId1 and (FriendLabels.friend eq userId2)
            }.first().label)

            assertEquals("classmate", FriendLabelEntity.find {
                FriendLabels.user eq userId2 and (FriendLabels.friend eq userId1)
            }.first().label)

            rollback()
        }
    }

    @Test
    fun `update friend label`() {
        transaction {
            userService.signUp(testUser, "")
            val userId1 = findUserByLogin(testUser.login)!!.id
            val inviteCode = userService.generateInviteCode(userId1)
            userService.signUp(testUser2, inviteCode)
            val userId2 = findUserByLogin(testUser2.login)!!.id

            // Become friends first
            val request = FriendRequestDto.Create("bigbabyboy", "Let's be friends!", "coworker")
            userService.sendFriendRequest(userId1, request)
            userService.acceptFriendRequest(userId2, userService.getReceivedFriendRequests(userId2)[0].id, "classmate")

            // Update label
            userService.updateFriendLabel(userId1, testUser2.login, "best friend")

            // Verify label is updated
            assertEquals("best friend", FriendLabelEntity.find {
                FriendLabels.user eq userId1 and (FriendLabels.friend eq userId2)
            }.first().label)

            // Other user's label should remain unchanged
            assertEquals("classmate", FriendLabelEntity.find {
                FriendLabels.user eq userId2 and (FriendLabels.friend eq userId1)
            }.first().label)

            rollback()
        }
    }

    @Test
    fun `remove friend with labels`() {
        transaction {
            userService.signUp(testUser, "")
            val userId1 = findUserByLogin(testUser.login)!!.id
            val inviteCode = userService.generateInviteCode(userId1)
            userService.signUp(testUser2, inviteCode)
            val userId2 = findUserByLogin(testUser2.login)!!.id

            // Become friends with labels
            val request = FriendRequestDto.Create("bigbabyboy", "Let's be friends!", "coworker")
            userService.sendFriendRequest(userId1, request)
            userService.acceptFriendRequest(userId2, userService.getReceivedFriendRequests(userId2)[0].id, "classmate")

            // Remove friendship
            userService.removeFriend(userId1, testUser2.login)

            // Verify labels are removed
            assertEquals(0, FriendLabelEntity.find {
                (FriendLabels.user eq userId1 and (FriendLabels.friend eq userId2)) or
                (FriendLabels.user eq userId2 and (FriendLabels.friend eq userId1))
            }.count())

            rollback()
        }
    }
}
