package fi.lipp.blog

import fi.lipp.blog.data.FileUploadData
import fi.lipp.blog.data.FriendRequestDto
import fi.lipp.blog.data.Language
import fi.lipp.blog.data.NSFWPolicy
import fi.lipp.blog.data.Sex
import fi.lipp.blog.data.UserDto
import fi.lipp.blog.domain.FriendLabelEntity
import fi.lipp.blog.domain.PasswordResetCodeEntity
import fi.lipp.blog.domain.PendingRegistrationEntity
import fi.lipp.blog.domain.PendingEmailChangeEntity
import fi.lipp.blog.domain.UserEntity
import fi.lipp.blog.model.exceptions.ConfirmationCodeInvalidOrExpiredException
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
import org.mockito.kotlin.whenever
import org.mockito.kotlin.any
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
            // Get system user to generate invite code
            val systemUserId = userService.getOrCreateSystemUser()
            val inviteCode = userService.generateInviteCode(systemUserId)

            assertFalse(userService.isLoginBusy(testUser.login))
            userService.signUp(testUser, inviteCode)
            assertTrue(userService.isLoginBusy(testUser.login))
            rollback()
        }
    }

    @Test
    fun isEmailBusy() {
        transaction {
            // Get system user to generate invite code
            val systemUserId = userService.getOrCreateSystemUser()
            val inviteCode = userService.generateInviteCode(systemUserId)

            assertFalse(userService.isEmailBusy(testUser.email))
            userService.signUp(testUser, inviteCode)
            assertTrue(userService.isEmailBusy(testUser.email))
            rollback()
        }
    }

    @Test
    fun isNicknameBusy() {
        transaction {
            // Get system user to generate invite code
            val systemUserId = userService.getOrCreateSystemUser()
            val inviteCode = userService.generateInviteCode(systemUserId)

            assertFalse(userService.isNicknameBusy(testUser.nickname))
            userService.signUp(testUser, inviteCode)
            assertTrue(userService.isNicknameBusy(testUser.nickname))
            rollback()
        }
    }

    @Test
    fun `successful registration creates pending registration`() {
        transaction {
            // Get system user to generate invite code
            val systemUserId = userService.getOrCreateSystemUser()
            val inviteCode = userService.generateInviteCode(systemUserId)

            var foundUser = findUserByLogin(testUser.login)
            assertNull(foundUser)

            // Verify email was sent
            val emailCaptor = argumentCaptor<String>()
            val subjectCaptor = argumentCaptor<String>()
            val recipientCaptor = argumentCaptor<String>()

            userService.signUp(testUser, inviteCode)

            // Verify user is not created yet
            foundUser = findUserByLogin(testUser.login)
            assertNull(foundUser)

            // Verify pending registration exists
            val pendingRegistration = transaction {
                PendingRegistrationEntity.find { 
                    (PendingRegistrations.email eq testUser.email) or
                    (PendingRegistrations.login eq testUser.login) or
                    (PendingRegistrations.nickname eq testUser.nickname)
                }.firstOrNull()
            }

            assertNotNull(pendingRegistration)
            assertEquals(testUser.email, pendingRegistration!!.email)
            assertEquals(testUser.login, pendingRegistration.login)
            assertEquals(testUser.nickname, pendingRegistration.nickname)
            assertTrue(encoder.matches(testUser.password, pendingRegistration.password))

            // Verify email was sent
            verify(mailService).sendEmail(
                subjectCaptor.capture(),
                emailCaptor.capture(),
                recipientCaptor.capture()
            )

            assertEquals("Confirm Your Registration", subjectCaptor.lastValue)
            assertTrue(emailCaptor.lastValue.contains("Confirmation code"))
            assertEquals(testUser.email, recipientCaptor.lastValue)

            rollback()
        }
    }

    @Test
    fun `confirm registration creates user`() {
        transaction {
            // Get system user to generate invite code
            val systemUserId = userService.getOrCreateSystemUser()
            val inviteCode = userService.generateInviteCode(systemUserId)

            // Create pending registration
            userService.signUp(testUser, inviteCode)

            // Get confirmation code
            val pendingRegistration = transaction {
                PendingRegistrationEntity.find { 
                    (PendingRegistrations.email eq testUser.email)
                }.first()
            }

            val confirmationCode = pendingRegistration.id.value.toString()

            // Confirm registration
            val token = userService.confirmRegistration(confirmationCode)

            // Verify user is created
            val foundUser = findUserByLogin(testUser.login)
            assertNotNull(foundUser)

            assertEquals(testUser.login, foundUser.login)
            assertEquals(testUser.email, foundUser.email)
            assertEquals(testUser.nickname, foundUser.nickname)
            assertTrue(encoder.matches(testUser.password, foundUser.password))
            assertNow(foundUser.registrationTime)

            // Verify pending registration is deleted
            val pendingRegistrationAfterConfirmation = transaction {
                PendingRegistrationEntity.find { 
                    (PendingRegistrations.email eq testUser.email)
                }.firstOrNull()
            }

            assertNull(pendingRegistrationAfterConfirmation)

            // Verify token is returned
            assertNotNull(token)
            assertTrue(token.isNotEmpty())

            rollback()
        }
    }

    @Test
    fun `confirm registration with invalid code throws exception`() {
        transaction {
            assertThrows(ConfirmationCodeInvalidOrExpiredException::class.java) {
                userService.confirmRegistration("invalid-code")
            }

            rollback()
        }
    }

    @Test
    fun `pending registration reserves email login and nickname`() {
        transaction {
            // Get system user to generate invite code
            val systemUserId = userService.getOrCreateSystemUser()
            val inviteCode = userService.generateInviteCode(systemUserId)

            // Create pending registration
            userService.signUp(testUser, inviteCode)

            // Verify email, login, and nickname are reserved
            assertTrue(userService.isEmailBusy(testUser.email))
            assertTrue(userService.isLoginBusy(testUser.login))
            assertTrue(userService.isNicknameBusy(testUser.nickname))

            rollback()
        }
    }

    @Test
    fun `registration with busy login`() {
        transaction {
            // Get system user to generate invite code
            val systemUserId = userService.getOrCreateSystemUser()
            val inviteCode = userService.generateInviteCode(systemUserId)

            // Create pending registration
            userService.signUp(testUser, inviteCode)

            // Get confirmation code
            val pendingRegistration = transaction {
                PendingRegistrationEntity.find { 
                    (PendingRegistrations.email eq testUser.email)
                }.first()
            }

            val confirmationCode = pendingRegistration.id.value.toString()

            // Confirm registration
            userService.confirmRegistration(confirmationCode)

            // Verify user is created
            val foundUser = findUserByLogin(testUser.login)
            assertNotNull(foundUser)

            val nextInviteCode = userService.generateInviteCode(foundUser.id)
            assertThrows(LoginIsBusyException::class.java) {
                userService.signUp(
                    UserDto.Registration(testUser.login, "new" + testUser.email, testUser.password, "new" + testUser.nickname, language = Language.EN, timezone = "Asia/Seoul"),
                    nextInviteCode
                )
            }

            rollback()
        }
    }

    @Test
    fun `registration with busy email`() {
        transaction {
            // Get system user to generate invite code
            val systemUserId = userService.getOrCreateSystemUser()
            val inviteCode = userService.generateInviteCode(systemUserId)

            // Create pending registration
            userService.signUp(testUser, inviteCode)

            // Get confirmation code
            val pendingRegistration = transaction {
                PendingRegistrationEntity.find { 
                    (PendingRegistrations.email eq testUser.email)
                }.first()
            }

            val confirmationCode = pendingRegistration.id.value.toString()

            // Confirm registration
            userService.confirmRegistration(confirmationCode)

            // Verify user is created
            val foundUser = findUserByLogin(testUser.login)
            assertNotNull(foundUser)

            val nextInviteCode = userService.generateInviteCode(foundUser.id)
            assertThrows(EmailIsBusyException::class.java) {
                userService.signUp(
                    UserDto.Registration("new" + testUser.login, testUser.email, testUser.password, "new" + testUser.nickname, language = Language.EN, timezone = "Asia/Seoul"),
                    nextInviteCode
                )
            }

            rollback()
        }
    }

    @Test
    fun `registration with busy nickname`() {
        transaction {
            // Get system user to generate invite code
            val systemUserId = userService.getOrCreateSystemUser()
            val inviteCode = userService.generateInviteCode(systemUserId)

            var foundUser = findUserByLogin(testUser.login)
            assertNull(foundUser)

            // Create pending registration
            userService.signUp(testUser, inviteCode)

            // Get confirmation code
            val pendingRegistration = transaction {
                PendingRegistrationEntity.find { 
                    (PendingRegistrations.email eq testUser.email)
                }.first()
            }
            val confirmationCode = pendingRegistration.id.value.toString()

            // Confirm registration
            userService.confirmRegistration(confirmationCode)

            foundUser = findUserByLogin(testUser.login)
            assertNotNull(foundUser)

            val nextInviteCode = userService.generateInviteCode(foundUser.id)
            assertThrows(NicknameIsBusyException::class.java) {
                userService.signUp(
                    UserDto.Registration("new" + testUser.login, "new" + testUser.email, testUser.password, testUser.nickname, language = Language.EN, timezone = "Asia/Seoul"),
                    nextInviteCode
                )
            }

            rollback()
        }
    }

    @Test
    fun `successful sign in does not throw exception`() {
        transaction {
            // Get system user to generate invite code
            val systemUserId = userService.getOrCreateSystemUser()
            val inviteCode = userService.generateInviteCode(systemUserId)

            // Create pending registration
            userService.signUp(testUser, inviteCode)

            // Get confirmation code
            val pendingRegistration = transaction {
                PendingRegistrationEntity.find { 
                    (PendingRegistrations.email eq testUser.email)
                }.first()
            }
            val confirmationCode = pendingRegistration.id.value.toString()

            // Confirm registration
            userService.confirmRegistration(confirmationCode)

            userService.signIn(UserDto.Login(testUser.login, testUser.password))
            rollback()
        }
    }

    @Test
    fun `sign in with wrong password`() {
        transaction {
            // Get system user to generate invite code
            val systemUserId = userService.getOrCreateSystemUser()
            val inviteCode = userService.generateInviteCode(systemUserId)

            // Create pending registration
            userService.signUp(testUser, inviteCode)

            // Get confirmation code
            val pendingRegistration = transaction {
                PendingRegistrationEntity.find { 
                    (PendingRegistrations.email eq testUser.email)
                }.first()
            }
            val confirmationCode = pendingRegistration.id.value.toString()

            // Confirm registration
            userService.confirmRegistration(confirmationCode)

            assertThrows(WrongPasswordException::class.java) {
                userService.signIn(UserDto.Login(testUser.login, "wrong" + testUser.password))
            }
            rollback()
        }
    }

    @Test
    fun `sign in with nonexistent login`() {
        transaction {
            // Get system user to generate invite code
            val systemUserId = userService.getOrCreateSystemUser()
            val inviteCode = userService.generateInviteCode(systemUserId)

            // Create pending registration
            userService.signUp(testUser, inviteCode)

            // Get confirmation code
            val pendingRegistration = transaction {
                PendingRegistrationEntity.find { 
                    (PendingRegistrations.email eq testUser.email)
                }.first()
            }
            val confirmationCode = pendingRegistration.id.value.toString()

            // Confirm registration
            userService.confirmRegistration(confirmationCode)

            assertThrows(UserNotFoundException::class.java) {
                userService.signIn(UserDto.Login("unknown" + testUser.login, testUser.password))
            }
            rollback()
        }
    }

    @Test
    fun `updating user info`() {
        transaction {
            // Get system user to generate invite code
            val systemUserId = userService.getOrCreateSystemUser()
            val inviteCode = userService.generateInviteCode(systemUserId)

            // Create pending registration
            userService.signUp(testUser, inviteCode)

            // Get confirmation code
            val pendingRegistration = transaction {
                PendingRegistrationEntity.find { 
                    (PendingRegistrations.email eq testUser.email)
                }.first()
            }
            val confirmationCode = pendingRegistration.id.value.toString()

            // Confirm registration
            userService.confirmRegistration(confirmationCode)

            val userEntity = findUserByLogin(testUser.login)!!
            val registrationTime = userEntity.registrationTime
            val newUser = UserDto.Registration(testUser.login, "new" + testUser.email, "new" + testUser.password, "new" + testUser.nickname, language = Language.EN, timezone = "Asia/Seoul")
            userService.update(userEntity.id, newUser, testUser.password)

            val updatedUser = findUserByLogin(testUser.login)!!
            assertEquals(testUser.login, updatedUser.login)
            assertEquals(newUser.email, updatedUser.email)
            assertEquals(newUser.nickname, updatedUser.nickname)
            assertTrue(encoder.matches(newUser.password, updatedUser.password))
            assertEquals(registrationTime, updatedUser.registrationTime)

            rollback()
        }
    }

    @Test
    fun `updating email creates pending change and sends confirmation email`() {
        transaction {
            // Get system user to generate invite code
            val systemUserId = userService.getOrCreateSystemUser()
            val inviteCode = userService.generateInviteCode(systemUserId)

            // Create pending registration
            userService.signUp(testUser, inviteCode)

            // Get confirmation code
            val pendingRegistration = transaction {
                PendingRegistrationEntity.find { 
                    (PendingRegistrations.email eq testUser.email)
                }.first()
            }
            val confirmationCode = pendingRegistration.id.value.toString()

            // Confirm registration
            userService.confirmRegistration(confirmationCode)

            // Request email update
            val userEntity = findUserByLogin(testUser.login)!!
            val originalEmail = userEntity.email
            val newEmail = "new" + testUser.email

            // Capture email service calls
            val subjectCaptor = argumentCaptor<String>()
            val textCaptor = argumentCaptor<String>()
            val recipientCaptor = argumentCaptor<String>()

            // Reset and setup mock
            org.mockito.Mockito.reset(mailService)
            org.mockito.Mockito.doNothing().`when`(mailService).sendEmail(subjectCaptor.capture(), textCaptor.capture(), recipientCaptor.capture())

            userService.updateEmail(userEntity.id, newEmail)

            // Verify email is not updated yet
            val userAfterRequest = findUserByLogin(testUser.login)!!
            assertEquals(originalEmail, userAfterRequest.email)

            // Verify confirmation email was sent
            verify(mailService).sendEmail(any(), any(), any())
            assertEquals("Confirm Your Email Change", subjectCaptor.lastValue)
            assertTrue(textCaptor.lastValue.contains("Confirmation code"))
            assertEquals(newEmail, recipientCaptor.lastValue)

            rollback()
        }
    }

    @Test
    fun `confirm email update changes email`() {
        transaction {
            // Get system user to generate invite code
            val systemUserId = userService.getOrCreateSystemUser()
            val inviteCode = userService.generateInviteCode(systemUserId)

            // Create pending registration
            userService.signUp(testUser, inviteCode)

            // Get confirmation code
            val pendingRegistration = transaction {
                PendingRegistrationEntity.find { 
                    (PendingRegistrations.email eq testUser.email)
                }.first()
            }
            val confirmationCode = pendingRegistration.id.value.toString()

            // Confirm registration
            userService.confirmRegistration(confirmationCode)

            // Request email update
            val userEntity = findUserByLogin(testUser.login)!!
            val originalEmail = userEntity.email
            val newEmail = "new" + testUser.email

            // Capture email service calls
            val subjectCaptor = argumentCaptor<String>()
            val textCaptor = argumentCaptor<String>()
            val recipientCaptor = argumentCaptor<String>()

            // Reset and setup mock
            org.mockito.Mockito.reset(mailService)
            org.mockito.Mockito.doNothing().`when`(mailService).sendEmail(subjectCaptor.capture(), textCaptor.capture(), recipientCaptor.capture())

            userService.updateEmail(userEntity.id, newEmail)

            // Get the pending email change confirmation code
            val pendingEmailChange = transaction {
                PendingEmailChangeEntity.find { 
                    PendingEmailChanges.user eq userEntity.id
                }.first()
            }

            val emailChangeConfirmationCode = pendingEmailChange.id.value.toString()

            // Reset captors for the confirmation email
            org.mockito.Mockito.reset(mailService)
            org.mockito.Mockito.doNothing().`when`(mailService).sendEmail(subjectCaptor.capture(), textCaptor.capture(), recipientCaptor.capture())

            // Confirm email update
            userService.confirmEmailUpdate(emailChangeConfirmationCode)

            // Verify email is updated
            val userAfterConfirmation = findUserByLogin(testUser.login)!!
            assertEquals(newEmail, userAfterConfirmation.email)

            // Verify notification email was sent to old address
            verify(mailService).sendEmail(any(), any(), any())
            assertEquals("Your Email Has Been Changed", subjectCaptor.lastValue)
            assertTrue(textCaptor.lastValue.contains("has been successfully changed"))
            assertEquals(originalEmail, recipientCaptor.lastValue)

            // Verify pending email change is deleted
            val pendingEmailChangeAfterConfirmation = transaction {
                PendingEmailChangeEntity.find { 
                    PendingEmailChanges.user eq userEntity.id
                }.firstOrNull()
            }

            assertNull(pendingEmailChangeAfterConfirmation)

            rollback()
        }
    }

    @Test
    fun `confirm email update with invalid code throws exception`() {
        transaction {
            assertThrows(ConfirmationCodeInvalidOrExpiredException::class.java) {
                userService.confirmEmailUpdate("invalid-code")
            }

            rollback()
        }
    }

    @Test
    fun `update nickname changes user nickname`() {
        transaction {
            // Create a user
            val (userId, _) = signUsersUp()

            // Get original nickname
            val originalUser = findUserByLogin(testUser.login)!!
            val originalNickname = originalUser.nickname

            // Update nickname
            val newNickname = "new_nickname"
            userService.updateNickname(userId, newNickname)

            // Verify nickname was updated
            val updatedUser = findUserByLogin(testUser.login)!!
            assertEquals(newNickname, updatedUser.nickname)
            assertNotEquals(originalNickname, updatedUser.nickname)

            rollback()
        }
    }

    @Test
    fun `update nickname with busy nickname throws exception`() {
        transaction {
            // Create two users
            val (userId1, userId2) = signUsersUp()

            // Try to update first user's nickname to second user's nickname
            assertThrows(NicknameIsBusyException::class.java) {
                userService.updateNickname(userId1, testUser2.nickname)
            }

            rollback()
        }
    }

    @Test
    fun `update nickname with non-existent user throws exception`() {
        transaction {
            // Try to update non-existent user's nickname
            val nonExistentUserId = UUID.randomUUID()
            assertThrows(UserNotFoundException::class.java) {
                userService.updateNickname(nonExistentUserId, "new_nickname")
            }

            rollback()
        }
    }

    @Test
    fun `update password changes user password`() {
        transaction {
            // Create a user
            val (userId, _) = signUsersUp()

            // Get original password
            val originalUser = findUserByLogin(testUser.login)!!
            val originalPassword = originalUser.password

            // Update password
            val newPassword = "new_password"
            userService.updatePassword(userId, newPassword, testUser.password)

            // Verify password was updated
            val updatedUser = findUserByLogin(testUser.login)!!
            assertNotEquals(originalPassword, updatedUser.password)
            assertTrue(encoder.matches(newPassword, updatedUser.password))

            rollback()
        }
    }

    @Test
    fun `update password with wrong old password throws exception`() {
        transaction {
            // Create a user
            val (userId, _) = signUsersUp()

            // Try to update password with wrong old password
            assertThrows(WrongPasswordException::class.java) {
                userService.updatePassword(userId, "new_password", "wrong_password")
            }

            rollback()
        }
    }

    @Test
    fun `update password with non-existent user throws exception`() {
        transaction {
            // Try to update non-existent user's password
            val nonExistentUserId = UUID.randomUUID()
            assertThrows(UserNotFoundException::class.java) {
                userService.updatePassword(nonExistentUserId, "new_password", "old_password")
            }

            rollback()
        }
    }

    @Test
    fun `update sex changes user sex`() {
        transaction {
            // Create a user
            val (userId, _) = signUsersUp()

            // Update sex
            userService.updateSex(userId, Sex.MALE)

            // Verify sex was updated in the database
            val userEntity = transaction {
                UserEntity.findById(userId) ?: throw UserNotFoundException()
            }
            assertEquals(Sex.MALE, userEntity.sex)

            rollback()
        }
    }

    @Test
    fun `update sex with non-existent user throws exception`() {
        transaction {
            // Try to update non-existent user's sex
            val nonExistentUserId = UUID.randomUUID()
            assertThrows(UserNotFoundException::class.java) {
                userService.updateSex(nonExistentUserId, Sex.MALE)
            }

            rollback()
        }
    }

    @Test
    fun `update timezone changes user timezone`() {
        transaction {
            // Create a user
            val (userId, _) = signUsersUp()

            // Update timezone
            val newTimezone = "America/New_York"
            userService.updateTimezone(userId, newTimezone)

            // Verify timezone was updated in the database
            val userEntity = transaction {
                UserEntity.findById(userId) ?: throw UserNotFoundException()
            }
            assertEquals(newTimezone, userEntity.timezone)

            rollback()
        }
    }

    @Test
    fun `update timezone with invalid timezone throws exception`() {
        transaction {
            // Create a user
            val (userId, _) = signUsersUp()

            // Try to update timezone with invalid timezone
            assertThrows(InvalidTimezoneException::class.java) {
                userService.updateTimezone(userId, "Invalid/Timezone")
            }

            rollback()
        }
    }

    @Test
    fun `update timezone with non-existent user throws exception`() {
        transaction {
            // Try to update non-existent user's timezone
            val nonExistentUserId = UUID.randomUUID()
            assertThrows(UserNotFoundException::class.java) {
                userService.updateTimezone(nonExistentUserId, "America/New_York")
            }

            rollback()
        }
    }

    @Test
    fun `update language changes user language`() {
        transaction {
            // Create a user
            val (userId, _) = signUsersUp()

            // Update language
            val newLanguage = Language.RU
            userService.updateLanguage(userId, newLanguage)

            // Verify language was updated in the database
            val userEntity = transaction {
                UserEntity.findById(userId) ?: throw UserNotFoundException()
            }
            assertEquals(newLanguage, userEntity.language)

            rollback()
        }
    }

    @Test
    fun `update language with non-existent user throws exception`() {
        transaction {
            // Try to update non-existent user's language
            val nonExistentUserId = UUID.randomUUID()
            assertThrows(UserNotFoundException::class.java) {
                userService.updateLanguage(nonExistentUserId, Language.RU)
            }

            rollback()
        }
    }

    @Test
    fun `update NSFW policy changes user NSFW policy`() {
        transaction {
            // Create a user
            val (userId, _) = signUsersUp()

            // Update NSFW policy
            val newNSFWPolicy = NSFWPolicy.SHOW
            userService.updateNSFWPolicy(userId, newNSFWPolicy)

            // Verify NSFW policy was updated in the database
            val userEntity = transaction {
                UserEntity.findById(userId) ?: throw UserNotFoundException()
            }
            assertEquals(newNSFWPolicy, userEntity.nsfw)

            rollback()
        }
    }

    @Test
    fun `update NSFW policy with non-existent user throws exception`() {
        transaction {
            // Try to update non-existent user's NSFW policy
            val nonExistentUserId = UUID.randomUUID()
            assertThrows(UserNotFoundException::class.java) {
                userService.updateNSFWPolicy(nonExistentUserId, NSFWPolicy.SHOW)
            }

            rollback()
        }
    }

    @Test
    fun `update birth date changes user birth date`() {
        transaction {
            // Create a user
            val (userId, _) = signUsersUp()

            // Update birth date
            val newBirthDate = kotlinx.datetime.LocalDate(1990, 1, 1)
            userService.updateBirthDate(userId, newBirthDate)

            // Verify birth date was updated in the database
            val userEntity = transaction {
                UserEntity.findById(userId) ?: throw UserNotFoundException()
            }
            assertEquals(newBirthDate, userEntity.birthdate)

            rollback()
        }
    }

    @Test
    fun `update birth date with non-existent user throws exception`() {
        transaction {
            // Try to update non-existent user's birth date
            val nonExistentUserId = UUID.randomUUID()
            assertThrows(UserNotFoundException::class.java) {
                userService.updateBirthDate(nonExistentUserId, kotlinx.datetime.LocalDate(1990, 1, 1))
            }

            rollback()
        }
    }

    @Test
    fun `updating only email with old method`() {
        transaction {
            // Get system user to generate invite code
            val systemUserId = userService.getOrCreateSystemUser()
            val inviteCode = userService.generateInviteCode(systemUserId)

            // Create pending registration
            userService.signUp(testUser, inviteCode)

            // Get confirmation code
            val pendingRegistration = transaction {
                PendingRegistrationEntity.find { 
                    (PendingRegistrations.email eq testUser.email)
                }.first()
            }
            val confirmationCode = pendingRegistration.id.value.toString()

            // Confirm registration
            userService.confirmRegistration(confirmationCode)

            val userEntity = findUserByLogin(testUser.login)!!
            val registrationTime = userEntity.registrationTime
            val newUser = UserDto.Registration(testUser.login, "new" + testUser.email, testUser.password, testUser.nickname, language = Language.EN, timezone = "Asia/Seoul")
            userService.update(userEntity.id, newUser, testUser.password)

            val updatedUser = findUserByLogin(testUser.login)!!
            assertEquals(testUser.login, updatedUser.login)
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
            // Get system user to generate invite code
            val systemUserId = userService.getOrCreateSystemUser()
            val inviteCode = userService.generateInviteCode(systemUserId)

            // Create pending registration
            userService.signUp(testUser, inviteCode)

            // Get confirmation code
            val pendingRegistration = transaction {
                PendingRegistrationEntity.find { 
                    (PendingRegistrations.email eq testUser.email)
                }.first()
            }
            val confirmationCode = pendingRegistration.id.value.toString()

            // Confirm registration
            userService.confirmRegistration(confirmationCode)

            val userEntity = findUserByLogin(testUser.login)!!
            val registrationTime = userEntity.registrationTime
            val newUser = UserDto.Registration(testUser.login, testUser.email, testUser.password, "new" + testUser.nickname, language = Language.EN, timezone = "Asia/Seoul")
            userService.update(userEntity.id, newUser, testUser.password)

            val updatedUser = findUserByLogin(testUser.login)!!
            assertEquals(testUser.login, updatedUser.login)
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
            // Get system user to generate invite code
            val systemUserId = userService.getOrCreateSystemUser()
            val inviteCode = userService.generateInviteCode(systemUserId)

            // Create pending registration
            userService.signUp(testUser, inviteCode)

            // Get confirmation code
            val pendingRegistration = transaction {
                PendingRegistrationEntity.find { 
                    (PendingRegistrations.email eq testUser.email)
                }.first()
            }
            val confirmationCode = pendingRegistration.id.value.toString()

            // Confirm registration
            userService.confirmRegistration(confirmationCode)

            val userEntity = findUserByLogin(testUser.login)!!
            val registrationTime = userEntity.registrationTime
            val newUser = UserDto.Registration(testUser.login, testUser.email, testUser.password, "new" + testUser.nickname, language = Language.EN, timezone = "Asia/Seoul")
            assertThrows(WrongPasswordException::class.java) {
                userService.update(userEntity.id, newUser, "wrong" + testUser.password)
            }

            val updatedUser = findUserByLogin(testUser.login)!!
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
            val (userId1, userId2) = signUsersUp()
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
            val (userId1, userId2) = signUsersUp()
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

            // Get system user to generate invite code
            val systemUserId = userService.getOrCreateSystemUser()
            val inviteCode = userService.generateInviteCode(systemUserId)

            // Create pending registration
            userService.signUp(testUser, inviteCode)

            // Get confirmation code
            val pendingRegistration = transaction {
                PendingRegistrationEntity.find { 
                    (PendingRegistrations.email eq testUser.email)
                }.first()
            }
            val confirmationCode = pendingRegistration.id.value.toString()

            // Confirm registration
            userService.confirmRegistration(confirmationCode)

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

            val (userId, _) = signUsersUp()

            userService.addAvatar(userId, listOf(avatarUpload1))
            var avatars = userService.getAvatars(userId)
            assertEquals(1, avatars.size)
            val avatar1 = avatars.last()
            assertEquals(userId, avatar1.ownerId)
            assertEquals(avatarUpload1.type, avatar1.type)

            userService.addAvatar(userId, listOf(avatarUpload2))
            avatars = userService.getAvatars(userId)
            assertEquals(2, avatars.size)
            val avatar2 = avatars.last()
            assertEquals(userId, avatar2.ownerId)
            assertEquals(avatarUpload2.type, avatar2.type)

            userService.addAvatar(userId, listOf(avatarUpload3))
            avatars = userService.getAvatars(userId)
            assertEquals(3, avatars.size)
            val avatar3 = avatars.last()
            assertEquals(userId, avatar3.ownerId)
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
                val resizedImage = java.awt.image.BufferedImage(200, 250, originalImage.type)
                val g = resizedImage.createGraphics()
                g.drawImage(originalImage, 0, 0, 200, 250, null)
                g.dispose()

                val outputStream = java.io.ByteArrayOutputStream()
                ImageIO.write(resizedImage, "png", outputStream)
                java.io.ByteArrayInputStream(outputStream.toByteArray())
            }
            val wrongSizeUpload = FileUploadData(avatarFile1.name, modifiedInputStream)

            val (userId, _) = signUsersUp()

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

            val (userId, _) = signUsersUp()

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

            val (userId, _) = signUsersUp()

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

            val (userId, _) = signUsersUp()

            userService.addAvatar(userId, listOf(avatarUpload1, avatarUpload2, avatarUpload3))
            val avatars = userService.getAvatars(userId)
            assertEquals(3, avatars.size)
            val avatar1 = avatars[0]
            assertEquals(userId, avatar1.ownerId)
            assertEquals(avatarUpload1.type, avatar1.type)

            val avatar2 = avatars[1]
            assertEquals(userId, avatar2.ownerId)
            assertEquals(avatarUpload2.type, avatar2.type)

            val avatar3 = avatars[2]
            assertEquals(userId, avatar3.ownerId)
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

            val (userId, _) = signUsersUp()

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

            val (userId, _) = signUsersUp()

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

            val (userId, _) = signUsersUp()

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

            val (userId, _) = signUsersUp()

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

            val (userId, _) = signUsersUp()

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

            val (userId, _) = signUsersUp()

            userService.addAvatar(userId, listOf(avatarUpload1, avatarUpload2, avatarUpload3))
            assertThrows(InvalidAvatarUriException::class.java) {
                userService.deleteAvatar(userId, "nonexistent uri")
            }
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

            val (userId1, userId2) = signUsersUp()

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

            val (userId1, userId2) = signUsersUp()

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

            // Get system user to generate invite code
            val systemUserId = userService.getOrCreateSystemUser()
            val inviteCode = userService.generateInviteCode(systemUserId)

            // Create pending registration
            userService.signUp(testUser, inviteCode)

            // Get confirmation code
            val pendingRegistration = transaction {
                PendingRegistrationEntity.find { 
                    (PendingRegistrations.email eq testUser.email)
                }.first()
            }
            val confirmationCode = pendingRegistration.id.value.toString()

            // Confirm registration
            userService.confirmRegistration(confirmationCode)

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
            val (userId, _) = signUsersUp()

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
            val (userId, _) = signUsersUp()

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
            val (userId1, userId2) = signUsersUp()

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
            val (userId, _) = signUsersUp()

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
            val (userId1, userId2) = signUsersUp()

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
            val (userId, _) = signUsersUp()

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
            val (userId1, userId2) = signUsersUp()

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
            // Get system user to generate invite code
            val systemUserId = userService.getOrCreateSystemUser()
            val inviteCode = userService.generateInviteCode(systemUserId)

            // Create pending registration
            userService.signUp(testUser, inviteCode)

            // Get confirmation code
            val pendingRegistration = transaction {
                PendingRegistrationEntity.find { 
                    (PendingRegistrations.email eq testUser.email)
                }.first()
            }
            val confirmationCode = pendingRegistration.id.value.toString()

            // Confirm registration
            userService.confirmRegistration(confirmationCode)

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
            val (userId1, userId2) = signUsersUp()

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
            val (userId1, userId2) = signUsersUp()

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
            val (userId1, userId2) = signUsersUp()

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
            val (userId, _) = signUsersUp()

            assertThrows(FriendRequestNotFoundException::class.java) {
                userService.acceptFriendRequest(userId, UUID.randomUUID(), null)
            }

            rollback()
        }
    }

    @Test
    fun `accept friend request as non-recipient`() {
        transaction {
            val (userId1, userId2) = signUsersUp()

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
            val (userId1, userId2) = signUsersUp()

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
            val (userId1, userId2) = signUsersUp()

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
            val (userId1, _) = signUsersUp()

            assertThrows(FriendRequestNotFoundException::class.java) {
                userService.cancelFriendRequest(userId1, UUID.randomUUID())
            }

            rollback()
        }
    }

    @Test
    fun `cancel friend request as non-sender`() {
        transaction {
            val (userId1, userId2) = signUsersUp()

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
            val (userId1, userId2) = signUsersUp()

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
            val (userId1, userId2) = signUsersUp()

            assertThrows(NotFriendsException::class.java) {
                userService.removeFriend(userId1, testUser2.login)
            }

            rollback()
        }
    }

    @Test
    fun `verify friend labels after accepting request`() {
        transaction {
            val (userId1, userId2) = signUsersUp()

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
            val (userId1, userId2) = signUsersUp()

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
            val (userId1, userId2) = signUsersUp()

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

    @Test
    fun `get users by logins`() {
        transaction {
            val (userId1, userId2) = signUsersUp()

            // Get users by their logins
            val users = userService.getByLogins(listOf(testUser.login, testUser2.login))

            // Verify the correct users are returned
            assertEquals(2, users.size)

            // Check first user
            val user1 = users.find { it.login == testUser.login }
            assertNotNull(user1)
            assertEquals(testUser.nickname, user1!!.nickname)

            // Check second user
            val user2 = users.find { it.login == testUser2.login }
            assertNotNull(user2)
            assertEquals(testUser2.nickname, user2!!.nickname)

            rollback()
        }
    }

    @Test
    fun `get users by logins with empty list`() {
        transaction {
            // Get users with empty login list
            val users = userService.getByLogins(emptyList())

            // Verify empty list is returned
            assertEquals(0, users.size)

            rollback()
        }
    }

    @Test
    fun `get users by logins with nonexistent login`() {
        transaction {
            val (userId1, userId2) = signUsersUp()

            // Get users with a mix of existing and nonexistent logins
            val users = userService.getByLogins(listOf(testUser.login, "nonexistent-login"))

            // Verify only existing users are returned
            assertEquals(1, users.size)
            assertEquals(testUser.login, users[0].login)
            assertEquals(testUser.nickname, users[0].nickname)

            rollback()
        }
    }
}
