package fi.lipp.blog.service.implementations

import fi.lipp.blog.data.AccessGroupType
import fi.lipp.blog.domain.AccessGroupEntity
import fi.lipp.blog.domain.DiaryEntity
import fi.lipp.blog.model.exceptions.DiaryNotFoundException
import fi.lipp.blog.model.exceptions.InvalidAccessGroupException
import fi.lipp.blog.model.exceptions.WrongUserException
import fi.lipp.blog.repository.AccessGroups
import fi.lipp.blog.repository.CustomGroupUsers
import fi.lipp.blog.service.AccessGroupService
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.*

class AccessGroupServiceImpl : AccessGroupService {
    override val privateGroupUUID: UUID = getOrCreateDefaultGroup(AccessGroupType.PRIVATE)
    override val everyoneGroupUUID: UUID = getOrCreateDefaultGroup(AccessGroupType.EVERYONE)
    override val registeredGroupUUID: UUID = getOrCreateDefaultGroup(AccessGroupType.REGISTERED_USERS)

    private val commonGroupList = mutableListOf(
        "everyone" to everyoneGroupUUID,
        "registered users" to registeredGroupUUID,
        "private" to privateGroupUUID,
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

    override fun getAccessGroups(userId: Long, diaryId: Long): List<Pair<String, UUID>> {
        return transaction {
            val diaryEntity = DiaryEntity.findById(diaryId)!!
            if (userId != diaryEntity.owner.value ) throw WrongUserException()

            val diaryGroups = AccessGroupEntity.find { AccessGroups.diary eq diaryId }.toList()
            commonGroupList + diaryGroups.map { it.name to it.id.value }
        }
    }

    override fun createAccessGroup(userId: Long, diaryId: Long, groupName: String) {
        transaction {
            val diaryEntity = DiaryEntity.findById(diaryId) ?: throw DiaryNotFoundException()
            if (diaryEntity.owner.value != userId) throw WrongUserException()

            val sameNameGroup = AccessGroupEntity.find { (AccessGroups.diary eq diaryId) and (AccessGroups.name eq groupName) }.firstOrNull()
            if (sameNameGroup != null) throw InvalidAccessGroupException()

            AccessGroups.insert {
                it[diary] = diaryId
                it[name] = groupName
                it[type] = AccessGroupType.CUSTOM
                it[ordinal] = 0
            }
        }
    }

    override fun deleteAccessGroup(userId: Long, groupId: UUID) {
        TODO("Not yet implemented")
    }

    override fun addUserToGroup(userId: Long, memberId: Long, groupId: UUID) {
        transaction {
            val accessGroupEntity = AccessGroupEntity.findById(groupId) ?: throw InvalidAccessGroupException()
            val diaryId = accessGroupEntity.diaryId?.value ?: throw InvalidAccessGroupException()
            val diaryEntity = DiaryEntity.findById(diaryId)!!
            if (userId != diaryEntity.owner.value) throw WrongUserException()

            if (CustomGroupUsers.select { (CustomGroupUsers.accessGroup eq groupId) and (CustomGroupUsers.member eq memberId) }.count() <= 0) {
                CustomGroupUsers.insert {
                    it[accessGroup] = groupId
                    it[member] = memberId
                }
            }
        }
    }

    override fun removeUserFromGroup(userId: Long, memberId: Long, groupId: UUID) {
        TODO("Not yet implemented")
    }

    override fun inGroup(memberId: Long?, groupId: UUID): Boolean {
        return when (groupId) {
            everyoneGroupUUID -> true
            registeredGroupUUID -> memberId != null
            privateGroupUUID -> false
            else -> {
                memberId != null && transaction {
                    CustomGroupUsers.select { (CustomGroupUsers.member eq memberId) and (CustomGroupUsers.accessGroup eq groupId) }.count() >= 0
                }
            }
        }
    }
}