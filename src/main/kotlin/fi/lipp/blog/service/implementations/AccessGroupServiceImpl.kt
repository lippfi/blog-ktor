package fi.lipp.blog.service.implementations

import fi.lipp.blog.data.AccessGroupType
import fi.lipp.blog.domain.AccessGroupEntity
import fi.lipp.blog.domain.DiaryEntity
import fi.lipp.blog.model.exceptions.DiaryNotFoundException
import fi.lipp.blog.model.exceptions.InvalidAccessGroupException
import fi.lipp.blog.model.exceptions.WrongUserException
import fi.lipp.blog.repository.AccessGroups
import fi.lipp.blog.repository.CustomGroupUsers
import fi.lipp.blog.repository.Diaries
import fi.lipp.blog.repository.Friends
import fi.lipp.blog.service.AccessGroupService
import fi.lipp.blog.service.Viewer
import fi.lipp.blog.util.SerializableMap
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.*

class AccessGroupServiceImpl : AccessGroupService {
    override val privateGroupUUID: UUID = getOrCreateDefaultGroup(AccessGroupType.PRIVATE)
    override val everyoneGroupUUID: UUID = getOrCreateDefaultGroup(AccessGroupType.EVERYONE)
    override val registeredGroupUUID: UUID = getOrCreateDefaultGroup(AccessGroupType.REGISTERED_USERS)
    override val friendsGroupUUID: UUID = getOrCreateDefaultGroup(AccessGroupType.FRIENDS)

    private val commonGroupList = mutableListOf(
        "everyone" to everyoneGroupUUID,
        "registered" to registeredGroupUUID,
        "private" to privateGroupUUID,
        "friends" to friendsGroupUUID,
    )

    private fun getOrCreateDefaultGroup(groupType: AccessGroupType): UUID {
        if (groupType == AccessGroupType.CUSTOM) throw InvalidAccessGroupException()
        return transaction {
            val groupEntity = AccessGroupEntity.find { AccessGroups.type eq groupType }.firstOrNull()
            if (groupEntity != null) return@transaction groupEntity.id.value

            AccessGroups.insertAndGetId {
                it[diary] = null
                it[name] = groupType.name
                it[type] = groupType
                it[ordinal] = 0
            }.value
        }
    }

    override fun getBasicAccessGroups(): SerializableMap {
        return SerializableMap(commonGroupList.associate { it.first to it.second.toString() })
    }

    override fun getDefaultAccessGroups(userId: UUID, diaryLogin: String): SerializableMap {
        val diaryEntity = transaction { findDiaryByLogin(diaryLogin) }.takeIf { it.owner.value == userId } ?: throw WrongUserException()
        return SerializableMap(
            mapOf(
                "read" to diaryEntity.defaultReadGroup.value.toString(),
                "comment" to diaryEntity.defaultCommentGroup.value.toString(),
                "react" to diaryEntity.defaultReactGroup.value.toString(),
            )
        )
    }

    override fun getAccessGroups(userId: UUID, diaryLogin: String): SerializableMap {
        return transaction {
            val diaryEntity = findDiaryByLogin(diaryLogin)
            if (userId != diaryEntity.owner.value ) throw WrongUserException()

            val diaryGroups = AccessGroupEntity.find { AccessGroups.diary eq diaryEntity.id }.toList()
            val groupMap = (commonGroupList + diaryGroups.map { it.name to it.id.value }).associate { it.first to it.second.toString() }
            SerializableMap(groupMap)
        }
    }

    override fun createAccessGroup(userId: UUID, diaryLogin: String, groupName: String) {
        transaction {
            val diaryEntity = findDiaryByLogin(diaryLogin)
            if (diaryEntity.owner.value != userId) throw WrongUserException()

            val sameNameGroup = AccessGroupEntity.find { (AccessGroups.diary eq diaryEntity.id) and (AccessGroups.name eq groupName) }.firstOrNull()
            if (sameNameGroup != null) throw InvalidAccessGroupException()

            AccessGroups.insert {
                it[diary] = diaryEntity.id
                it[name] = groupName
                it[type] = AccessGroupType.CUSTOM
                it[ordinal] = 0
            }
        }
    }

