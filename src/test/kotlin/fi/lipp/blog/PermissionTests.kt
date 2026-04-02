package fi.lipp.blog

import fi.lipp.blog.data.FileUploadData
import fi.lipp.blog.data.UserDto
import fi.lipp.blog.data.UserPermission
import fi.lipp.blog.domain.PendingRegistrationEntity
import fi.lipp.blog.model.exceptions.SvgUploadNotAllowedException
import fi.lipp.blog.repository.PendingRegistrations
import fi.lipp.blog.service.Viewer
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.test.*
import org.jetbrains.exposed.sql.transactions.transaction

class PermissionTests : UnitTestBase() {
    private lateinit var registeredUser: UserDto.FullProfileInfo

    @BeforeTest
    fun setUp() {
        transaction {
            val systemUserId = userService.getOrCreateSystemUser()
            val inviteCode = userService.generateInviteCode(systemUserId)

            userService.signUp(testUser, inviteCode)

            val pendingRegistration = transaction {
                PendingRegistrationEntity.find {
                    (PendingRegistrations.email eq testUser.email)
                }.first()
            }
            val confirmationCode = pendingRegistration.id.value.toString()

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

    private fun createSvgFile(): FileUploadData {
        val svgContent = """<svg xmlns="http://www.w3.org/2000/svg" width="100" height="100"><rect fill="red" width="100" height="100"/></svg>"""
        return FileUploadData(
            fullName = "test.svg",
            bytes = svgContent.toByteArray()
        )
    }

    @Test
    fun `test new user has no permissions by default`() {
        val permissions = userService.getUserPermissions(registeredUser.id)
        assertTrue(permissions.isEmpty())
    }

    @Test
    fun `test update user permissions`() {
        userService.updateUserPermissions(registeredUser.id, setOf(UserPermission.SVG_UPLOAD))
        val permissions = userService.getUserPermissions(registeredUser.id)
        assertEquals(setOf(UserPermission.SVG_UPLOAD), permissions)
    }

    @Test
    fun `test remove user permissions`() {
        userService.updateUserPermissions(registeredUser.id, setOf(UserPermission.SVG_UPLOAD))
        userService.updateUserPermissions(registeredUser.id, emptySet())
        val permissions = userService.getUserPermissions(registeredUser.id)
        assertTrue(permissions.isEmpty())
    }

    @Test
    fun `test SVG upload blocked without permission`() {
        val svgFile = createSvgFile()
        val viewer = Viewer.Registered(registeredUser.id)
        assertFailsWith<SvgUploadNotAllowedException> {
            storageService.store(viewer, listOf(svgFile))
        }
    }

    @Test
    fun `test SVG upload allowed with permission`() {
        val svgFile = createSvgFile()
        val viewer = Viewer.Registered(registeredUser.id, setOf(UserPermission.SVG_UPLOAD))
        transaction {
            val result = storageService.store(viewer, listOf(svgFile))
            assertEquals(1, result.size)
            assertTrue(result[0].storageKey.endsWith(".svg"))
        }
    }

    @Test
    fun `test non-SVG upload works without permission`() {
        val pngFile = createTestImage(100, 100)
        val viewer = Viewer.Registered(registeredUser.id)
        transaction {
            val result = storageService.store(viewer, listOf(pngFile))
            assertEquals(1, result.size)
        }
    }

    @Test
    fun `test mixed upload with SVG blocked without permission`() {
        val pngFile = createTestImage(100, 100)
        val svgFile = createSvgFile()
        val viewer = Viewer.Registered(registeredUser.id)
        assertFailsWith<SvgUploadNotAllowedException> {
            storageService.store(viewer, listOf(pngFile, svgFile))
        }
    }

    @Test
    fun `test mixed upload with SVG allowed with permission`() {
        val pngFile = createTestImage(100, 100)
        val svgFile = createSvgFile()
        val viewer = Viewer.Registered(registeredUser.id, setOf(UserPermission.SVG_UPLOAD))
        transaction {
            val result = storageService.store(viewer, listOf(pngFile, svgFile))
            assertEquals(2, result.size)
        }
    }
}
