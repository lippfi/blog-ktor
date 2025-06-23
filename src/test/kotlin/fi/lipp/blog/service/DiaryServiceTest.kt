package fi.lipp.blog.service

import fi.lipp.blog.UnitTestBase
import fi.lipp.blog.data.*
import fi.lipp.blog.domain.AccessGroupEntity
import fi.lipp.blog.domain.DiaryEntity
import fi.lipp.blog.domain.DiaryStyleEntity
import fi.lipp.blog.domain.DiaryStyleJunctionEntity
import fi.lipp.blog.model.exceptions.DiaryNotFoundException
import fi.lipp.blog.model.exceptions.InvalidAccessGroupException
import fi.lipp.blog.model.exceptions.InvalidStyleException
import fi.lipp.blog.model.exceptions.WrongUserException
import fi.lipp.blog.repository.Diaries
import fi.lipp.blog.repository.DiaryStyleJunctions
import fi.lipp.blog.service.implementations.DiaryServiceImpl
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.koin.core.context.loadKoinModules
import org.koin.dsl.module
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DiaryServiceTest : UnitTestBase() {
    private lateinit var diaryService: DiaryService

    @Before
    fun setUp() {
        // Create DiaryService instance
        diaryService = DiaryServiceImpl(storageService)

        // Register DiaryService in Koin
        loadKoinModules(module {
            single<DiaryService> { diaryService }
        })
    }

    @Test
    fun `updateDiaryInfo updates diary information`() {
        transaction {
            // Create a user and get their diary
            val (userId, _) = signUsersUp()
            val diaryEntity = DiaryEntity.find { Diaries.owner eq userId }.single()
            val diaryLogin = diaryEntity.login

            // Create access groups for the diary
            groupService.createAccessGroup(userId, diaryLogin, "Read Group")
            groupService.createAccessGroup(userId, diaryLogin, "Comment Group")
            groupService.createAccessGroup(userId, diaryLogin, "React Group")

            // Get the created groups
            val groups = groupService.getAccessGroups(userId, diaryLogin)
            val readGroup = UUID.fromString(groups.content["Read Group"])
            val commentGroup = UUID.fromString(groups.content["Comment Group"])
            val reactGroup = UUID.fromString(groups.content["React Group"])

            // Create diary info
            val diaryInfo = UserDto.DiaryInfo(
                name = "Updated Diary Name",
                subtitle = "Updated Diary Subtitle",
                defaultReadGroup = readGroup,
                defaultCommentGroup = commentGroup,
                defaultReactGroup = reactGroup
            )

            // Update diary info
            diaryService.updateDiaryInfo(userId, diaryLogin, diaryInfo)

            // Verify diary info was updated
            val updatedDiary = DiaryEntity.find { Diaries.login eq diaryLogin }.single()
            assertEquals(diaryInfo.name, updatedDiary.name)
            assertEquals(diaryInfo.subtitle, updatedDiary.subtitle)
            assertEquals(diaryInfo.defaultReadGroup, updatedDiary.defaultReadGroup.value)
            assertEquals(diaryInfo.defaultCommentGroup, updatedDiary.defaultCommentGroup.value)
            assertEquals(diaryInfo.defaultReactGroup, updatedDiary.defaultReactGroup.value)

            rollback()
        }
    }

    @Test
    fun `updateDiaryInfo with global access groups`() {
        transaction {
            // Create a user and get their diary
            val (userId, _) = signUsersUp()
            val diaryEntity = DiaryEntity.find { Diaries.owner eq userId }.single()
            val diaryLogin = diaryEntity.login

            // Use global access groups
            val readGroup = AccessGroupEntity.findById(groupService.everyoneGroupUUID)!!
            val commentGroup = AccessGroupEntity.findById(groupService.registeredGroupUUID)!!
            val reactGroup = AccessGroupEntity.findById(groupService.friendsGroupUUID)!!

            // Create diary info
            val diaryInfo = UserDto.DiaryInfo(
                name = "Updated Diary Name",
                subtitle = "Updated Diary Subtitle",
                defaultReadGroup = readGroup.id.value,
                defaultCommentGroup = commentGroup.id.value,
                defaultReactGroup = reactGroup.id.value
            )

            // Update diary info
            diaryService.updateDiaryInfo(userId, diaryLogin, diaryInfo)

            // Verify diary info was updated
            val updatedDiary = DiaryEntity.find { Diaries.login eq diaryLogin }.single()
            assertEquals(diaryInfo.name, updatedDiary.name)
            assertEquals(diaryInfo.subtitle, updatedDiary.subtitle)
            assertEquals(diaryInfo.defaultReadGroup, updatedDiary.defaultReadGroup.value)
            assertEquals(diaryInfo.defaultCommentGroup, updatedDiary.defaultCommentGroup.value)
            assertEquals(diaryInfo.defaultReactGroup, updatedDiary.defaultReactGroup.value)

            rollback()
        }
    }

    @Test
    fun `updateDiaryInfo with wrong user throws exception`() {
        transaction {
            // Create two users
            val (userId1, userId2) = signUsersUp()
            val diaryEntity = DiaryEntity.find { Diaries.owner eq userId1 }.single()
            val diaryLogin = diaryEntity.login

            // Create access groups for the diary
            groupService.createAccessGroup(userId1, diaryLogin, "Read Group")
            groupService.createAccessGroup(userId1, diaryLogin, "Comment Group")
            groupService.createAccessGroup(userId1, diaryLogin, "React Group")

            // Get the created groups
            val groups = groupService.getAccessGroups(userId1, diaryLogin)
            val readGroup = UUID.fromString(groups.content["Read Group"])
            val commentGroup = UUID.fromString(groups.content["Comment Group"])
            val reactGroup = UUID.fromString(groups.content["React Group"])

            // Create diary info
            val diaryInfo = UserDto.DiaryInfo(
                name = "Updated Diary Name",
                subtitle = "Updated Diary Subtitle",
                defaultReadGroup = readGroup,
                defaultCommentGroup = commentGroup,
                defaultReactGroup = reactGroup
            )

            // Try to update diary info with wrong user
            assertThrows(WrongUserException::class.java) {
                diaryService.updateDiaryInfo(userId2, diaryLogin, diaryInfo)
            }

            rollback()
        }
    }

    @Test
    fun `updateDiaryInfo with nonexistent diary throws exception`() {
        transaction {
            // Create a user
            val (userId, _) = signUsersUp()

            // Create diary info
            val diaryInfo = UserDto.DiaryInfo(
                name = "Updated Diary Name",
                subtitle = "Updated Diary Subtitle",
                defaultReadGroup = UUID.randomUUID(),
                defaultCommentGroup = UUID.randomUUID(),
                defaultReactGroup = UUID.randomUUID()
            )

            // Try to update nonexistent diary
            assertThrows(DiaryNotFoundException::class.java) {
                diaryService.updateDiaryInfo(userId, "nonexistent-diary", diaryInfo)
            }

            rollback()
        }
    }

    @Test
    fun `updateDiaryInfo with invalid access group throws exception`() {
        transaction {
            // Create a user and get their diary
            val (userId, _) = signUsersUp()
            val diaryEntity = DiaryEntity.find { Diaries.owner eq userId }.single()
            val diaryLogin = diaryEntity.login

            // Create diary info with invalid access groups
            val diaryInfo = UserDto.DiaryInfo(
                name = "Updated Diary Name",
                subtitle = "Updated Diary Subtitle",
                defaultReadGroup = UUID.randomUUID(),
                defaultCommentGroup = UUID.randomUUID(),
                defaultReactGroup = UUID.randomUUID()
            )

            // Try to update diary info with invalid access groups
            assertThrows(InvalidAccessGroupException::class.java) {
                diaryService.updateDiaryInfo(userId, diaryLogin, diaryInfo)
            }

            rollback()
        }
    }

    @Test
    fun `updateDiaryName updates diary name`() {
        transaction {
            // Create a user and get their diary
            val (userId, _) = signUsersUp()
            val diaryEntity = DiaryEntity.find { Diaries.owner eq userId }.single()
            val diaryLogin = diaryEntity.login

            // Update diary name
            val newName = "New Diary Name"
            diaryService.updateDiaryName(userId, diaryLogin, newName)

            // Verify name was updated
            val updatedDiary = DiaryEntity.find { Diaries.login eq diaryLogin }.single()
            assertEquals(newName, updatedDiary.name)

            rollback()
        }
    }

    @Test
    fun `updateDiarySubtitle updates diary subtitle`() {
        transaction {
            // Create a user and get their diary
            val (userId, _) = signUsersUp()
            val diaryEntity = DiaryEntity.find { Diaries.owner eq userId }.single()
            val diaryLogin = diaryEntity.login

            // Update diary subtitle
            val newSubtitle = "New Diary Subtitle"
            diaryService.updateDiarySubtitle(userId, diaryLogin, newSubtitle)

            // Verify subtitle was updated
            val updatedDiary = DiaryEntity.find { Diaries.login eq diaryLogin }.single()
            assertEquals(newSubtitle, updatedDiary.subtitle)

            rollback()
        }
    }

    @Test
    fun `updateDiaryDefaultReadGroup updates default read group`() {
        transaction {
            // Create a user and get their diary
            val (userId, _) = signUsersUp()
            val diaryEntity = DiaryEntity.find { Diaries.owner eq userId }.single()
            val diaryLogin = diaryEntity.login

            // Create a new access group
            groupService.createAccessGroup(userId, diaryLogin, "New Read Group")

            // Get the created group
            val groups = groupService.getAccessGroups(userId, diaryLogin)
            val newGroup = UUID.fromString(groups.content["New Read Group"])

            // Update default read group
            diaryService.updateDiaryDefaultReadGroup(userId, diaryLogin, newGroup)

            // Verify default read group was updated
            val updatedDiary = DiaryEntity.find { Diaries.login eq diaryLogin }.single()
            assertEquals(newGroup, updatedDiary.defaultReadGroup.value)

            rollback()
        }
    }

    @Test
    fun `updateDiaryDefaultCommentGroup updates default comment group`() {
        transaction {
            // Create a user and get their diary
            val (userId, _) = signUsersUp()
            val diaryEntity = DiaryEntity.find { Diaries.owner eq userId }.single()
            val diaryLogin = diaryEntity.login

            // Create a new access group
            groupService.createAccessGroup(userId, diaryLogin, "New Comment Group")

            // Get the created group
            val groups = groupService.getAccessGroups(userId, diaryLogin)
            val newGroup = UUID.fromString(groups.content["New Comment Group"])

            // Update default comment group
            diaryService.updateDiaryDefaultCommentGroup(userId, diaryLogin, newGroup)

            // Verify default comment group was updated
            val updatedDiary = DiaryEntity.find { Diaries.login eq diaryLogin }.single()
            assertEquals(newGroup, updatedDiary.defaultCommentGroup.value)

            rollback()
        }
    }

    @Test
    fun `updateDiaryDefaultReactGroup updates default react group`() {
        transaction {
            // Create a user and get their diary
            val (userId, _) = signUsersUp()
            val diaryEntity = DiaryEntity.find { Diaries.owner eq userId }.single()
            val diaryLogin = diaryEntity.login

            // Create a new access group
            groupService.createAccessGroup(userId, diaryLogin, "New React Group")

            // Get the created group
            val groups = groupService.getAccessGroups(userId, diaryLogin)
            val newGroup = UUID.fromString(groups.content["New React Group"])

            // Update default react group
            diaryService.updateDiaryDefaultReactGroup(userId, diaryLogin, newGroup)

            // Verify default react group was updated
            val updatedDiary = DiaryEntity.find { Diaries.login eq diaryLogin }.single()
            assertEquals(newGroup, updatedDiary.defaultReactGroup.value)

            rollback()
        }
    }

    @Test
    fun `update methods with global access groups`() {
        transaction {
            // Create a user and get their diary
            val (userId, _) = signUsersUp()
            val diaryEntity = DiaryEntity.find { Diaries.owner eq userId }.single()
            val diaryLogin = diaryEntity.login

            // Use global access groups
            val everyoneGroup = AccessGroupEntity.findById(groupService.everyoneGroupUUID)!!
            val registeredGroup = AccessGroupEntity.findById(groupService.registeredGroupUUID)!!
            val friendsGroup = AccessGroupEntity.findById(groupService.friendsGroupUUID)!!

            // Update default groups
            diaryService.updateDiaryDefaultReadGroup(userId, diaryLogin, everyoneGroup.id.value)
            diaryService.updateDiaryDefaultCommentGroup(userId, diaryLogin, registeredGroup.id.value)
            diaryService.updateDiaryDefaultReactGroup(userId, diaryLogin, friendsGroup.id.value)

            // Verify default groups were updated
            val updatedDiary = DiaryEntity.find { Diaries.login eq diaryLogin }.single()
            assertEquals(everyoneGroup.id.value, updatedDiary.defaultReadGroup.value)
            assertEquals(registeredGroup.id.value, updatedDiary.defaultCommentGroup.value)
            assertEquals(friendsGroup.id.value, updatedDiary.defaultReactGroup.value)

            rollback()
        }
    }

    // New tests for multiple styles functionality

    @Test
    fun `addDiaryStyle adds a new style`() {
        transaction {
            // Create a user and get their diary
            val (userId, _) = signUsersUp()
            val diaryLogin = DiaryEntity.find { Diaries.owner eq userId }.single().login
            val diaryEntity = DiaryEntity.find { Diaries.login eq diaryLogin }.single()

            // Create a style
            val styleCreate = DiaryStyleCreate(
                name = "Test Style",
                description = "Test style description",
                styleContent = "body { background-color: #f0f0f0; }",
                enabled = true
            )

            // Add the style
            val createdStyle = diaryService.addDiaryStyle(userId, diaryLogin, styleCreate)

            // Verify style was created
            assertNotNull(createdStyle)
            assertEquals(styleCreate.name, createdStyle.name)
            assertEquals(styleCreate.enabled, createdStyle.enabled)
            assertNotNull(createdStyle.styleContent)

            // Verify style exists in database
            val styleEntity = DiaryStyleEntity.findById(createdStyle.id)
            assertNotNull(styleEntity)
            assertEquals(styleCreate.name, styleEntity!!.name)

            // Verify junction exists in database
            val junction = DiaryStyleJunctionEntity.find { 
                DiaryStyleJunctions.diary eq diaryEntity.id 
            }.firstOrNull { it.style.id == styleEntity.id }

            assertNotNull(junction)
            assertEquals(0, junction!!.ordinal)
            assertEquals(styleCreate.enabled, junction.enabled)
            assertEquals(diaryEntity.id, junction.diary.id)
            assertEquals(styleEntity.id, junction.style.id)

            rollback()
        }
    }

    @Test
    fun `addDiaryStyle with styleId adds an existing style to a diary`() {
        transaction {
            // Create two users and get their diaries
            val (userId1, userId2) = signUsersUp()
            val diaryLogin1 = DiaryEntity.find { Diaries.owner eq userId1 }.single().login
            val diaryLogin2 = DiaryEntity.find { Diaries.owner eq userId2 }.single().login
            val diaryEntity1 = DiaryEntity.find { Diaries.login eq diaryLogin1 }.single()
            val diaryEntity2 = DiaryEntity.find { Diaries.login eq diaryLogin2 }.single()

            // Create a style for the first diary
            val styleCreate = DiaryStyleCreate(
                name = "Shared Style",
                description = "Test style description",
                styleContent = "body { background-color: #f0f0f0; }",
                enabled = true
            )

            // Add the style to the first diary
            val createdStyle = diaryService.addDiaryStyle(userId1, diaryLogin1, styleCreate)
            val styleId = createdStyle.id

            // Add the same style to the second diary
            val addedStyle = diaryService.addDiaryStyle(userId2, diaryLogin2, styleId, true)

            // Verify style was added to the second diary
            assertNotNull(addedStyle)
            assertEquals(styleId, addedStyle.id)
            assertEquals(styleCreate.name, addedStyle.name)
            assertTrue(addedStyle.enabled) // Should be enabled by default

            // Verify style exists in database
            val styleEntity = DiaryStyleEntity.findById(styleId)
            assertNotNull(styleEntity)

            // Verify junction exists for the first diary
            val junction1 = DiaryStyleJunctionEntity.find { 
                DiaryStyleJunctions.diary eq diaryEntity1.id 
            }.firstOrNull { it.style.id == styleEntity!!.id }

            assertNotNull(junction1)

            // Verify junction exists for the second diary
            val junction2 = DiaryStyleJunctionEntity.find { 
                DiaryStyleJunctions.diary eq diaryEntity2.id 
            }.firstOrNull { it.style.id == styleEntity!!.id }

            assertNotNull(junction2)

            // Verify the style is shared between both diaries
            assertEquals(styleEntity!!.id, junction1!!.style.id)
            assertEquals(styleEntity.id, junction2!!.style.id)

            rollback()
        }
    }

    @Test
    fun `getDiaryStyles returns all styles for a diary`() {
        transaction {
            // Create a user and get their diary
            val (userId, _) = signUsersUp()
            val diaryEntity = DiaryEntity.find { Diaries.owner eq userId }.single()
            val diaryLogin = diaryEntity.login

            // Create multiple styles
            val style1 = DiaryStyleCreate(
                name = "Style 1",
                description = "Style 1 description",
                styleContent = "body { color: red; }",
                enabled = true
            )
            val style2 = DiaryStyleCreate(
                name = "Style 2",
                description = "Style 2 description",
                styleContent = "body { color: blue; }",
                enabled = true
            )
            val style3 = DiaryStyleCreate(
                name = "Style 3",
                description = "Style 3 description",
                styleContent = "body { color: green; }",
                enabled = true
            )

            diaryService.addDiaryStyle(userId, diaryLogin, style1)
            diaryService.addDiaryStyle(userId, diaryLogin, style2)
            diaryService.addDiaryStyle(userId, diaryLogin, style3)

            // Get all styles
            val styles = diaryService.getDiaryStyleCollection(userId, diaryLogin)

            // Verify styles were returned
            assertEquals(3, styles.size)
            assertEquals("Style 1", styles[0].name)
            assertEquals("Style 2", styles[1].name)
            assertEquals("Style 3", styles[2].name)

            rollback()
        }
    }

    @Test
    fun `getDiaryStyle returns a specific style`() {
        transaction {
            // Create a user and get their diary
            val (userId, _) = signUsersUp()
            val diaryEntity = DiaryEntity.find { Diaries.owner eq userId }.single()
            val diaryLogin = diaryEntity.login

            // Create a style
            val styleCreate = DiaryStyleCreate(
                name = "Test Style",
                description = "Test style description",
                styleContent = "body { color: red; }",
                enabled = true
            )
            val createdStyle = diaryService.addDiaryStyle(userId, diaryLogin, styleCreate)

            // Get all styles and find the specific one
            val styles = diaryService.getDiaryStyleCollection(userId, diaryLogin)
            val style = styles.find { it.id == createdStyle.id }

            // Verify style was returned
            assertNotNull(style)
            assertEquals(createdStyle.id, style!!.id)
            assertEquals(createdStyle.name, style.name)

            rollback()
        }
    }

    @Test
    fun `updateDiaryStyle updates a style`() {
        transaction {
            // Create a user and get their diary
            val (userId, _) = signUsersUp()
            val diaryEntity = DiaryEntity.find { Diaries.owner eq userId }.single()
            val diaryLogin = diaryEntity.login

            // Create a style
            val styleCreate = DiaryStyleCreate(
                name = "Test Style",
                description = "Test style description",
                styleContent = "body { color: red; }",
                enabled = true
            )
            val createdStyle = diaryService.addDiaryStyle(userId, diaryLogin, styleCreate)

            // Update the style
            val styleUpdate = DiaryStyleUpdate(
                id = createdStyle.id,
                name = "Updated Style",
                description = "Updated description",
                styleContent = "body { color: blue; }",
                enabled = false,
            )

            val updatedStyle = diaryService.updateDiaryStyle(userId, diaryLogin, styleUpdate)

            // Verify style was updated
            assertNotNull(updatedStyle)
            // ID should be different because a new entity is created
            assertEquals(styleUpdate.name, updatedStyle!!.name)
            assertEquals(false, updatedStyle.enabled)

            rollback()
        }
    }

    @Test
    fun `deleteDiaryStyle deletes a style`() {
        transaction {
            // Create a user and get their diary
            val (userId, _) = signUsersUp()
            val diaryEntity = DiaryEntity.find { Diaries.owner eq userId }.single()
            val diaryLogin = diaryEntity.login

            // Create a style
            val styleCreate = DiaryStyleCreate(
                name = "Test Style",
                description = "Test style description",
                styleContent = "body { color: red; }",
                enabled = true
            )
            val createdStyle = diaryService.addDiaryStyle(userId, diaryLogin, styleCreate)

            // Delete the style
            val deleted = diaryService.deleteDiaryStyle(userId, createdStyle.id)

            // Verify style was deleted
            assertTrue(deleted)

            // Verify style no longer exists
            val styles = diaryService.getDiaryStyleCollection(userId, diaryLogin)
            assertEquals(0, styles.size)

            rollback()
        }
    }

    @Test
    fun `reorderDiaryStyles reorders styles`() {
        transaction {
            // Create a user and get their diary
            val (userId, _) = signUsersUp()
            val diaryEntity = DiaryEntity.find { Diaries.owner eq userId }.single()
            val diaryLogin = diaryEntity.login

            // Create multiple styles
            val style1 = diaryService.addDiaryStyle(userId, diaryLogin, DiaryStyleCreate(
                name = "Style 1",
                description = "Style 1 description",
                styleContent = "body { color: red; }",
                enabled = true,
            ))
            val style2 = diaryService.addDiaryStyle(userId, diaryLogin, DiaryStyleCreate(
                name = "Style 2",
                description = "Style 2 description",
                styleContent = "body { color: blue; }",
                enabled = true,
            ))
            val style3 = diaryService.addDiaryStyle(userId, diaryLogin, DiaryStyleCreate(
                name = "Style 3",
                description = "Style 3 description",
                styleContent = "body { color: green; }",
                enabled = true,
            ))

            // Reorder styles (reverse order)
            val reorderedStyles = diaryService.reorderDiaryStyles(userId, diaryLogin, listOf(style3.id, style2.id, style1.id))

            // Verify styles were reordered
            assertEquals(3, reorderedStyles.size)
            assertEquals("Style 3", reorderedStyles[0].name)
            assertEquals("Style 2", reorderedStyles[1].name)
            assertEquals("Style 1", reorderedStyles[2].name)

            // No need to verify ordinals as they are not part of the DiaryStyle class anymore

            rollback()
        }
    }

    @Test
    fun `reorderDiaryStyles with invalid style throws exception`() {
        transaction {
            // Create a user and get their diary
            val (userId, _) = signUsersUp()
            val diaryEntity = DiaryEntity.find { Diaries.owner eq userId }.single()
            val diaryLogin = diaryEntity.login

            // Create multiple styles
            val style1 = diaryService.addDiaryStyle(userId, diaryLogin, DiaryStyleCreate(
                name = "Style 1",
                description = "Style 1 description",
                styleContent = "body { color: red; }",
                enabled = true,
            ))
            val style2 = diaryService.addDiaryStyle(userId, diaryLogin, DiaryStyleCreate(
                name = "Style 2",
                description = "Style 2 description",
                styleContent = "body { color: blue; }",
                enabled = true,
            ))

            // Try to reorder with invalid style ID
            assertThrows(InvalidStyleException::class.java) {
                diaryService.reorderDiaryStyles(userId, diaryLogin, listOf(style1.id, style2.id, UUID.randomUUID()))
            }

            rollback()
        }
    }

    @Test
    fun `reorderDiaryStyles with missing style throws exception`() {
        transaction {
            // Create a user and get their diary
            val (userId, _) = signUsersUp()
            val diaryEntity = DiaryEntity.find { Diaries.owner eq userId }.single()
            val diaryLogin = diaryEntity.login

            // Create multiple styles
            val style1 = diaryService.addDiaryStyle(userId, diaryLogin, DiaryStyleCreate(
                name = "Style 1",
                description = "Style 1 description",
                styleContent = "body { color: red; }",
                enabled = true,
            ))
            val style2 = diaryService.addDiaryStyle(userId, diaryLogin, DiaryStyleCreate(
                name = "Style 2",
                description = "Style 2 description",
                styleContent = "body { color: blue; }",
                enabled = true,
            ))
            val style3 = diaryService.addDiaryStyle(userId, diaryLogin, DiaryStyleCreate(
                name = "Style 3",
                description = "Style 3 description",
                styleContent = "body { color: green; }",
                enabled = true,
            ))

            // Try to reorder with missing style
            assertThrows(InvalidStyleException::class.java) {
                diaryService.reorderDiaryStyles(userId, diaryLogin, listOf(style1.id, style2.id))
            }

            rollback()
        }
    }
}
