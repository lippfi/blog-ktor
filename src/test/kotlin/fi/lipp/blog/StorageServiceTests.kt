package fi.lipp.blog

import fi.lipp.blog.data.FileType
import fi.lipp.blog.data.FileUploadData
import fi.lipp.blog.data.UserDto
import fi.lipp.blog.domain.PendingRegistrationEntity
import fi.lipp.blog.model.exceptions.InvalidReactionImageException
import fi.lipp.blog.repository.PendingRegistrations
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.test.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq

class StorageServiceTests : UnitTestBase() {
    private lateinit var registeredUser: UserDto.FullProfileInfo

    @BeforeTest
    fun setUp() {
        transaction {
            // Get system user to generate invite code
            val systemUserId = userService.getOrCreateSystemUser()
            val inviteCode = userService.generateInviteCode(systemUserId)

            // Sign up test user with invite code
            userService.signUp(testUser, inviteCode)

            // Get confirmation code for the user
            val pendingRegistration = transaction {
                PendingRegistrationEntity.find { 
                    (PendingRegistrations.email eq testUser.email)
                }.first()
            }
            val confirmationCode = pendingRegistration.id.value.toString()

            // Confirm registration for the user
            userService.confirmRegistration(confirmationCode, "test-device", "127.0.0.1", false)

            registeredUser = findUserByLogin(testUser.login)!!
        }
    }

    private fun createTestImage(width: Int, height: Int, format: String = "png"): FileUploadData {
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val outputStream = ByteArrayOutputStream()
        ImageIO.write(image, format, outputStream)
        val bytes = outputStream.toByteArray()

        return FileUploadData(
            fullName = "test.$format",
            bytes = bytes
        )
    }

    @Test
    fun `test store reaction - valid square image`() {
        transaction {
            val file = createTestImage(100, 100)
            val blogFile = storageService.storeReaction(registeredUser.id, "test.jpg", file)
            assertEquals(FileType.REACTION, blogFile.type)
        }
    }

//    @Test
//    fun `test store reaction - non-square image`() {
//        transaction {
//            val file = createTestImage(100, 90)
//            assertFailsWith<InvalidReactionImageException> {
//                storageService.storeReaction(registeredUser.id, file)
//            }
//        }
//    }

//    @Test
//    fun `test store reaction - too large dimensions`() {
//        transaction {
//            val file = createTestImage(101, 101)
//            assertFailsWith<InvalidReactionImageException> {
//                storageService.storeReaction(registeredUser.id, file)
//            }
//        }
//    }

    @Test
    fun `test store reaction - invalid extension`() {
        transaction {
            val file = FileUploadData(
                fullName = "test.txt",
                bytes = ByteArray(10)
            )
            assertFailsWith<InvalidReactionImageException> {
                storageService.storeReaction(registeredUser.id, "1.png",file)
            }
        }
    }

    @Test
    fun `test store reaction - file too large`() {
        transaction {
            // Create a large byte array (600KB)
            val largeBytes = ByteArray(600 * 1024)
            val largeFile = FileUploadData(
                fullName = "test.png",
                bytes = largeBytes
            )
            assertFailsWith<InvalidReactionImageException> {
                storageService.storeReaction(registeredUser.id, "2.png", largeFile)
            }
        }
    }

    @Test
    fun `test directory creation for different file types`() {
        transaction {
            // Test image storage
            val imageFile = createTestImage(100, 100)
            val storedImage = storageService.store(registeredUser.id, listOf(imageFile))[0]
            storageService.openFileStream(storedImage).use { stream ->
                val bytes = stream.readBytes()
                assertTrue(bytes.isNotEmpty(), "Image file should have content")
            }

            // Test reaction storage
            val reactionFile = createTestImage(50, 50)
            val storedReaction = storageService.storeReaction(registeredUser.id, "3.png", reactionFile)
            storageService.openFileStream(storedReaction).use { stream ->
                val bytes = stream.readBytes()
                assertTrue(bytes.isNotEmpty(), "Reaction file should have content")
            }
        }
    }

    @Test
    fun `test nested directory creation`() {
        transaction {
            val imageFile = createTestImage(100, 100)
            val storedImage = storageService.store(registeredUser.id, listOf(imageFile))[0]

            // Verify the file is accessible via stream
            storageService.openFileStream(storedImage).use { stream ->
                val bytes = stream.readBytes()
                assertTrue(bytes.isNotEmpty(), "Stored file should be readable and non-empty")
            }
        }
    }
}
