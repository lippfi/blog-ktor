package fi.lipp.blog.service

import fi.lipp.blog.UnitTestBase
import fi.lipp.blog.data.PostDto
import fi.lipp.blog.domain.DiaryEntity
import fi.lipp.blog.domain.PostEntity
import fi.lipp.blog.repository.Diaries
import fi.lipp.blog.repository.Posts
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class AccessGroupServiceTest : UnitTestBase() {

    @Test
    fun `test deleteAccessGroup updates references`() {
        transaction {
            // Create users
            val users = signUsersUp(2)
            val (user1Id, user1Login) = users[0]
            val (user2Id, _) = users[1]

            // Create a custom access group
            groupService.createAccessGroup(user1Id, user1Login, "test-group")
            val groupsMap = groupService.getAccessGroups(user1Id, user1Login).content
            val testGroupUUID = UUID.fromString(groupsMap["test-group"])
            
            // Create a post that uses the custom access group for all access types
            val post = PostDto.Create(
                uri = "test-post",
                avatar = "test-avatar.jpg",
                title = "Test Post",
                text = "Test content",
                readGroupId = testGroupUUID,
                commentGroupId = testGroupUUID,
                reactionGroupId = testGroupUUID,
                commentReactionGroupId = testGroupUUID,
                tags = setOf("test"),
                classes = "",
                isPreface = false,
                isEncrypted = false
            )
            postService.addPost(user1Id, post)
            
            // Set the diary's default groups to the custom access group
            val diaryEntity = transaction {
                DiaryEntity.find { Diaries.login eq user1Login }.single()
            }
            diaryEntity.defaultReadGroup = org.jetbrains.exposed.dao.id.EntityID(testGroupUUID, Diaries)
            diaryEntity.defaultCommentGroup = org.jetbrains.exposed.dao.id.EntityID(testGroupUUID, Diaries)
            diaryEntity.defaultReactGroup = org.jetbrains.exposed.dao.id.EntityID(testGroupUUID, Diaries)
            
            // Delete the custom access group
            groupService.deleteAccessGroup(user1Id, testGroupUUID)
            
            // Verify that the post's access groups have been updated to use privateGroupUUID
            val postEntity = transaction {
                PostEntity.find { Posts.uri eq "test-post" }.single()
            }
            assertEquals(groupService.privateGroupUUID, postEntity.readGroupId.value)
            assertEquals(groupService.privateGroupUUID, postEntity.commentGroupId.value)
            assertEquals(groupService.privateGroupUUID, postEntity.reactionGroupId.value)
            assertEquals(groupService.privateGroupUUID, postEntity.commentReactionGroupId.value)
            
            // Verify that the diary's default groups have been updated to use privateGroupUUID
            val updatedDiaryEntity = transaction {
                DiaryEntity.find { Diaries.login eq user1Login }.single()
            }
            assertEquals(groupService.privateGroupUUID, updatedDiaryEntity.defaultReadGroup.value)
            assertEquals(groupService.privateGroupUUID, updatedDiaryEntity.defaultCommentGroup.value)
            assertEquals(groupService.privateGroupUUID, updatedDiaryEntity.defaultReactGroup.value)
            
            rollback()
        }
    }
}