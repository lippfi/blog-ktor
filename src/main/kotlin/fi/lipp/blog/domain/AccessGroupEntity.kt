package fi.lipp.blog.domain

import fi.lipp.blog.repository.AccessGroups
import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import java.util.UUID

class AccessGroupEntity(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<AccessGroupEntity>(AccessGroups)

    val diaryId by AccessGroups.diary
    val name by AccessGroups.name
    val type by AccessGroups.type
}