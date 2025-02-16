package fi.lipp.blog

import fi.lipp.blog.data.FileType
import fi.lipp.blog.data.FileUploadData
import fi.lipp.blog.data.UserDto
import fi.lipp.blog.model.exceptions.InvalidReactionImageException
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.test.*
import org.jetbrains.exposed.sql.transactions.transaction

class StorageServiceTests : UnitTestBase() {
    private lateinit var registeredUser: UserDto.FullProfileInfo

    @BeforeTest
    fun setUp() {
        transaction {
            userService.signUp(testUser, "")
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
            val blogFile = storageService.storeReaction(registeredUser.id, file)
            assertEquals(FileType.REACTION, blogFile.type)
        }
    }

    @Test
    fun `test store reaction - non-square image`() {
        transaction {
            val file = createTestImage(100, 90)
            assertFailsWith<InvalidReactionImageException> {
                storageService.storeReaction(registeredUser.id, file)
            }
        }
    }

    @Test
    fun `test store reaction - too large dimensions`() {
        transaction {
            val file = createTestImage(101, 101)
            assertFailsWith<InvalidReactionImageException> {
                storageService.storeReaction(registeredUser.id, file)
            }
        }
    }

    @Test
    fun `test store reaction - invalid extension`() {
        transaction {
            val file = FileUploadData(
                fullName = "test.txt",
                inputStream = ByteArrayInputStream(ByteArray(10))
            )
            assertFailsWith<InvalidReactionImageException> {
                storageService.storeReaction(registeredUser.id, file)
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
                storageService.storeReaction(registeredUser.id, largeFile)
            }
        }
    }
}
