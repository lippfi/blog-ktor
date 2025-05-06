package fi.lipp.blog.service

import java.util.UUID

interface AccessGroupService {
    val privateGroupUUID: UUID
    val everyoneGroupUUID: UUID
    val registeredGroupUUID: UUID
    val friendsGroupUUID: UUID

    fun getAccessGroups(userId: UUID, diaryLogin: String): List<Pair<String, UUID>>
    fun createAccessGroup(userId: UUID, diaryLogin: String, groupName: String)
    fun deleteAccessGroup(userId: UUID, groupId: UUID)

    fun addUserToGroup(userId: UUID, memberLogin: String, groupId: UUID)
    fun removeUserFromGroup(userId: UUID, memberLogin: String, groupId: UUID)

    fun inGroup(viewer: Viewer, groupId: UUID, groupOwner: UUID): Boolean
}
