package fi.lipp.blog.service

import java.util.UUID

interface AccessGroupService {
    val privateGroupUUID: UUID
    val everyoneGroupUUID: UUID
    val registeredGroupUUID: UUID

    fun getAccessGroups(userId: Long, diaryId: Long): List<Pair<String, UUID>>
    fun createAccessGroup(userId: Long, diaryId: Long, groupName: String)
    fun deleteAccessGroup(userId: Long, groupId: UUID)

    fun addUserToGroup(userId: Long, memberId: Long, groupId: UUID)
    fun removeUserFromGroup(userId: Long, memberId: Long, groupId: UUID)

    fun inGroup(memberId: Long?, groupId: UUID): Boolean
}