    override fun deleteAccessGroup(userId: UUID, groupId: UUID) {
        transaction {
            val accessGroupEntity = AccessGroupEntity.findById(groupId) ?: throw InvalidAccessGroupException()
            val diaryId = accessGroupEntity.diaryId?.value ?: throw InvalidAccessGroupException()
            val diaryEntity = DiaryEntity.findById(diaryId) ?: throw DiaryNotFoundException()
            if (userId != diaryEntity.owner.value) throw WrongUserException()

            accessGroupEntity.delete()
        }
    }

    // TODO logging if there was attempt of adding not to custom group
    override fun addUserToGroup(userId: UUID, memberLogin: String, groupId: UUID) {
        transaction {
            if (!(groupId == everyoneGroupUUID || groupId == registeredGroupUUID || groupId == privateGroupUUID || groupId == friendsGroupUUID)) {
                val accessGroupEntity = AccessGroupEntity.findById(groupId) ?: throw InvalidAccessGroupException()
                val diaryId = accessGroupEntity.diaryId?.value ?: throw InvalidAccessGroupException()
                val diaryEntity = DiaryEntity.findById(diaryId)!!
                if (userId != diaryEntity.owner.value) throw WrongUserException()

                val memberId = findUserIdByDiaryLogin(memberLogin)
                if (CustomGroupUsers.select { (CustomGroupUsers.accessGroup eq groupId) and (CustomGroupUsers.member eq memberId) }.count() <= 0) {
                    CustomGroupUsers.insert {
                        it[accessGroup] = groupId
                        it[member] = memberId
                    }
                }
            }
        }
    }

    override fun removeUserFromGroup(userId: UUID, memberLogin: String, groupId: UUID) {
        transaction {
            if (!(groupId == everyoneGroupUUID || groupId == registeredGroupUUID || groupId == privateGroupUUID || groupId == friendsGroupUUID)) {
                val accessGroupEntity = AccessGroupEntity.findById(groupId) ?: throw InvalidAccessGroupException()
                val diaryId = accessGroupEntity.diaryId?.value ?: throw InvalidAccessGroupException()
                val diaryEntity = DiaryEntity.findById(diaryId) ?: throw DiaryNotFoundException()
                if (userId != diaryEntity.owner.value) throw WrongUserException()

                val memberId = findUserIdByDiaryLogin(memberLogin)
                CustomGroupUsers.deleteWhere {
                    (accessGroup eq groupId) and (member eq memberId)
                }
            }
        }
    }

    override fun inGroup(viewer: Viewer, groupId: UUID, groupOwner: UUID): Boolean {
        return when (groupId) {
            everyoneGroupUUID -> true
            registeredGroupUUID -> viewer is Viewer.Registered
            privateGroupUUID -> false
            friendsGroupUUID -> {
                if (viewer is Viewer.Anonymous) {
                    return false
                } else {
                    val memberId = (viewer as Viewer.Registered).userId
                    transaction {
                        Friends.select {
                            ((Friends.user1 eq groupOwner) and (Friends.user2 eq memberId)) or
                            ((Friends.user1 eq memberId) and (Friends.user2 eq groupOwner))
                        }
                    }.count() > 0
                }
            }
            else -> {
                if (viewer is Viewer.Anonymous) {
                    return false
                } else {
                    val memberId = (viewer as Viewer.Registered).userId
                    transaction {
                        CustomGroupUsers.select { (CustomGroupUsers.member eq memberId) and (CustomGroupUsers.accessGroup eq groupId) }.count() > 0
                    }
                }
            }
        }
    }

    @Suppress("UnusedReceiverParameter")
    private fun Transaction.findDiaryByLogin(diaryLogin: String): DiaryEntity {
        return DiaryEntity.find { Diaries.login eq diaryLogin }.singleOrNull() ?: throw DiaryNotFoundException()
    }

    private fun Transaction.findUserIdByDiaryLogin(login: String): UUID {
        val diaryEntity = findDiaryByLogin(login) 
        return diaryEntity.owner.value
    }
}
