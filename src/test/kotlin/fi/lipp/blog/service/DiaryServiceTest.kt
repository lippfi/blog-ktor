package fi.lipp.blog.service

import fi.lipp.blog.UnitTestBase
import fi.lipp.blog.data.BlogFile
import fi.lipp.blog.data.FileUploadData
import fi.lipp.blog.data.UserDto
import fi.lipp.blog.domain.AccessGroupEntity
import fi.lipp.blog.domain.DiaryEntity
import fi.lipp.blog.domain.FileEntity
import fi.lipp.blog.model.exceptions.DiaryNotFoundException
import fi.lipp.blog.model.exceptions.InvalidAccessGroupException
import fi.lipp.blog.model.exceptions.WrongUserException
import fi.lipp.blog.repository.Diaries
import fi.lipp.blog.repository.Files
import fi.lipp.blog.service.implementations.DiaryServiceImpl
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.koin.core.context.loadKoinModules
import org.koin.dsl.module
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.ByteArrayInputStream
import java.io.File
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

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
            val readGroup = groups.find { it.first == "Read Group" }!!.second
            val commentGroup = groups.find { it.first == "Comment Group" }!!.second
            val reactGroup = groups.find { it.first == "React Group" }!!.second

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
            val readGroup = groups.find { it.first == "Read Group" }!!.second
            val commentGroup = groups.find { it.first == "Comment Group" }!!.second
            val reactGroup = groups.find { it.first == "React Group" }!!.second

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
    fun `setDiaryStyle and getDiaryStyle work correctly`() {
        transaction {
            // Create a user
            val (userId, _) = signUsersUp()

            // Set diary style
            val styleContent = "body { background-color: #f0f0f0; }"
            diaryService.setDiaryStyle(userId, styleContent)

            // Get diary login
            val diaryEntity = DiaryEntity.find { Diaries.owner eq userId }.single()
            val diaryLogin = diaryEntity.login

            // Get diary style
            val retrievedStyle = diaryService.getDiaryStyle(diaryLogin)

            // Verify style was set correctly
            assertEquals(styleContent, retrievedStyle)

            rollback()
        }
    }

    @Test
    fun `getDiaryStyleFile returns correct URL`() {
        transaction {
            // Create a user
            val (userId, _) = signUsersUp()

            // Set diary style
            val styleContent = "body { background-color: #f0f0f0; }"
            diaryService.setDiaryStyle(userId, styleContent)

            // Get diary login
            val diaryEntity = DiaryEntity.find { Diaries.owner eq userId }.single()
            val diaryLogin = diaryEntity.login

            // Get diary style file URL
            val styleFileURL = diaryService.getDiaryStyleFile(diaryLogin)

            // Verify URL is not null
            assertNotNull(styleFileURL)

            rollback()
        }
    }

    @Test
    fun `getDiaryStyle for nonexistent diary throws exception`() {
        transaction {
            // Try to get style for nonexistent diary
            assertThrows(DiaryNotFoundException::class.java) {
                diaryService.getDiaryStyle("nonexistent-diary")
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
            val newGroup = groups.find { it.first == "New Read Group" }!!.second

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
            val newGroup = groups.find { it.first == "New Comment Group" }!!.second

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
            val newGroup = groups.find { it.first == "New React Group" }!!.second

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
}
