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
            userService.confirmRegistration(confirmationCode)

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
            inputStream = ByteArrayInputStream(bytes)
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
                inputStream = ByteArrayInputStream(ByteArray(10))
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
                inputStream = ByteArrayInputStream(largeBytes)
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
            val imageStoredFile = storageService.getFile(storedImage)
            assertTrue(imageStoredFile.parentFile.exists(), "Image directory should be created")
            assertTrue(imageStoredFile.parentFile.isDirectory, "Image path should be a directory")
            assertTrue(imageStoredFile.exists(), "Image file should exist")

            // Test reaction storage
            val reactionFile = createTestImage(50, 50)
            val storedReaction = storageService.storeReaction(registeredUser.id, "3.png", reactionFile)
            val reactionStoredFile = storageService.getFile(storedReaction)
            assertTrue(reactionStoredFile.parentFile.exists(), "Reaction directory should be created")
            assertTrue(reactionStoredFile.parentFile.isDirectory, "Reaction path should be a directory")
            assertTrue(reactionStoredFile.exists(), "Reaction file should exist")
        }
    }

    @Test
    fun `test nested directory creation`() {
        transaction {
            val imageFile = createTestImage(100, 100)
            val storedImage = storageService.store(registeredUser.id, listOf(imageFile))[0]
            val imageStoredFile = storageService.getFile(storedImage)

            // Check that all parent directories were created
            var parent = imageStoredFile.parentFile
            while (parent != null && parent.path.contains(testUser.login)) {
                assertTrue(parent.exists(), "Parent directory should exist: ${parent.path}")
                assertTrue(parent.isDirectory, "Parent path should be a directory: ${parent.path}")
                parent = parent.parentFile
            }
        }
    }
}
