package fi.lipp.blog.service

import java.util.UUID

interface AccessGroupService {
    val privateGroupUUID: UUID
    val everyoneGroupUUID: UUID
    val registeredGroupUUID: UUID

    fun getAccessGroups(userId: UUID, diaryId: UUID): List<Pair<String, UUID>>
    fun createAccessGroup(userId: UUID, diaryId: UUID, groupName: String)
    fun deleteAccessGroup(userId: UUID, groupId: UUID)

    fun addUserToGroup(userId: UUID, memberId: UUID, groupId: UUID)
    fun removeUserFromGroup(userId: UUID, memberId: UUID, groupId: UUID)

    fun inGroup(memberId: UUID?, groupId: UUID): Boolean
}